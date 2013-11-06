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

package org.solovyev.android.messenger.entities;

import android.os.Parcelable;

import javax.annotation.Nonnull;

/**
 * User: serso
 * Date: 2/24/13
 * Time: 4:10 PM
 */
public interface Entity extends Parcelable {

	/**
	 * @return unique ID of entity (user/chat/message) in application
	 */
	@Nonnull
	String getEntityId();

	/**
	 * @return account to which entity is belonged to
	 */
	@Nonnull
	String getAccountId();

	/**
	 * @return realm id to which entity is belonged to
	 */
	@Nonnull
	String getRealmId();

	/**
	 * @return id in account
	 */
	@Nonnull
	String getAccountEntityId();

	boolean isAccountEntityIdSet();

	/**
	 * @return id in account generated by application
	 */
	@Nonnull
	String getAppAccountEntityId();

    /*
	**********************************************************************
    *
    *                           EQUALS/HASHCODE/CLONE
    *
    **********************************************************************
    */

	int hashCode();

	boolean equals(Object o);

	@Nonnull
	Entity clone();
}
