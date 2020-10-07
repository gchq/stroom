/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.security.identity.account;

import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
import java.util.Optional;

// TODO : @66 Add audit logging
class AccountResourceImpl implements AccountResource {
    private final Provider<AccountService> serviceProvider;
    private final AccountEventLog eventLog;

    @Inject
    public AccountResourceImpl(final Provider<AccountService> serviceProvider,
                               final AccountEventLog eventLog) {
        this.serviceProvider = serviceProvider;
        this.eventLog = eventLog;
    }

    @Override
    public ResultPage<Account> list(final HttpServletRequest httpServletRequest) {
        try {
            final ResultPage<Account> result = serviceProvider.get().list();
            eventLog.list(result, null);
            return result;
        } catch (final RuntimeException e) {
            eventLog.list(null, e);
            throw e;
        }
    }

    @Override
    public ResultPage<Account> search(final SearchAccountRequest request) {
        try {
            final ResultPage<Account> result = serviceProvider.get().search(request);
            eventLog.search(request, result, null);
            return result;
        } catch (final RuntimeException e) {
            eventLog.search(request, null, e);
            throw e;
        }
    }

    @Override
    public Integer create(final HttpServletRequest httpServletRequest,
                          final CreateAccountRequest request) {
        try {
            final Account account = serviceProvider.get().create(request);
            eventLog.create(request, account, null);
            return account.getId();
        } catch (final RuntimeException e) {
            eventLog.create(request, null, e);
            throw e;
        }
    }

    @Override
    public Account read(final HttpServletRequest httpServletRequest,
                        final int userId) {
        try {
            final Optional<Account> optionalAccount = serviceProvider.get().read(userId);
            final Account account = optionalAccount.orElseThrow(NotFoundException::new);
            eventLog.read(userId, account, null);
            return account;
        } catch (final RuntimeException e) {
            eventLog.read(userId, null, e);
            throw e;
        }
    }

    @Override
    public Boolean update(final HttpServletRequest httpServletRequest,
                          final UpdateAccountRequest request,
                          final int accountId) {
        try {
            serviceProvider.get().update(request, accountId);
            eventLog.update(request, accountId, null);
            return true;
        } catch (final RuntimeException e) {
            eventLog.update(null, accountId, e);
            throw e;
        }
    }

    @Override
    public Boolean delete(final HttpServletRequest httpServletRequest,
                          final int userId) {
        try {
            serviceProvider.get().delete(userId);
            eventLog.delete(userId, null);
            return true;
        } catch (final RuntimeException e) {
            eventLog.delete(userId, e);
            throw e;
        }
    }
}

