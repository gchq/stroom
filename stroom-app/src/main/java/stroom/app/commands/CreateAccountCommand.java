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

package stroom.app.commands;

import stroom.config.app.Config;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.shared.CreateAccountRequest;
import stroom.util.logging.LogUtil;

import com.google.inject.Injector;
import event.logging.CreateEventAction;
import event.logging.Outcome;
import event.logging.User;
import io.dropwizard.core.setup.Bootstrap;
import jakarta.inject.Inject;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;

/**
 * Creates an account in the internal identity provider
 */
public class CreateAccountCommand extends AbstractStroomAppCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateAccountCommand.class);
    private static final String COMMAND_NAME = "create_account";
    private static final String COMMAND_DESCRIPTION = "Creates the specified user account in the internal identity " +
            "provider";

    private static final String USERNAME_ARG_NAME = "user";
    private static final String PASSWORD_ARG_NAME = "password";
    private static final String EMAIL_ARG_NAME = "email";
    private static final String FIRST_NAME_ARG_NAME = "firstName";
    private static final String LAST_NAME_ARG_NAME = "lastName";
    private static final String NO_PASSWORD_CHANGE = "noPasswordChange";
    private static final String NEVER_EXPIRES_CHANGE_ARG_NAME = "neverExpires";

    private static final Set<String> ARGUMENT_NAMES = Set.of(
            USERNAME_ARG_NAME,
            PASSWORD_ARG_NAME,
            EMAIL_ARG_NAME,
            FIRST_NAME_ARG_NAME,
            LAST_NAME_ARG_NAME,
            NO_PASSWORD_CHANGE,
            NEVER_EXPIRES_CHANGE_ARG_NAME);

    private final Path configFile;

    @Inject
    private UserService userService;
    @Inject
    private AccountService accountService;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private StroomEventLoggingService stroomEventLoggingService;

    public CreateAccountCommand(final Path configFile) {
        super(configFile, COMMAND_NAME, COMMAND_DESCRIPTION);
        this.configFile = configFile;
    }

    @Override
    public void configure(final Subparser subparser) {
        super.configure(subparser);

        subparser.addArgument(asArg('u', USERNAME_ARG_NAME))
                .dest(USERNAME_ARG_NAME)
                .type(String.class)
                .required(true)
                .help("The user id of the account, e.g. 'admin'");

        subparser.addArgument(asArg('p', PASSWORD_ARG_NAME))
                .dest(PASSWORD_ARG_NAME)
                .type(String.class)
                .required(false)
                .help("The password for the account");

        subparser.addArgument(asArg('e', EMAIL_ARG_NAME))
                .dest(EMAIL_ARG_NAME)
                .type(String.class)
                .required(false)
                .help("The email address for the account");

        subparser.addArgument(asArg('f', FIRST_NAME_ARG_NAME))
                .dest(FIRST_NAME_ARG_NAME)
                .type(String.class)
                .required(false)
                .help("The user's first name");

        subparser.addArgument(asArg('s', LAST_NAME_ARG_NAME))
                .dest(FIRST_NAME_ARG_NAME)
                .type(String.class)
                .required(false)
                .help("The user's last name");

        subparser.addArgument(asArg(NO_PASSWORD_CHANGE))
                .dest(NO_PASSWORD_CHANGE)
                .action(Arguments.storeTrue())
                .setDefault(false)
                .required(false)
                .help("If set do not require a password change on first login");

        subparser.addArgument(asArg(NEVER_EXPIRES_CHANGE_ARG_NAME))
                .dest(NEVER_EXPIRES_CHANGE_ARG_NAME)
                .action(Arguments.storeTrue())
                .required(false)
                .help("The account will never expire");
    }

    @Override
    public Set<String> getArgumentNames() {
        return ARGUMENT_NAMES;
    }

    @Override
    protected void runSecuredCommand(final Bootstrap<Config> bootstrap,
                                     final Namespace namespace,
                                     final Config config,
                                     final Injector injector) {

        injector.injectMembers(this);

        final String username = namespace.getString(USERNAME_ARG_NAME);

        accountService.read(username)
                .ifPresentOrElse(
                        account -> {
                            final String msg = LogUtil.message("An account for user '{}' already exists",
                                    username);
                            logEvent(username, false, msg);
                            throw new RuntimeException(msg);
                        },
                        () -> {
                            createAccount(namespace, username);
                            final String msg = LogUtil.message("Account creation complete for user '{}'",
                                    username);
                            info(LOGGER, msg);
                            logEvent(username, true, msg);
                        });
    }

    private void createAccount(final Namespace namespace, final String username) {
        final String password = namespace.getString(PASSWORD_ARG_NAME);
        final String email = namespace.getString(EMAIL_ARG_NAME);
        final String firstName = namespace.getString(FIRST_NAME_ARG_NAME);
        final String lastName = namespace.getString(LAST_NAME_ARG_NAME);
        final boolean noPasswordChange = namespace.getBoolean(NO_PASSWORD_CHANGE);
        final boolean neverExpires = namespace.getBoolean(NEVER_EXPIRES_CHANGE_ARG_NAME);
        final long now = System.currentTimeMillis();

        LOGGER.info("Creating account for user '{}'", username);

        final CreateAccountRequest createAccountRequest = new CreateAccountRequest(
                firstName,
                lastName,
                username,
                email,
                null,
                password,
                password,
                !noPasswordChange,
                neverExpires);

        accountService.create(createAccountRequest);
    }

    private void logEvent(final String username,
                          final boolean wasSuccessful,
                          final String description) {

        stroomEventLoggingService.log(
                "CliCreateInternalIdentityProviderUser",
                LogUtil.message("An account for user {} was created in the internal identity provider", username),
                CreateEventAction.builder()
                        .addUser(User.builder()
                                .withName(username)
                                .build())
                        .withOutcome(Outcome.builder()
                                .withSuccess(wasSuccessful)
                                .withDescription(description)
                                .build())
                        .build());
    }
}
