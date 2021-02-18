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
import stroom.event.logging.api.StroomEventLoggingUtil;

import event.logging.AdvancedQuery;
import event.logging.And;
import event.logging.AuthenticateAction;
import event.logging.AuthenticateEventAction;
import event.logging.AuthenticateOutcome;
import event.logging.ComplexLoggedOutcome;
import event.logging.CreateEventAction;
import event.logging.Data;
import event.logging.DeleteEventAction;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.Query;
import event.logging.SearchEventAction;
import event.logging.Term;
import event.logging.TermCondition;
import event.logging.UpdateEventAction;
import event.logging.User;
import event.logging.ViewEventAction;
import event.logging.util.EventLoggingUtil;

import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;

// TODO : @66 Add audit logging
class AccountResourceImpl implements AccountResource {

    private final Provider<AccountService> serviceProvider;
    private final StroomEventLoggingService stroomEventLoggingService;

    @Inject
    public AccountResourceImpl(final Provider<AccountService> serviceProvider,
                               final StroomEventLoggingService stroomEventLoggingService) {
        this.serviceProvider = serviceProvider;
        this.stroomEventLoggingService = stroomEventLoggingService;
    }

    @Override
    public AccountResultPage list(final HttpServletRequest httpServletRequest) {
        return stroomEventLoggingService.loggedResult(
                "ListAccounts",
                "List all accounts",
                SearchEventAction.builder()
                        .withQuery(Query.builder()
                                .withAdvanced(AdvancedQuery.builder()
                                        .addAnd(new And())
                                        .build())
                                .build())
                        .build(),
                searchEventAction -> {
                    // Do the work
                    final AccountResultPage result = serviceProvider.get().list();

                    final SearchEventAction newSearchEventAction = searchEventAction.newCopyBuilder()
                            .withResultPage(StroomEventLoggingUtil.createResultPage(result))
                            .withTotalResults(BigInteger.valueOf(result.size()))
                            .build();

                    return ComplexLoggedOutcome.success(result, newSearchEventAction);
                },
                null
        );
    }

    @Override
    public AccountResultPage search(final SearchAccountRequest request) {
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
                        .build(),
                searchEventAction -> {
                    // Do the work
                    final AccountResultPage result = serviceProvider.get()
                            .search(request);

                    final SearchEventAction newSearchEventAction = searchEventAction.newCopyBuilder()
                            .withResultPage(StroomEventLoggingUtil.createResultPage(result))
                            .withTotalResults(BigInteger.valueOf(result.size()))
                            .build();

                    return ComplexLoggedOutcome.success(result, newSearchEventAction);
                },
                null);
    }

    @Override
    public Integer create(final HttpServletRequest httpServletRequest,
                          final CreateAccountRequest request) {

        return stroomEventLoggingService.loggedResult(
                "CreateAccount",
                "Create an account",
                CreateEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType("Account")
                                .withName(request.getUserId())
                                .build())
                        .build(),
                createEventAction -> {
                    // Do the work
                    final Account account = serviceProvider.get()
                            .create(request);

                    final OtherObject otherObject = (OtherObject) createEventAction.getObjects()
                            .get(0);
                    final CreateEventAction newCreateEventAction = createEventAction.newCopyBuilder()
                            .withObjects(otherObject.newCopyBuilder()
                                    .withData(Data.builder()
                                            .withName("Enabled")
                                            .withValue(String.valueOf(account.isEnabled()))
                                            .build())
                                    .build())
                            .build();

                    return ComplexLoggedOutcome.success(account.getId(), newCreateEventAction);
                },
                null);
    }

    @Override
    public Account read(final Integer userId) {
        if (userId == null) {
            return null;
        }
        return stroomEventLoggingService.loggedResult(
                "GetAccountById",
                "Get a user by ID",
                ViewEventAction.builder()
                        .addUser(User.builder()
                                .withId(String.valueOf(userId))
                                .build())
                        .build(),
                viewEventAction -> {
                    // Do the work
                    final Account account = serviceProvider.get()
                            .read(userId)
                            .orElseThrow(NotFoundException::new);

                    final ViewEventAction newViewEventAction = viewEventAction.newCopyBuilder()
                            .withObjects(viewEventAction.getObjects().get(0).newCopyBuilder()
                                    .withName(account.getFirstName() + " " + account.getLastName())
                                    .build())
                            .build();

                    return ComplexLoggedOutcome.success(account, newViewEventAction);
                },
                null);
    }

    @Override
    public Boolean update(final HttpServletRequest httpServletRequest,
                          final UpdateAccountRequest request,
                          final int accountId) {

        final User user = User.builder()
                .withId(String.valueOf(accountId))
                .withName(request.getAccount().getFirstName() + " "
                        + request.getAccount().getLastName())
                .withEmailAddress(request.getAccount().getEmail())
                .build();


        final Boolean result;
        try {
            result = stroomEventLoggingService.loggedResult(
                    "UpdateAccount",
                    "Update account for user " + accountId,
                    UpdateEventAction.builder()
                            .withAfter(MultiObject.builder()
                                    .addUser(user)
                                    .build())
                            .build(),
                    () -> {
                        serviceProvider.get()
                                .update(request, accountId);
                        return true;
                    });

            if (request.getPassword() != null) {
                // Password change so log that separately
                logChangePassword(accountId, user, null);
            }
        } catch (Exception e) {
            // Password change so log that separately
            logChangePassword(accountId, user, e);
            throw e;
        }

        return result;
    }

    private void logChangePassword(final int accountId,
                                   final User user,
                                   final Throwable e) {
        stroomEventLoggingService.log(
                "ChangePassword",
                "Change password for user " + accountId,
                AuthenticateEventAction.builder()
                        .withUser(user)
                        .withAction(AuthenticateAction.CHANGE_PASSWORD)
                        .withOutcome(EventLoggingUtil.createOutcome(AuthenticateOutcome.class, e))
                        .build());
    }

    @Override
    public Boolean delete(final HttpServletRequest httpServletRequest,
                          final int userId) {

        return stroomEventLoggingService.loggedResult(
                "DeleteAccount",
                "Deleting user account " + userId,
                DeleteEventAction.builder()
                        .addUser(User.builder()
                                .withId(String.valueOf(userId))
                                .build())
                        .build(),
                () -> {
                    serviceProvider.get()
                            .delete(userId);
                    return true;
                });
    }
}

