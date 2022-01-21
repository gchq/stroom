package stroom.security.identity.account;

import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.task.api.TaskContext;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
class AccountMaintenanceTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountMaintenanceTask.class);

    private final Provider<PasswordPolicyConfig> passwordPolicyConfigProvider;
    private final AccountDao accountDao;
    private final TaskContext taskContext;

    @Inject
    AccountMaintenanceTask(final Provider<PasswordPolicyConfig> passwordPolicyConfigProvider,
                           final AccountDao accountDao,
                           final TaskContext taskContext) {
        this.passwordPolicyConfigProvider = passwordPolicyConfigProvider;
        this.accountDao = accountDao;
        this.taskContext = taskContext;
    }

    public void exec() {
        LOGGER.info("Checking for accounts that are not being used.");
        taskContext.info(() -> "Checking for accounts that are not being used.");

        final StroomDuration neverUsedAgeThreshold = passwordPolicyConfigProvider.get()
                .getNeverUsedAccountDeactivationThreshold();
        int numberOfInactiveNewAccounts = accountDao.deactivateNewInactiveUsers(neverUsedAgeThreshold.getDuration());
        LOGGER.info("Deactivated {} new user account(s) that have been inactive for {} or more.",
                numberOfInactiveNewAccounts, neverUsedAgeThreshold);
        taskContext.info(() -> LogUtil.message(
                "Deactivated {} new user account(s) that have been inactive for {} or more.",
                numberOfInactiveNewAccounts,
                neverUsedAgeThreshold));

        final StroomDuration unusedAgeThreshold = passwordPolicyConfigProvider.get()
                .getUnusedAccountDeactivationThreshold();
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
