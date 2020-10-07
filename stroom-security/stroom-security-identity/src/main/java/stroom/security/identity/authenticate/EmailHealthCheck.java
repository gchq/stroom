package stroom.security.identity.authenticate;

import com.codahale.metrics.health.HealthCheck;
import stroom.security.identity.config.EmailConfig;

/**
 * A health check for email sending. It tries to send an email and logs any exception that comes out.
 */
public class EmailHealthCheck extends HealthCheck {
    private EmailSender emailSender;
    private EmailConfig emailConfig;

    public EmailHealthCheck(final EmailSender emailSender,
                            final EmailConfig emailConfig) {
        this.emailSender = emailSender;
        this.emailConfig = emailConfig;
    }

    @Override
    protected Result check() {
        if (emailConfig.isAllowPasswordResets()) {
            try {
                emailSender.send("dummy_email@dummydummydummydummy.abc", "Dummy first name", "Dummy last name", "Dummy token");
            } catch (Exception ex) {
                return Result.unhealthy(ex);
            }
            return Result.healthy();
        } else {
            return Result.healthy("Emailing is disabled");
        }
    }
}
