package org.solovyev.android.messenger.chats;

import android.app.Application;
import android.util.Log;
import android.widget.ImageView;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.solovyev.android.http.ImageLoader;
import org.solovyev.android.messenger.MergeDaoResult;
import org.solovyev.android.messenger.MessengerApplication;
import org.solovyev.android.messenger.core.R;
import org.solovyev.android.messenger.entities.Entity;
import org.solovyev.android.messenger.entities.EntityImpl;
import org.solovyev.android.messenger.messages.ChatMessageDao;
import org.solovyev.android.messenger.messages.ChatMessageService;
import org.solovyev.android.messenger.messages.UnreadMessagesCounter;
import org.solovyev.android.messenger.realms.EntityMapEntryMatcher;
import org.solovyev.android.messenger.realms.Realm;
import org.solovyev.android.messenger.realms.RealmException;
import org.solovyev.android.messenger.realms.RealmRuntimeException;
import org.solovyev.android.messenger.realms.RealmService;
import org.solovyev.android.messenger.realms.UnsupportedRealmException;
import org.solovyev.android.messenger.users.PersistenceLock;
import org.solovyev.android.messenger.users.User;
import org.solovyev.android.messenger.users.UserEvent;
import org.solovyev.android.messenger.users.UserEventType;
import org.solovyev.android.messenger.users.UserService;
import org.solovyev.common.collections.Collections;
import org.solovyev.common.listeners.AbstractJEventListener;
import org.solovyev.common.listeners.JEventListener;
import org.solovyev.common.listeners.JEventListeners;
import org.solovyev.common.listeners.Listeners;
import org.solovyev.common.text.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * User: serso
 * Date: 6/6/12
 * Time: 2:43 AM
 */
@Singleton
public class DefaultChatService implements ChatService {

    /*
    **********************************************************************
    *
    *                           CONSTANTS
    *
    **********************************************************************
    */

    @Nonnull
    private static final Character PRIVATE_CHAT_DELIMITER = ':';

    /*
    **********************************************************************
    *
    *                           AUTO INJECTED FIELDS
    *
    **********************************************************************
    */

    @Inject
    @Nonnull
    private RealmService realmService;

    @GuardedBy("lock")
    @Inject
    @Nonnull
    private ChatDao chatDao;

    @Inject
    @Nonnull
    private ChatMessageService chatMessageService;

    @Inject
    @Nonnull
    private UserService userService;

    @Inject
    @Nonnull
    private ImageLoader imageLoader;

    @Inject
    @Nonnull
    private Application context;

    @Inject
    @Nonnull
    private ChatMessageDao chatMessageDao;

    @Inject
    @Nonnull
    private UnreadMessagesCounter unreadMessagesCounter;


    /*
    **********************************************************************
    *
    *                           OWN FIELDS
    *
    **********************************************************************
    */
    @Nonnull
    private static final String EVENT_TAG = "ChatEvent";

    @Nonnull
    private final JEventListeners<JEventListener<? extends ChatEvent>, ChatEvent> listeners;

    // key: chat id, value: list of participants
    @Nonnull
    private final Map<Entity, List<User>> chatParticipantsCache = new HashMap<Entity, List<User>>();

    // key: chat id, value: last message
    @Nonnull
    private final Map<Entity, ChatMessage> lastMessagesCache = new HashMap<Entity, ChatMessage>();

    // key: chat id, value: chat
    @Nonnull
    private final Map<Entity, Chat> chatsById = new HashMap<Entity, Chat>();

    @Nonnull
    private final Object lock;

    @Inject
    public DefaultChatService(@Nonnull PersistenceLock lock, @Nonnull ExecutorService eventExecutor) {
        this.listeners = Listeners.newEventListenersBuilderFor(ChatEvent.class).withHardReferences().withExecutor(eventExecutor).create();
        this.listeners.addListener(new ChatEventListener());
        this.lock = lock;
    }

    @Override
    public void init() {
        userService.addListener(new UserEventListener());
    }

