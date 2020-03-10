package stroom.authentication.service;

import com.codahale.metrics.health.HealthCheck;
import stroom.authentication.EmailSender;
import stroom.authentication.config.EmailConfig;
import stroom.authentication.resources.user.v1.User;

/**
 * A health check for email sending. It tries to send an email and logs any exception that comes out.
 */
public class EmailHealthCheck extends HealthCheck {
    private EmailSender emailSender;
    private EmailConfig emailConfig;

    public EmailHealthCheck(EmailSender emailSender, EmailConfig emailConfig) {
        this.emailSender = emailSender;
        this.emailConfig = emailConfig;
    }

    @Override
    protected Result check() {
        if (emailConfig.isAllowPasswordResets()) {
            try {
                User dummyUser = new User("dummy_email@dummydummydummydummy.abc", "Dummy password");
                dummyUser.setFirstName("Dummy first name");
                dummyUser.setLastName("Dummy last name");
                emailSender.send(dummyUser, "Dummy token");
            } catch (Exception ex) {
                return Result.unhealthy(ex);
            }
            return Result.healthy();
        } else {
            return Result.healthy("Emailing is disabled");
        }
    }
}
