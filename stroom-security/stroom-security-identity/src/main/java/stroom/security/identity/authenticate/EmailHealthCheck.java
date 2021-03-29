package stroom.security.identity.authenticate;

import stroom.security.identity.config.EmailConfig;

import com.codahale.metrics.health.HealthCheck;

/**
 * A health check for email sending. It tries to send an email and logs any exception that comes out.
 */
public class EmailHealthCheck extends HealthCheck {

    private final EmailSender emailSender;
    private final EmailConfig emailConfig;

    public EmailHealthCheck(final EmailSender emailSender,
                            final EmailConfig emailConfig) {
        this.emailSender = emailSender;
        this.emailConfig = emailConfig;
    }

    @Override
    protected Result check() {
        if (emailConfig.isAllowPasswordResets()) {
            try {
                emailSender.send("dummy_email@dummydummydummydummy.abc",
                        "Dummy first name",
                        "Dummy last name",
                        "Dummy token");
            } catch (Exception ex) {
                return Result.unhealthy(ex);
            }
            return Result.healthy();
        } else {
            return Result.healthy("Emailing is disabled");
        }
    }
}
