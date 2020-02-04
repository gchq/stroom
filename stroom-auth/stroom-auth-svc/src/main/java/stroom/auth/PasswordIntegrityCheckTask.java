package stroom.auth;

import stroom.auth.config.PasswordIntegrityChecksConfig;
import stroom.auth.daos.UserDao;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.TimerTask;

@Singleton
public class PasswordIntegrityCheckTask extends TimerTask {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PasswordIntegrityCheckTask.class);
    private PasswordIntegrityChecksConfig passwordIntegrityChecksConfig;
    private UserDao userDao;

    @Inject
    public PasswordIntegrityCheckTask(PasswordIntegrityChecksConfig passwordIntegrityChecksConfig, UserDao userDao) {
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
