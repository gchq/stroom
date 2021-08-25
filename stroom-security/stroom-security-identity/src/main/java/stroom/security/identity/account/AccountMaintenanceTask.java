package stroom.security.identity.account;

import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.task.api.TaskContext;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class AccountMaintenanceTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountMaintenanceTask.class);

    private final PasswordPolicyConfig passwordPolicyConfig;
    private final AccountDao accountDao;
    private final TaskContext taskContext;

    @Inject
    AccountMaintenanceTask(final PasswordPolicyConfig passwordPolicyConfig,
                           final AccountDao accountDao,
                           final TaskContext taskContext) {
        this.passwordPolicyConfig = passwordPolicyConfig;
        this.accountDao = accountDao;
        this.taskContext = taskContext;
    }

    public void exec() {
        LOGGER.info("Checking for accounts that are not being used.");
        taskContext.info(() -> "Checking for accounts that are not being used.");

        final StroomDuration neverUsedAgeThreshold = passwordPolicyConfig.getNeverUsedAccountDeactivationThreshold();
        int numberOfInactiveNewAccounts = accountDao.deactivateNewInactiveUsers(neverUsedAgeThreshold.getDuration());
        LOGGER.info("Deactivated {} new user account(s) that have been inactive for {} or more.",
                numberOfInactiveNewAccounts, neverUsedAgeThreshold);
        taskContext.info(() -> LogUtil.message(
                "Deactivated {} new user account(s) that have been inactive for {} or more.",
                numberOfInactiveNewAccounts,
                neverUsedAgeThreshold));

        final StroomDuration unusedAgeThreshold = passwordPolicyConfig.getUnusedAccountDeactivationThreshold();
        int numberOfInactiveAccounts = accountDao.deactivateInactiveUsers(unusedAgeThreshold.getDuration());
        LOGGER.info("Deactivated {} user account(s) that have been inactive for {} or more.",
                numberOfInactiveAccounts, unusedAgeThreshold);
        taskContext.info(() -> LogUtil.message(
                "Deactivated {} user account(s) that have been inactive for {} or more.",
                numberOfInactiveAccounts,
                unusedAgeThreshold));

        // TODO password change checks
    }
}