    @Nonnull
    @Override
    public Chat updateChat(@Nonnull Chat chat) {
        final boolean changed;
        synchronized (lock) {
            changed = chatDao.updateChat(chat);
        }

        if (changed) {
            fireEvent(ChatEventType.changed.newEvent(chat, null));
        }

        return chat;
    }

    @Nonnull
    private Chat newPrivateChat(@Nonnull Entity user1, @Nonnull Entity user2) throws RealmException {
        final Realm realm = getRealmByEntity(user1);

        Chat result;

        final Entity realmChat = getPrivateChatId(user1, user2);
        synchronized (lock) {
            result = getChatById(realmChat);
            if ( result == null ) {
                // no private chat exists => create one
                final RealmChatService realmChatService = realm.getRealmChatService();

                Chat chat = realmChatService.newPrivateChat(realmChat, user1.getRealmEntityId(), user2.getRealmEntityId());

                chat = preparePrivateChat(chat, user1, user2);

                final List<User> participants = new ArrayList<User>(2);
                participants.add(getUserService().getUserById(user1));
                participants.add(getUserService().getUserById(user2));
                final ApiChat apiChat = Chats.newEmptyApiChat(chat, participants);

                getUserService().mergeUserChats(user1, Arrays.asList(apiChat));

                result = apiChat.getChat();
            }
        }

        return result;
    }

    /**
     * Method prepares private chat for inserting into database.
     *
     * @param chat chat to be prepared
     * @param user1 first participant
     * @param user2 second participant
     *
     * @return prepared chat
     */
    @Nonnull
    private Chat preparePrivateChat(@Nonnull Chat chat, @Nonnull Entity user1, @Nonnull Entity user2) throws UnsupportedRealmException {
        final Realm realm = getRealmByEntity(user1);
        final Entity chatEntity = getPrivateChatId(user1, user2);

        if (!chatEntity.getRealmEntityId().equals(chat.getEntity().getRealmEntityId())) {
            /**
             * chat id that was created by realm (may differ from one created in {@link org.solovyev.android.messenger.chats.ChatService#getPrivateChatId(org.solovyev.android.messenger.entities.Entity, org.solovyev.android.messenger.entities.Entity)) method)
             */
            final String realmChatId = chat.getEntity().getRealmEntityId();

            // copy with new id
            chat = chat.copyWithNew(realm.newRealmEntity(realmChatId, chatEntity.getEntityId()));
        }

        return chat;
    }

    @Nonnull
    private ApiChat prepareChat(@Nonnull ApiChat apiChat) throws UnsupportedRealmException {
        if (apiChat.getChat().isPrivate()) {
            final Realm realm = realmService.getRealmById(apiChat.getChat().getEntity().getRealmId());
            final User user = realm.getUser();
            final List<User> participants = apiChat.getParticipantsExcept(user);

            if (participants.size() == 1) {
                final Entity participant1 = user.getEntity();
                final Entity participant2 = participants.get(0).getEntity();

                final Entity realmChat = getPrivateChatId(participant1, participant2);

                if (!realmChat.getRealmEntityId().equals(apiChat.getChat().getEntity().getRealmEntityId())) {
                    /**
                     * chat id that was created by realm (may differ from one created in {@link org.solovyev.android.messenger.chats.ChatService#getPrivateChatId(org.solovyev.android.messenger.entities.Entity, org.solovyev.android.messenger.entities.Entity)) method)
                     */
                    final String realmChatId = apiChat.getChat().getEntity().getRealmEntityId();

                    // copy with new id
                    apiChat = apiChat.copyWithNew(realm.newRealmEntity(realmChatId, realmChat.getEntityId()));
                }
            }
        }

        return apiChat;
    }

    @Nonnull
    @Override
    public List<Chat> loadUserChats(@Nonnull Entity user) {
        synchronized (lock) {
            return chatDao.loadUserChats(user.getEntityId());
        }
    }

    @Nonnull
    @Override
    public ApiChat saveChat(@Nonnull Entity user, @Nonnull ApiChat chat) throws RealmException {
        final MergeDaoResult<ApiChat, String> result = mergeUserChats(user, Arrays.asList(chat));
        if ( result.getAddedObjects().size() > 0 ) {
            return result.getAddedObjects().get(0);
        } else if (result.getUpdatedObjects().size() > 0) {
            return result.getUpdatedObjects().get(0);
        } else {
            return chat;
        }
    }

