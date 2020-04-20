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

    @Inject
    public AccountResourceImpl(final AccountService service) {
        this.service = service;
    }

    @Override
    public ResultPage<Account> list(final HttpServletRequest httpServletRequest) {
        return service.getAll();
    }

    @Override
    public Integer create(final HttpServletRequest httpServletRequest,
                          final CreateAccountRequest request) {
        return service.create(request);
    }

    @Override
    public ResultPage<Account> search(final HttpServletRequest httpServletRequest,
                                      final String email) {
        return service.search(email);
    }

    @Override
    public Account read(final HttpServletRequest httpServletRequest,
                        final int userId) {
        final Optional<Account> optionalUser = service.get(userId);
        if (optionalUser.isPresent()) {
            return optionalUser.get();
        }
        throw new NotFoundException();
    }

    @Override
    public Boolean update(final HttpServletRequest httpServletRequest,
                          final Account account,
                          final int userId) {
        service.update(account, userId);
        return true;
    }

    @Override
    public Boolean delete(final HttpServletRequest httpServletRequest,
                          final int userId) {
        service.deleteUser(userId);
        return true;
    }
}

