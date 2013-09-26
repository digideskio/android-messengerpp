package org.solovyev.android.messenger.realms;

import android.content.Context;

import javax.annotation.Nonnull;

import org.solovyev.android.messenger.accounts.connection.AbstractAccountConnection;

/**
 * User: serso
 * Date: 3/4/13
 * Time: 5:05 PM
 */
public class TestAccountConnection extends AbstractAccountConnection<TestAccount> {

	public TestAccountConnection(@Nonnull TestAccount account, @Nonnull Context context) {
		super(account, context, false);
	}

	@Override
	protected void start0() {

	}

	@Override
	protected void stop0() {
		//To change body of implemented methods use File | Settings | File Templates.
	}
}