    @Nonnull
    @Override
    public Map<Entity, Integer> getUnreadChats() {
        synchronized (lock) {
            return chatDao.getUnreadChats();
        }
    }

    @Override
    public void onUnreadMessagesCountChanged(@Nonnull Entity chatEntity, @Nonnull Integer unreadMessagesCount) {
        final Chat chat = getChatById(chatEntity);
        if (chat != null) {
            fireEvent(ChatEventType.unread_message_count_changed.newEvent(chat, unreadMessagesCount));

            if (chat.isPrivate()) {
                final Entity secondUser = getSecondUser(chat);
                if (secondUser != null) {
                    userService.onUnreadMessagesCountChanged(secondUser, unreadMessagesCount);
                }
            }
        }
    }

    @Override
    public int getUnreadMessagesCount(@Nonnull Entity chat) {
        return unreadMessagesCounter.getUnreadMessagesCountForChat(chat);
    }

    @Override
    public void removeChatsInRealm(@Nonnull String realmId) {
        synchronized (lock) {
            this.chatDao.deleteAllChatsInRealm(realmId);
        }

        synchronized (chatParticipantsCache) {
            Iterators.removeIf(chatParticipantsCache.entrySet().iterator(), EntityMapEntryMatcher.forRealm(realmId));
        }

        synchronized (lastMessagesCache) {
            Iterators.removeIf(lastMessagesCache.entrySet().iterator(), EntityMapEntryMatcher.forRealm(realmId));
        }

        synchronized (chatsById) {
            Iterators.removeIf(chatsById.entrySet().iterator(), EntityMapEntryMatcher.forRealm(realmId));
        }
    }

    @Nonnull
    @Override
    public MergeDaoResult<ApiChat, String> mergeUserChats(@Nonnull final Entity user, @Nonnull List<? extends ApiChat> chats) throws RealmException {
        synchronized (lock) {
            final List<ApiChat> preparedChats;
            try {
                preparedChats = Lists.transform(chats, new Function<ApiChat, ApiChat>() {
                    @Override
                    public ApiChat apply(@Nullable ApiChat chat) {
                        assert chat != null;
                        try {
                            return prepareChat(chat);
                        } catch (UnsupportedRealmException e) {
                            throw new RealmRuntimeException(e);
                        }
                    }
                });
            } catch (RealmRuntimeException e) {
                throw new RealmException(e);
            }
            return chatDao.mergeUserChats(user.getEntityId(), preparedChats);
        }
    }

    @Nullable
    @Override
    public Chat getChatById(@Nonnull Entity chat) {
        Chat result;

        synchronized (chatsById) {
            result = chatsById.get(chat);
        }

        if (result == null) {
            synchronized (lock) {
                result = chatDao.loadChatById(chat.getEntityId());
            }

            if ( result != null ) {
                synchronized (chatsById) {
                    chatsById.put(result.getEntity(), result);
                }
            }
        }

        return result;
    }


    @Nonnull
    private Realm getRealmByEntity(@Nonnull Entity entity) throws UnsupportedRealmException {
        return realmService.getRealmById(entity.getRealmId());
    }

    @Nonnull
    @Override
    public List<ChatMessage> syncChatMessages(@Nonnull Entity user) throws RealmException {
        final List<ChatMessage> messages = getRealmByEntity(user).getRealmChatService().getChatMessages(user.getRealmEntityId());

        final Multimap<Chat, ChatMessage> messagesByChats = ArrayListMultimap.create();

        for (ChatMessage message : messages) {
            if (message.isPrivate()) {
                final Entity participant = message.getSecondUser(user);
                final Chat chat = getPrivateChat(user, participant);
                messagesByChats.put(chat, message);
            } else {
                // todo serso: we need link to chat here
            }
        }

        for (Chat chat : messagesByChats.keys()) {
            saveChatMessages(chat.getEntity(), messagesByChats.get(chat), true);
        }

        return java.util.Collections.unmodifiableList(messages);
    }

