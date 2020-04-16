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
import javax.ws.rs.core.Response;
import java.util.Optional;

// TODO : @66 Add audit logging
class AccountResourceImpl implements AccountResource {
    private AccountService service;

    @Inject
    public AccountResourceImpl(final AccountService service) {
        this.service = service;
    }

    @Override
    public ResultPage<Account> getAll(final HttpServletRequest httpServletRequest) {
        return service.getAll();
    }

    @Override
    public Response createUser(final HttpServletRequest httpServletRequest,
                               final Account account) {
        final int newUserId = service.create(account);
        return Response.status(Response.Status.OK).entity(newUserId).build();
    }

    @Override
    public ResultPage<Account> searchUsers(final HttpServletRequest httpServletRequest,
                                           final String email) {
        return service.search(email);
    }

    @Override
    public Response getUser(final HttpServletRequest httpServletRequest,
                            final int userId) {
        final Optional<Account> optionalUser = service.get(userId);
        if (optionalUser.isPresent()) {
            return Response.status(Response.Status.OK).entity(optionalUser.get()).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @Override
    public Response updateUser(final HttpServletRequest httpServletRequest,
                               final Account account,
                               final int userId) {
        service.update(account, userId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @Override
    public Response deleteUser(final HttpServletRequest httpServletRequest,
                               final int userId) {
        service.deleteUser(userId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }
}

