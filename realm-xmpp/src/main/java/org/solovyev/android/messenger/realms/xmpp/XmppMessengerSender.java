/*
 * Copyright 2013 serso aka se.solovyev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.solovyev.android.messenger.realms.xmpp;

import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.XMPPException;
import org.solovyev.android.messenger.accounts.Account;
import org.solovyev.android.messenger.accounts.AccountConnectionException;
import org.solovyev.android.messenger.chats.Chat;
import org.solovyev.android.messenger.entities.Entity;
import org.solovyev.android.messenger.messages.Message;

import javax.annotation.Nonnull;

final class XmppMessengerSender implements XmppConnectedCallable<String> {

	@Nonnull
	private final Chat chat;

	@Nonnull
	private final Message message;

	@Nonnull
	private final Account account;

	XmppMessengerSender(@Nonnull Chat chat, @Nonnull Message message, @Nonnull Account account) {
		this.chat = chat;
		this.message = message;
		this.account = account;
	}

	@Override
	public String call(@Nonnull Connection connection) throws AccountConnectionException, XMPPException {
		final ChatManager chatManager = connection.getChatManager();

		final Entity chatId = chat.getEntity();
		final XmppMessageListener messageListener = new XmppMessageListener(account, chatId);
		org.jivesoftware.smack.Chat smackChat = chatManager.getThreadChat(chatId.getAccountEntityId());
		if (smackChat == null) {
			// smack forget about chat ids after restart => need to create chat here
			smackChat = chatManager.createChat(chat.getSecondUser().getAccountEntityId(), chatId.getAccountEntityId(), messageListener);
		} else if (!smackChat.getListeners().contains(messageListener)) {
			smackChat.addMessageListener(messageListener);
		}

		smackChat.sendMessage(message.getBody());

		return null;
	}
}
