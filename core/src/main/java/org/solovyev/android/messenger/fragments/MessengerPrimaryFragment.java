package org.solovyev.android.messenger.fragments;

import android.support.v4.app.Fragment;
import org.solovyev.android.messenger.chats.MessengerChatsFragment;
import org.solovyev.android.messenger.core.R;
import org.solovyev.android.messenger.realms.MessengerRealmsFragment;
import org.solovyev.android.messenger.users.MessengerContactsFragment;

import javax.annotation.Nonnull;

/**
 * User: serso
 * Date: 3/7/13
 * Time: 5:04 PM
 */
public enum MessengerPrimaryFragment {

    contacts(MessengerContactsFragment.class, MessengerContactsFragment.FRAGMENT_TAG, R.string.mpp_tab_contacts),
    messages(MessengerChatsFragment.class, MessengerChatsFragment.FRAGMENT_TAG, R.string.mpp_tab_messages),
    realms(MessengerRealmsFragment.class, MessengerRealmsFragment.FRAGMENT_TAG, R.string.mpp_tab_realms);

    // todo serso: make settings a fragment
    // special logic for settings tab as it is not just a fragment
    /*settings;*/

    @Nonnull
    private final Class<? extends Fragment> fragmentClass;

    @Nonnull
    private final String fragmentTag;

    private final int titleResId;

    MessengerPrimaryFragment(@Nonnull Class<? extends Fragment> fragmentClass, @Nonnull String fragmentTag, int titleResId) {
        this.fragmentClass = fragmentClass;
        this.fragmentTag = fragmentTag;
        this.titleResId = titleResId;
    }

    @Nonnull
    public String getFragmentTag() {
        return this.fragmentTag;
    }

    @Nonnull
    public Class<? extends Fragment> getFragmentClass() {
        return fragmentClass;
    }

    public int getTitleResId() {
        return titleResId;
    }
}
