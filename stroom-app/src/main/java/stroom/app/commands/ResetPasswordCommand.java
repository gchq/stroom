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
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.shared.Account;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.google.inject.Injector;
import event.logging.AuthenticateAction;
import event.logging.AuthenticateEventAction;
import event.logging.AuthenticateOutcome;
import event.logging.User;
import event.logging.util.EventLoggingUtil;
import io.dropwizard.core.setup.Bootstrap;
import jakarta.inject.Inject;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;

/**
 * Resets the password of an account in the internal identity provider
 */
public class ResetPasswordCommand extends AbstractStroomAppCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResetPasswordCommand.class);

    private static final String COMMAND_NAME = "reset_password";
    private static final String COMMAND_DESCRIPTION = "Reset the password of the user account " +
                                                      "in the internal identity provider";

    private static final String USERNAME_ARG_NAME = "user";
    private static final String PASSWORD_ARG_NAME = "password";

    private static final Set<String> ARGUMENT_NAMES = Set.of(
            USERNAME_ARG_NAME,
            PASSWORD_ARG_NAME);

    @Inject
    private AccountDao accountDao;
    @Inject
    private StroomEventLoggingService stroomEventLoggingService;

    public ResetPasswordCommand(final Path configFile) {
        super(configFile, COMMAND_NAME, COMMAND_DESCRIPTION);
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
                .required(true)
                .help("The new password for the account");
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
        final String username = namespace.getString(USERNAME_ARG_NAME);
        if (NullSafe.isEmptyString(username)) {
            throw new RuntimeException("Username must be provided");
        }

        final String newPassword = namespace.getString(PASSWORD_ARG_NAME);
        if (NullSafe.isEmptyString(newPassword)) {
            throw new RuntimeException("Password must be provided");
        }

        LOGGER.debug("Resetting password for account {}", username);

        injector.injectMembers(this);

        // Refuse to reset a disabled account. resetPassword() clears the disabled/inactive/locked flags as
        // well as setting the password, so resetting a deliberately disabled account would silently
        // re-enable it. Require an administrator to enable it explicitly first.
        final Account account = accountDao.get(username)
                .orElseThrow(() -> new RuntimeException("No account exists for user " + username));
        if (!account.isEnabled()) {
            throw new RuntimeException("The account for user " + username + " is disabled. Enable it first "
                                       + "(e.g. with the 'manage_users' command) before resetting its "
                                       + "password, so the reset does not silently re-enable it.");
        }

        accountDao.resetPassword(username, newPassword);

        final String msg = LogUtil.message("Password reset complete for user {}", username);
        LOGGER.info(msg);
        logEvent(username, true, msg);
    }

    private void logEvent(final String username,
                          final boolean wasSuccessful,
                          final String description) {

        stroomEventLoggingService.log(
                "CliChangePassword",
                LogUtil.message("The password for user {} was changed from the command line", username),
                AuthenticateEventAction.builder()
                        .withAction(AuthenticateAction.CHANGE_PASSWORD)
                        .withUser(User.builder()
                                .withName(username)
                                .build())
                        .withOutcome(EventLoggingUtil.createOutcome(
                                AuthenticateOutcome.class,
                                wasSuccessful,
                                description))
                        .build());
    }
}
