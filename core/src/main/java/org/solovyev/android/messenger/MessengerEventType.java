package org.solovyev.android.messenger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * User: serso
 * Date: 3/23/13
 * Time: 5:17 PM
 */

/**
 * Common messenger events
 */
public enum MessengerEventType {

    // data == number of unread messages
    unread_messages_count_changed {
        @Override
        protected void checkData(@Nullable Object data) {
            assert data instanceof Integer;
        }
    };

    @Nonnull
    public final MessengerEvent newEvent(@Nullable Object data) {
        checkData(data);
        return new MessengerEvent(this, data);
    }

    protected void checkData(@Nullable Object data) {
        assert data == null;
    }
}