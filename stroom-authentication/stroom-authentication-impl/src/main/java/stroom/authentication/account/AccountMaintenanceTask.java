package stroom.authentication.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.config.PasswordIntegrityChecksConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class AccountMaintenanceTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountMaintenanceTask.class);

    private final PasswordIntegrityChecksConfig passwordIntegrityChecksConfig;
    private final AccountDao accountDao;

    @Inject
    AccountMaintenanceTask(final PasswordIntegrityChecksConfig passwordIntegrityChecksConfig,
                           final AccountDao accountDao) {
        this.passwordIntegrityChecksConfig = passwordIntegrityChecksConfig;
        this.accountDao = accountDao;
    }

    public void exec() {
        LOGGER.info("Checking for accounts that are not being used.");

        int numberOfInactiveNewAccounts = accountDao.deactivateNewInactiveUsers(passwordIntegrityChecksConfig.getNeverUsedAccountDeactivationThreshold().getDuration());
        LOGGER.info("Deactivated {} new user account(s) that have been inactive for duration of {} or more.",
                numberOfInactiveNewAccounts, passwordIntegrityChecksConfig.getNeverUsedAccountDeactivationThreshold());

        int numberOfInactiveAccounts = accountDao.deactivateInactiveUsers(passwordIntegrityChecksConfig.getUnusedAccountDeactivationThreshold().getDuration());
        LOGGER.info("Deactivated {} user account(s) that have been inactive for  duration of {} days or more.",
                numberOfInactiveAccounts, passwordIntegrityChecksConfig.getUnusedAccountDeactivationThreshold());

        // TODO password change checks
    }
}
