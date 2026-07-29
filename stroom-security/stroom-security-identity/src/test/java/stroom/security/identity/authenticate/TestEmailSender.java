/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.identity.authenticate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestEmailSender {

    private static final String URL = "https://stroom.example.com/reset?token=abc";

    @Test
    void missingNamePartIsNotAddressedTo() {
        // The recipient sees how they were addressed, so joining a missing part gave them "John null".
        assertThat(EmailSender.buildRecipientName("John", null)).isEqualTo("John");
        assertThat(EmailSender.buildRecipientName(null, "Smith")).isEqualTo("Smith");
        assertThat(EmailSender.buildRecipientName("John", "Smith")).isEqualTo("John Smith");
        assertThat(EmailSender.buildRecipientName(null, null)).isEqualTo("[Name not available]");
        assertThat(EmailSender.buildRecipientName("John", " ")).isEqualTo("John");
    }

    @Test
    void strayPercentInTheOperatorsTextDoesNotStopTheEmail() {
        // The body is operator-supplied, so it is not a trusted format string. This used to throw on the
        // executor, after the endpoint had already told the user their email was on its way.
        final String text = EmailSender.buildResetText("Reset here: %s. Discount is 50% off.", URL);

        assertThat(text).contains(URL);
    }

    @Test
    void textWithNoPlaceholderStillCarriesTheLink() {
        // Worse than an exception: the email arrives looking fine, with no way to reset anything.
        final String text = EmailSender.buildResetText("Please reset your password.", URL);

        assertThat(text)
                .contains("Please reset your password.")
                .contains(URL);
    }

    @Test
    void wellFormedTemplateIsUsedAsWritten() {
        assertThat(EmailSender.buildResetText("Go to %s now", URL)).isEqualTo("Go to " + URL + " now");
    }
}