    @Nonnull
    @Override
    public List<ChatMessage> syncNewerChatMessagesForChat(@Nonnull Entity chat) throws RealmException {
        final Realm realm = getRealmByEntity(chat);
        final RealmChatService realmChatService = realm.getRealmChatService();

        final List<ChatMessage> messages = realmChatService.getNewerChatMessagesForChat(chat.getRealmEntityId(), realm.getUser().getEntity().getRealmEntityId());

        saveChatMessages(chat, messages, true);

        return java.util.Collections.unmodifiableList(messages);

    }

    @Override
    public void saveChatMessages(@Nonnull Entity realmChat, @Nonnull Collection<? extends ChatMessage> messages, boolean updateChatSyncDate) {
        Chat chat = this.getChatById(realmChat);

        if (chat != null) {
            final MergeDaoResult<ChatMessage, String> result;
            synchronized (lock) {
                result = getChatMessageDao().mergeChatMessages(realmChat.getEntityId(), messages, false);

                // update sync data
                if (updateChatSyncDate) {
                    chat = chat.updateMessagesSyncDate();
                    updateChat(chat);
                }
            }

            final List<ChatEvent> chatEvents = new ArrayList<ChatEvent>(messages.size());

            chatEvents.add(ChatEventType.message_added_batch.newEvent(chat, result.getAddedObjects()));

            // cannot to remove as not all message can be loaded
/*            for (Integer removedMessageId : result.getRemovedObjectIds()) {
                chatEvents.add(new ChatEvent(chat, ChatEventType.message_removed, removedMessageId));
            }*/

            for (ChatMessage updatedMessage : result.getUpdatedObjects()) {
                chatEvents.add(ChatEventType.message_changed.newEvent(chat, updatedMessage));
            }

            fireEvents(chatEvents);
        } else {
            Log.e(this.getClass().getSimpleName(), "Not chat found - chat id: " + realmChat.getEntityId());
        }
    }

    @Override
    public void onChatMessageRead(@Nonnull Chat chat, @Nonnull ChatMessage message) {
        if ( !message.isRead() ) {
            message = message.cloneRead();
        }

        final boolean changedReadStatus;
        synchronized (lock) {
            changedReadStatus = chatMessageDao.changeReadStatus(message.getId(), true);
        }

        if ( changedReadStatus ) {
            fireEvent(ChatEventType.message_changed.newEvent(chat, message));
            fireEvent(ChatEventType.message_read.newEvent(chat, message));
        }
    }

    @Nonnull
    private ChatMessageDao getChatMessageDao() {
        return chatMessageDao;
    }

    @Nonnull
    @Override
    public List<ChatMessage> syncOlderChatMessagesForChat(@Nonnull Entity chat, @Nonnull Entity user) throws RealmException {
        final Integer offset = getChatMessageService().getChatMessages(chat).size();

        final List<ChatMessage> messages = getRealmByEntity(user).getRealmChatService().getOlderChatMessagesForChat(chat.getRealmEntityId(), user.getRealmEntityId(), offset);
        saveChatMessages(chat, messages, false);

        return java.util.Collections.unmodifiableList(messages);
    }

    @Override
    public void syncChat(@Nonnull Entity chat, @Nonnull Entity user) throws RealmException {
        // todo serso: check if OK
        syncNewerChatMessagesForChat(chat);
    }

    @Nullable
    @Override
    public Entity getSecondUser(@Nonnull Chat chat) {
        boolean first = true;

        if (chat.isPrivate()) {
            for (String userId : Splitter.on(PRIVATE_CHAT_DELIMITER).split(chat.getEntity().getAppRealmEntityId())) {
                if ( first ) {
                    first = false;
                } else {
                    return EntityImpl.newInstance(chat.getEntity().getRealmId(), userId);
                }
            }
        }

        return null;
    }

