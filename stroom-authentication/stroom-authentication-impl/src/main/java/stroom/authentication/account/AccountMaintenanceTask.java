package stroom.authentication.account;

import stroom.authentication.config.PasswordIntegrityChecksConfig;
import stroom.util.time.StroomDuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        final StroomDuration neverUsedAgeThreshold = passwordIntegrityChecksConfig.getNeverUsedAccountDeactivationThreshold();
        int numberOfInactiveNewAccounts = accountDao.deactivateNewInactiveUsers(neverUsedAgeThreshold.getDuration());
        LOGGER.info("Deactivated {} new user account(s) that have been inactive for {} or more.",
                numberOfInactiveNewAccounts, neverUsedAgeThreshold);

        final StroomDuration unusedAgeThreshold = passwordIntegrityChecksConfig.getUnusedAccountDeactivationThreshold();
        int numberOfInactiveAccounts = accountDao.deactivateInactiveUsers(unusedAgeThreshold.getDuration());
        LOGGER.info("Deactivated {} user account(s) that have been inactive for {} or more.",
                numberOfInactiveAccounts, unusedAgeThreshold);

        // TODO password change checks
    }
}
