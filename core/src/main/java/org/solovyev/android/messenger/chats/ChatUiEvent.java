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

package org.solovyev.android.messenger.chats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.solovyev.android.messenger.messages.Message;
import org.solovyev.common.listeners.AbstractTypedJEvent;

/**
 * User: serso
 * Date: 8/17/12
 * Time: 1:02 AM
 */
public final class ChatUiEvent extends AbstractTypedJEvent<Chat, ChatUiEventType> {

	public ChatUiEvent(@Nonnull Chat chat, @Nonnull ChatUiEventType type, @Nullable Object data) {
		super(chat, type, data);
	}

	@Nonnull
	public Chat getChat() {
		return getEventObject();
	}

	@Nonnull
	public Message getDataAsMessage() {
		return (Message) getData();
	}
}