    @Override
    public void setChatIcon(@Nonnull Chat chat, @Nonnull ImageView imageView) {
        try {
            final Realm realm = getRealmByEntity(chat.getEntity());

            final List<User> otherParticipants = this.getParticipantsExcept(chat.getEntity(), realm.getUser().getEntity());

            if (!otherParticipants.isEmpty()) {
                if (otherParticipants.size() == 1) {
                    final User participant = otherParticipants.get(0);
                    userService.setUserIcon(participant, imageView);
                } else {
                    userService.setUsersIcon(realm, otherParticipants, imageView);
                }
            } else {
                // just in case...
                imageView.setImageDrawable(context.getResources().getDrawable(R.drawable.mpp_app_icon));
            }
        } catch (UnsupportedRealmException e) {
            imageView.setImageDrawable(context.getResources().getDrawable(R.drawable.mpp_app_icon));
            MessengerApplication.getServiceLocator().getExceptionHandler().handleException(e);
        }
    }

    @Nonnull
    @Override
    public Entity getPrivateChatId(@Nonnull Entity user1, @Nonnull Entity user2) {
        final String realmEntityId1 = user1.getRealmEntityId();
        final String realmEntityId2 = user2.getRealmEntityId();
        if ( realmEntityId1.equals(realmEntityId2) ) {
            Log.e(TAG, "Same user in private chat " + Strings.fromStackTrace(Thread.currentThread().getStackTrace()));
        }
        return EntityImpl.newInstance(user1.getRealmId(), realmEntityId1 + PRIVATE_CHAT_DELIMITER + realmEntityId2);
    }

    @Nonnull
    private ChatMessageService getChatMessageService() {
        return this.chatMessageService;
    }

    @Nonnull
    @Override
    public Chat getPrivateChat(@Nonnull Entity user1, @Nonnull final Entity user2) throws RealmException {
        final Entity chat = this.getPrivateChatId(user1, user2);

        Chat result = this.getChatById(chat);
        if (result == null) {
            result = this.newPrivateChat(user1, user2);
        }

        return result;
    }

    @Nonnull
    @Override
    public List<User> getParticipants(@Nonnull Entity chat) {
        List<User> result;

        synchronized (chatParticipantsCache) {
            result = chatParticipantsCache.get(chat);
            if (result == null) {
                synchronized (lock) {
                    result = chatDao.loadChatParticipants(chat.getEntityId());
                }
                if (!Collections.isEmpty(result)) {
                    chatParticipantsCache.put(chat, result);
                }
            }
        }

        // result list might be in cache and might be updated due to some events => must COPY
        return new ArrayList<User>(result);
    }

    @Nonnull
    @Override
    public List<User> getParticipantsExcept(@Nonnull Entity chat, @Nonnull final Entity user) {
        final List<User> participants = getParticipants(chat);
        return Lists.newArrayList(Iterables.filter(participants, new Predicate<User>() {
            @Override
            public boolean apply(@javax.annotation.Nullable User input) {
                return input != null && !input.getEntity().equals(user);
            }
        }));
    }

    @Nullable
    @Override
    public ChatMessage getLastMessage(@Nonnull Entity chat) {
        ChatMessage result;

        synchronized (lastMessagesCache) {
            result = lastMessagesCache.get(chat);
            if (result == null) {
                result = getChatMessageDao().loadLastChatMessage(chat.getEntityId());
                if (result != null) {
                    lastMessagesCache.put(chat, result);
                }
            }
        }

        return result;
    }

    @Nonnull
    private UserService getUserService() {
        return this.userService;
    }

    @Override
    public boolean addListener(@Nonnull JEventListener<ChatEvent> listener) {
        return this.listeners.addListener(listener);
    }

    @Override
    public boolean removeListener(@Nonnull JEventListener<ChatEvent> listener) {
        return this.listeners.removeListener(listener);
    }

    @Override
    public void fireEvent(@Nonnull ChatEvent event) {
        this.listeners.fireEvent(event);
    }

    @Override
    public void fireEvents(@Nonnull Collection<ChatEvent> events) {
        this.listeners.fireEvents(events);
    }

    @Override
    public void removeListeners() {
        this.listeners.removeListeners();
    }

    private final class UserEventListener extends AbstractJEventListener<UserEvent> {

        private UserEventListener() {
            super(UserEvent.class);
        }

