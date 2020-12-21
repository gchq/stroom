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

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.util.shared.ResultPage;

import event.logging.AdvancedQuery;
import event.logging.And;
import event.logging.Outcome;
import event.logging.Query;
import event.logging.SearchEventAction;
import event.logging.Term;
import event.logging.TermCondition;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
import java.math.BigInteger;
import java.util.Optional;

// TODO : @66 Add audit logging
class AccountResourceImpl implements AccountResource {
    private final Provider<AccountService> serviceProvider;
    private final AccountEventLog eventLog;
    private final StroomEventLoggingService stroomEventLoggingService;

    @Inject
    public AccountResourceImpl(final Provider<AccountService> serviceProvider,
                               final AccountEventLog eventLog,
                               final StroomEventLoggingService stroomEventLoggingService) {
        this.serviceProvider = serviceProvider;
        this.eventLog = eventLog;
        this.stroomEventLoggingService = stroomEventLoggingService;
    }

    @Override
    public ResultPage<Account> list(final HttpServletRequest httpServletRequest) {

        return stroomEventLoggingService.loggedResult(
                "ListAccounts",
                "List all accounts",
                SearchEventAction.builder()
                        .withQuery(Query.builder()
                                .withAdvanced(AdvancedQuery.builder()
                                        .addAnd(new And())
                                        .build())
                                .build())
                        .withOutcome(Outcome.builder()
                                .withSuccess(true)
                                .build())
                        .build(),
                searchEventAction -> {
                    final ResultPage<Account> result = serviceProvider.get().list();
                    searchEventAction.setResultPage(stroomEventLoggingService.createResultPage(result));
                    searchEventAction.setTotalResults(BigInteger.valueOf(result.size()));
                    return result;
                }
        );
    }

    @Override
    public ResultPage<Account> search(final SearchAccountRequest request) {
        return stroomEventLoggingService.loggedResult(
                "SearchAccounts",
                "Search for accounts by email",
                SearchEventAction.builder()
                        .withQuery(Query.builder()
                                .withAdvanced(AdvancedQuery.builder()
                                        .addAnd(And.builder()
                                                .addTerm(Term.builder()
                                                        .withName("Email")
                                                        .withCondition(TermCondition.EQUALS)
                                                        .withValue(request.getQuickFilter())
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .withOutcome(Outcome.builder()
                                .withSuccess(true)
                                .build())
                        .build(),
                searchEventAction -> {
                    final ResultPage<Account> result = serviceProvider.get().search(request);
                    searchEventAction.setResultPage(stroomEventLoggingService.createResultPage(result));
                    searchEventAction.setTotalResults(BigInteger.valueOf(result.size()));
                    return result;
                });
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

