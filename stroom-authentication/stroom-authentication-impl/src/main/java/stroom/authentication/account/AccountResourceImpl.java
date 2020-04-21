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

package stroom.authentication.account;

import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
import java.util.Optional;

// TODO : @66 Add audit logging
class AccountResourceImpl implements AccountResource {
    private final AccountService service;
    private final AccountEventLog eventLog;

    @Inject
    public AccountResourceImpl(final AccountService service,
                               final AccountEventLog eventLog) {
        this.service = service;
        this.eventLog = eventLog;
    }

    @Override
    public ResultPage<Account> list(final HttpServletRequest httpServletRequest) {
        try {
            final ResultPage<Account> result = service.list();
            eventLog.list(result, null);
            return result;
        } catch (final RuntimeException e) {
            eventLog.list(null, e);
            throw e;
        }
    }

    @Override
    public ResultPage<Account> search(final HttpServletRequest httpServletRequest,
                                      final String email) {
        try {
            final ResultPage<Account> result = service.search(email);
            eventLog.search(email, result, null);
            return result;
        } catch (final RuntimeException e) {
            eventLog.search(email, null, e);
            throw e;
        }
    }

    @Override
    public Integer create(final HttpServletRequest httpServletRequest,
                          final CreateAccountRequest request) {
        try {
            final Account account = service.create(request);
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
            final Optional<Account> optionalAccount = service.read(userId);
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
                          final Account account,
                          final int userId) {
        try {
            service.update(account, userId);
            eventLog.update(account, userId, null);
            return true;
        } catch (final RuntimeException e) {
            eventLog.update(null, userId, e);
            throw e;
        }
    }

    @Override
    public Boolean delete(final HttpServletRequest httpServletRequest,
                          final int userId) {
        try {
            service.delete(userId);
            eventLog.delete(userId, null);
            return true;
        } catch (final RuntimeException e) {
            eventLog.delete(userId, e);
            throw e;
        }
    }
}

