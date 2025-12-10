/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.identity.account;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.SecurityContext;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountResource;
import stroom.security.identity.shared.AccountResultPage;
import stroom.security.identity.shared.CreateAccountRequest;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.security.identity.shared.UpdateAccountRequest;
import stroom.util.shared.ResultPage;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Strings;
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
import event.logging.UpdateEventAction;
import event.logging.User;
import event.logging.ViewEventAction;
import event.logging.util.EventLoggingUtil;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class AccountResourceImpl implements AccountResource {

    private final Provider<AccountService> serviceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<SecurityContext> securityContextProvider;

    @Inject
    public AccountResourceImpl(final Provider<AccountService> serviceProvider,
                               final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                               final Provider<SecurityContext> securityContextProvider) {
        this.serviceProvider = serviceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.securityContextProvider = securityContextProvider;
    }

    @Timed
    @Override
    public ResultPage<Account> list() {
        final StroomEventLoggingService eventLoggingService = stroomEventLoggingServiceProvider.get();
        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "list"))
                .withDescription("List all accounts")
                .withDefaultEventAction(SearchEventAction.builder()
                        .withQuery(Query.builder()
                                .withAdvanced(AdvancedQuery.builder()
                                        .addAnd(new And())
                                        .build())
                                .build())
                        .build())
                .withComplexLoggedResult(searchEventAction -> {
                    // Do the work
                    final AccountResultPage result = serviceProvider.get().list();

                    final SearchEventAction newSearchEventAction = searchEventAction.newCopyBuilder()
                            .withResultPage(StroomEventLoggingUtil.createResultPage(result))
                            .withTotalResults(BigInteger.valueOf(result.size()))
                            .build();

                    return ComplexLoggedOutcome.success(result, newSearchEventAction);
                })
                .getResultAndLog();
    }

    @AutoLogged(OperationType.VIEW)
    @Timed
    @Override
    public ResultPage<Account> find(final FindAccountRequest request) {

        final ResultPage<Account> result = serviceProvider.get()
                .search(request);

        return result;

//        if (NullSafe.isBlankString(request, FindAccountRequest::getQuickFilter)
//                && NullSafe.isEmptyCollection(request, FindAccountRequest::getSortList)) {
//            return list();
//        } else {
//
//            final StroomEventLoggingService eventLoggingService = stroomEventLoggingServiceProvider.get();
//            return eventLoggingService.loggedWorkBuilder()
//                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "search"))
//                    .withDescription("Search for accounts with quick filter")
//                    .withDefaultEventAction(SearchEventAction.builder()
//                            .withQuery(buildRawQuery(request.getQuickFilter()))
//                            .build())
//                    .withComplexLoggedResult(searchEventAction -> {
//                        // Do the work
//                        final AccountResultPage result = serviceProvider.get()
//                                .search(request);
//
//                        final SearchEventAction newSearchEventAction = searchEventAction.newCopyBuilder()
//                                .withQuery(buildRawQuery(result.getQualifiedFilterInput()))
//                                .withResultPage(StroomEventLoggingUtil.createResultPage(result))
//                                .withTotalResults(BigInteger.valueOf(result.size()))
//                                .build();
//
//                        return ComplexLoggedOutcome.success(result, newSearchEventAction);
//                    })
//                    .getResultAndLog();
//        }
    }

    private Query buildRawQuery(final String userInput) {
        return Strings.isNullOrEmpty(userInput)
                ? new Query()
                : Query.builder()
                        .withRaw("Account matches \""
                                 + Objects.requireNonNullElse(userInput, "")
                                 + "\"")
                        .build();
    }

    @Timed
    @Override
    public Integer create(final CreateAccountRequest request) {

        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId("CreateAccount")
                .withDescription("Create an account")
                .withDefaultEventAction(CreateEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType("Account")
                                .withName(request.getUserId())
                                .build())
                        .build())
                .withComplexLoggedResult(createEventAction -> {
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
                })
                .getResultAndLog();
    }

    @Timed
    @Override
    public Account fetch(final Integer userId) {
        if (userId == null) {
            return null;
        }
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "fetch"))
                .withDescription("Get a user by ID")
                .withDefaultEventAction(ViewEventAction.builder()
                        .addUser(User.builder()
                                .withId(String.valueOf(userId))
                                .build())
                        .build())
                .withComplexLoggedResult(viewEventAction -> {
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
                })
                .getResultAndLog();
    }

    private MultiObject getBefore(final int accountId) {
        User user = User.builder().withId("" + accountId).build();

        try {
            final Optional<Account> accountOptional = securityContextProvider.get().asProcessingUserResult(
                    () -> serviceProvider.get().read(accountId)
            );
            if (accountOptional.isPresent()) {
                user = userForAccount(accountOptional.get());
            }
        } catch (final Exception ex) {
            //Ignore
        }

        return MultiObject.builder().addUser(user).build();
    }

    private User userForAccount(final Account account) {
        final User.Builder<Void> builder = User.builder();

        if (account == null) {
            builder.withState("Not found");
        } else {
            builder.withName(account.getUserId())
                    .withState((account.isEnabled()
                            ? "Enabled"
                            : "Disabled") + "/"
                               + (account.isInactive()
                            ? "Inactive"
                            : "Active") + "/" + (account.isLocked()
                            ? "Locked"
                            : "Unlocked"))
                    .withId("" + account.getId())
                    .withEmailAddress(account.getEmail());
        }
        return builder.build();
    }

    @Timed
    @Override
    public Boolean update(final UpdateAccountRequest request,
                          final int accountId) {

        final User afterUser = userForAccount(request.getAccount());


        final Boolean result;
        try {
            result = stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                    .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "update"))
                    .withDescription("Update account for user " + accountId)
                    .withDefaultEventAction(UpdateEventAction.builder()
                            .withBefore(getBefore(accountId))
                            .withAfter(MultiObject.builder()
                                    .addUser(afterUser)
                                    .build())
                            .build())
                    .withSimpleLoggedResult(() -> {
                        serviceProvider.get()
                                .update(request, accountId);
                        return true;
                    })
                    .getResultAndLog();

            if (request.getPassword() != null) {
                // Password change so log that separately
                logChangePassword(accountId, afterUser, null);
            }
        } catch (final Exception e) {
            // Password change so log that separately
            logChangePassword(accountId, afterUser, e);
            throw e;
        }

        return result;
    }

    private void logChangePassword(final int accountId,
                                   final User user,
                                   final Throwable e) {
        stroomEventLoggingServiceProvider.get().log(
                "ChangePassword",
                "Change password for user " + accountId,
                AuthenticateEventAction.builder()
                        .withUser(user)
                        .withAction(AuthenticateAction.CHANGE_PASSWORD)
                        .withOutcome(EventLoggingUtil.createOutcome(AuthenticateOutcome.class, e))
                        .build());
    }

    @Timed
    @Override
    public Boolean delete(final int userId) {

        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "delete"))
                .withDescription("Deleting user account " + userId)
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addUser(User.builder()
                                .withId(String.valueOf(userId))
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> {
                    serviceProvider.get()
                            .delete(userId);
                    return true;
                })
                .getResultAndLog();
    }
}