        @Override
        public void onEvent(@Nonnull UserEvent event) {
            synchronized (chatParticipantsCache) {
                final User eventUser = event.getUser();

                if (event.getType() == UserEventType.changed) {
                    for (List<User> participants : chatParticipantsCache.values()) {
                        for (int i = 0; i < participants.size(); i++) {
                            final User participant = participants.get(i);
                            if (participant.equals(eventUser)) {
                                participants.set(i, eventUser);
                            }
                        }
                    }
                }

            }
        }
    }

    private final class ChatEventListener extends AbstractJEventListener<ChatEvent> {

        private ChatEventListener() {
            super(ChatEvent.class);
        }

        @Override
        public void onEvent(@Nonnull ChatEvent event) {
            final Chat eventChat = event.getChat();
            final ChatEventType type = event.getType();
            final Object data = event.getData();

            synchronized (chatParticipantsCache) {

                if (type == ChatEventType.participant_added) {
                    // participant added => need to add to list of cached participants
                    if (data instanceof User) {
                        final User participant = ((User) data);
                        final List<User> participants = chatParticipantsCache.get(eventChat.getEntity());
                        if (participants != null) {
                            // check if not contains as can be added in parallel
                            if (!Iterables.contains(participants, participant)) {
                                participants.add(participant);
                            }
                        }
                    }
                }

                if (type == ChatEventType.participant_removed) {
                    // participant removed => try to remove from cached participants
                    if (data instanceof User) {
                        final User participant = ((User) data);
                        final List<User> participants = chatParticipantsCache.get(eventChat.getEntity());
                        if (participants != null) {
                            participants.remove(participant);
                        }
                    }
                }
            }

            synchronized (chatsById) {
                if (event.isOfType(ChatEventType.changed, ChatEventType.changed, ChatEventType.last_message_changed)) {
                    chatsById.put(eventChat.getEntity(), eventChat);
                }
            }


            final Map<Chat, ChatMessage> changesLastMessages = new HashMap<Chat, ChatMessage>();
            synchronized (lastMessagesCache) {

                if (type == ChatEventType.message_added) {
                    final ChatMessage message = event.getDataAsChatMessage();
                    final ChatMessage messageFromCache = lastMessagesCache.get(eventChat.getEntity());
                    if (messageFromCache == null || message.getSendDate().isAfter(messageFromCache.getSendDate())) {
                        lastMessagesCache.put(eventChat.getEntity(), message);
                        changesLastMessages.put(eventChat, message);
                    }
                }

                if (type == ChatEventType.message_added_batch) {
                    final List<ChatMessage> messages = event.getDataAsChatMessages();

                    ChatMessage newestMessage = null;
                    for (ChatMessage message : messages) {
                        if (newestMessage == null) {
                            newestMessage = message;
                        } else if (message.getSendDate().isAfter(newestMessage.getSendDate())) {
                            newestMessage = message;
                        }
                    }

                    final ChatMessage messageFromCache = lastMessagesCache.get(eventChat.getEntity());
                    if (newestMessage != null && (messageFromCache == null || newestMessage.getSendDate().isAfter(messageFromCache.getSendDate()))) {
                        lastMessagesCache.put(eventChat.getEntity(), newestMessage);
                        changesLastMessages.put(eventChat, newestMessage);
                    }
                }


                if (type == ChatEventType.message_changed) {
                    if (data instanceof ChatMessage) {
                        final ChatMessage message = (ChatMessage) data;
                        final ChatMessage messageFromCache = lastMessagesCache.get(eventChat.getEntity());
                        if (messageFromCache == null || messageFromCache.equals(message)) {
                            lastMessagesCache.put(eventChat.getEntity(), message);
                            changesLastMessages.put(eventChat, message);
                        }
                    }
                }

            }

            for (Map.Entry<Chat, ChatMessage> changedLastMessageEntry : changesLastMessages.entrySet()) {
                fireEvent(ChatEventType.last_message_changed.newEvent(changedLastMessageEntry.getKey(), changedLastMessageEntry.getValue()));
            }
        }
    }
}
