package org.solovyev.android.messenger.users;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import org.solovyev.android.list.ListAdapter;
import org.solovyev.android.list.ListItem;
import org.solovyev.android.messenger.MessengerApplication;
import org.solovyev.android.messenger.core.R;
import org.solovyev.android.messenger.view.ViewAwareTag;
import org.solovyev.android.view.ViewFromLayoutBuilder;
import roboguice.RoboGuice;
import roboguice.event.EventManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * User: serso
 * Date: 6/1/12
 * Time: 7:04 PM
 */
public class ContactListItem implements ListItem, UserEventListener, Comparable<ContactListItem>, Checkable {

    @Nonnull
    private static final String TAG_PREFIX = "contact_list_item_view_";

    @Nonnull
    private User contact;

    private boolean checked;

    public ContactListItem(@Nonnull User contact) {
        this.contact = contact;
    }

    @Override
    public OnClickAction getOnClickAction() {
        return new OnClickAction() {
            @Override
            public void onClick(@Nonnull final Context context, @Nonnull final ListAdapter<? extends ListItem> adapter, @Nonnull ListView listView) {
                final EventManager eventManager = RoboGuice.getInjector(context).getInstance(EventManager.class);
                eventManager.fire(ContactGuiEventType.newContactClicked(contact));

            }
        };
    }

    @Override
    public OnClickAction getOnLongClickAction() {
        return null;
    }


    @Nonnull
    @Override
    public View updateView(@Nonnull Context context, @Nonnull View view) {
        final Object tag = view.getTag();
        if ( tag instanceof ViewAwareTag && ((ViewAwareTag) tag).getTag().startsWith(TAG_PREFIX)) {
            fillView((ViewGroup) view);
            return view;
        } else {
            return build(context);
        }
    }

    @Nonnull
    @Override
    public View build(@Nonnull Context context) {
        final ViewGroup view = (ViewGroup) ViewFromLayoutBuilder.newInstance(R.layout.mpp_list_item_contact).build(context);
        fillView(view);
        return view;
    }

    @Nonnull
    private ViewAwareTag createTag(@Nonnull ViewGroup view) {
        return new ViewAwareTag(TAG_PREFIX + contact.getRealmUser().getEntityId(), view);
    }

    private void fillView(@Nonnull final ViewGroup view) {
        final ViewAwareTag tag = createTag(view);

        // todo serso: view.setSelected() doesn't work
        toggleSelected(view, checked);

        ViewAwareTag viewTag = (ViewAwareTag) view.getTag();
        if (!tag.equals(viewTag)) {
            if (viewTag != null) {
                viewTag.update(tag);
            } else {
                viewTag = tag;
                view.setTag(viewTag);
            }

            final ImageView contactIcon = viewTag.getViewById(R.id.mpp_contact_icon);
            MessengerApplication.getServiceLocator().getUserService().setUserIcon(contact, contactIcon);

            final TextView contactName = viewTag.getViewById(R.id.mpp_contact_name);
            contactName.setText(contact.getDisplayName());

            final TextView contactOnline = viewTag.getViewById(R.id.mpp_contact_online);
            if (contact.isOnline()) {
                contactOnline.setText("·");
            } else {
                contactOnline.setText("");
            }
        }
    }

    public static void toggleSelected(@Nonnull ViewGroup view, boolean checked) {
        if (checked) {
            view.setBackgroundResource(R.drawable.item_states_selected);
        } else {
            view.setBackgroundResource(R.drawable.item_states);
        }
    }

    @Override
    public void onUserEvent(@Nonnull User eventUser, @Nonnull UserEventType userEventType, @Nullable Object data) {
        if (userEventType == UserEventType.changed) {
            if (eventUser.equals(contact)) {
                contact = eventUser;
            }
        }

        if (userEventType == UserEventType.contact_offline || userEventType == UserEventType.contact_online) {
            if (contact.equals(data)) {
                contact = (User) data;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ContactListItem)) {
            return false;
        }

        final ContactListItem that = (ContactListItem) o;

        if (!contact.equals(that.contact)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return contact.hashCode();
    }

    @Override
    public String toString() {
        // NOTE: this code is used inside the ArrayAdapter for filtering
        return contact.getDisplayName();
    }

    @Override
    public int compareTo(@Nonnull ContactListItem another) {
        return this.toString().compareTo(another.toString());
    }

    @Nonnull
    public User getContact() {
        return contact;
    }

    @Override
    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        this.checked = !checked;
    }
}
