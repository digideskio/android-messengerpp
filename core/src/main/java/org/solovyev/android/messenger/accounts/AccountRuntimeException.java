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

package org.solovyev.android.messenger.accounts;

import javax.annotation.Nonnull;

public class AccountRuntimeException extends RuntimeException {

	@Nonnull
	private final String accountId;

	public AccountRuntimeException(@Nonnull AccountException e) {
		this(e.getAccountId(), e);
	}

	public AccountRuntimeException(@Nonnull String accountId, Throwable throwable) {
		super(throwable);
		this.accountId = accountId;
	}

	public AccountRuntimeException(@Nonnull String accountId) {
		this.accountId = accountId;
	}

	@Nonnull
	public String getAccountId() {
		return accountId;
	}
}
