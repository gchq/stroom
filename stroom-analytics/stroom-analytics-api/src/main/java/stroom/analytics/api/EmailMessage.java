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

package stroom.analytics.api;

/// An email to send with [EmailService].
///
/// Each of `to`, `cc` and `bcc` is a `;` separated list of email addresses.
/// Any of them may be `null` or blank, as long as at least one recipient is given.
///
/// @param to      The `;` separated addresses to send the email to.
/// @param cc      The `;` separated addresses to copy the email to.
/// @param bcc     The `;` separated addresses to blind copy the email to.
/// @param subject The single line subject of the email.
/// @param body    The body of the email, as plain text or HTML.
public record EmailMessage(String to,
                           String cc,
                           String bcc,
                           String subject,
                           String body) {

}
