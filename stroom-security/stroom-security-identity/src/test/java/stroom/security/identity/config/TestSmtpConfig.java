/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.identity.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestSmtpConfig {

    private static final String PASSWORD = "TOTALLY-SECRET-SMTP-PASSWORD";

    @Test
    void theSmtpPasswordIsNotRendered() {
        final String rendered = smtpConfig().toString();

        assertThat(rendered)
                .as("the SMTP credential must not survive into anything loggable")
                .doesNotContain(PASSWORD);
        // The rest is still useful for diagnosing mail problems, which is why the object is logged at all.
        assertThat(rendered).contains("mail.example.com", "smtp-user");
    }

    @Test
    void theSmtpPasswordIsNotRenderedThroughTheConfigThatContainsIt() {
        // The harm is indirect: nothing logs an SmtpConfig on its own, but a debug log or config dump of the
        // identity configuration renders the whole tree, and toString chains all the way down.
        final EmailConfig emailConfig = new EmailConfig(
                smtpConfig(), "from@example.com", "Stroom", "subject", "body");

        final String rendered = emailConfig.toString();

        assertThat(rendered).doesNotContain(PASSWORD);
        // Asserted so the absence above cannot pass vacuously: this proves the chain really did render the
        // SmtpConfig, rather than the password being missing because nothing was rendered at all.
        assertThat(rendered)
                .as("EmailConfig must actually be chaining SmtpConfig")
                .contains("mail.example.com");
    }

    private static SmtpConfig smtpConfig() {
        return new SmtpConfig("mail.example.com", 2525, "plain", "smtp-user", PASSWORD);
    }
}
