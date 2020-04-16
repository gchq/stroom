package stroom.authentication.account;

import stroom.job.api.RunnableWrapper;
import stroom.job.api.ScheduledJobsModule;

import javax.inject.Inject;

import static stroom.job.api.Schedule.ScheduleType.PERIODIC;

class AccountTaskJobsModule extends ScheduledJobsModule {
    @Override
    protected void configure() {
        super.configure();
        bindJob()
                .name("Account Maintenance")
                .description("Maintain user accounts such as disabling unused ones.")
                .schedule(PERIODIC, "1d")
                .to(AccountMaintenance.class);
    }

    private static class AccountMaintenance extends RunnableWrapper {
        @Inject
        AccountMaintenance(final AccountMaintenanceTask accountMaintenanceTask) {
            super(accountMaintenanceTask::exec);
        }
    }
}
