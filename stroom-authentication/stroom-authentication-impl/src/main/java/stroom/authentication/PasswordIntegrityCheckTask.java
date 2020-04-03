package stroom.authentication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.config.PasswordIntegrityChecksConfig;
import stroom.authentication.dao.UserDao;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.TimerTask;

@Singleton
class PasswordIntegrityCheckTask extends TimerTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordIntegrityCheckTask.class);
    private PasswordIntegrityChecksConfig passwordIntegrityChecksConfig;
    private UserDao userDao;

    @Inject
    PasswordIntegrityCheckTask(PasswordIntegrityChecksConfig passwordIntegrityChecksConfig, UserDao userDao) {
        this.passwordIntegrityChecksConfig = passwordIntegrityChecksConfig;
        this.userDao = userDao;
    }

    @Override
    public void run() {
        LOGGER.info("Checking for accounts that are not being used.");

        int numberOfInactiveNewAccounts = userDao.deactivateNewInactiveUsers(passwordIntegrityChecksConfig.getNeverUsedAccountDeactivationThreshold().getDuration());
        LOGGER.info("Deactivated {} new user account(s) that have been inactive for duration of {} or more.",
                numberOfInactiveNewAccounts, passwordIntegrityChecksConfig.getNeverUsedAccountDeactivationThreshold());

        int numberOfInactiveAccounts = userDao.deactivateInactiveUsers(passwordIntegrityChecksConfig.getUnusedAccountDeactivationThreshold().getDuration());
        LOGGER.info("Deactivated {} user account(s) that have been inactive for  duration of {} days or more.",
                numberOfInactiveAccounts, passwordIntegrityChecksConfig.getUnusedAccountDeactivationThreshold());

        // TODO password change checks
    }
}
