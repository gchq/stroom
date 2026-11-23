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

package stroom.pipeline.xsltfunctions;

import stroom.analytics.api.EmailMessage;
import stroom.analytics.api.EmailService;
import stroom.pipeline.filter.XsltConfig;
import stroom.pipeline.state.PipelineContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// `stroom:send-email($to, $subject, $body)` and `stroom:send-email($to, $subject, $body, $cc, $bcc)`.
/// Sends an email using the SMTP server configured for analytics.
///
/// One email is sent per call, so a pipeline that calls this for every record sends an email per record.
/// Nothing is sent while stepping, so that stepping through data never emails anyone.
/// The recipients and the number of emails sent in one run are limited by [XsltConfig].
@XsltFunctionDef(
        name = SendEmail.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.OTHER,
        commonSubCategories = {"Email"},
        commonDescription = """
                Sends an email using the SMTP server and from address configured in
                `stroom.analytics.emailConfig`.
                Each address argument is a `;` separated list of email addresses, and there must be at least
                one recipient across `to`, `cc` and `bcc`.
                If the body contains HTML tags it is sent as HTML, otherwise as plain text.

                An email is sent every time the function is called, so calling it for every record will send
                an email for every record, up to the limit set by `stroom.pipeline.xslt.maxEmailsPerRun`.
                If `stroom.pipeline.xslt.allowedEmailRecipients` is set, every recipient must be one of those.
                No email is sent while stepping. Instead an INFO message shows the email that would have
                been sent.

                As the function returns an empty sequence, call it somewhere its result is used, e.g.
                `<xsl:sequence select="stroom:send-email(...)"/>`, otherwise the XSLT processor may not call it.
                """,
        commonReturnType = XsltDataType.EMPTY_SEQUENCE,
        commonReturnDescription = "An empty sequence.",
        signatures = {
                @XsltFunctionSignature(
                        args = {
                                @XsltFunctionArg(
                                        name = "to",
                                        description = "The `;` separated addresses to send the email to.",
                                        argType = XsltDataType.STRING
                                ),
                                @XsltFunctionArg(
                                        name = "subject",
                                        description = "The subject of the email. It must be a single line.",
                                        argType = XsltDataType.STRING
                                ),
                                @XsltFunctionArg(
                                        name = "body",
                                        description = "The body of the email, as plain text or HTML.",
                                        argType = XsltDataType.STRING
                                ),
                                @XsltFunctionArg(
                                        name = "cc",
                                        description = "The `;` separated addresses to copy the email to.",
                                        argType = XsltDataType.STRING,
                                        isOptional = true
                                ),
                                @XsltFunctionArg(
                                        name = "bcc",
                                        description = "The `;` separated addresses to blind copy the email to.",
                                        argType = XsltDataType.STRING,
                                        isOptional = true
                                )
                        }
                )
        })
class SendEmail extends StroomExtensionFunctionCall {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SendEmail.class);

    public static final String FUNCTION_NAME = "send-email";

    // A single address with no display name, so it can be compared with the allowed recipients.
    private static final String ADDRESS_PART = "[^\\s@<>,;:\"()\\[\\]\\\\]+";
    private static final Pattern PLAIN_ADDRESS = Pattern.compile(ADDRESS_PART + "@" + ADDRESS_PART);

    private final EmailService emailService;
    private final PipelineContext pipelineContext;
    private final Provider<XsltConfig> xsltConfigProvider;
    // A new instance is made for each run of an XSLT, so this counts the emails sent in one run.
    private int emailsSent;

    @Inject
    SendEmail(final EmailService emailService,
              final PipelineContext pipelineContext,
              final Provider<XsltConfig> xsltConfigProvider) {
        this.emailService = emailService;
        this.pipelineContext = pipelineContext;
        this.xsltConfigProvider = xsltConfigProvider;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        try {
            final String to = getSafeString(functionName, context, arguments, 0);
            final String subject = getSafeString(functionName, context, arguments, 1);
            final String body = getSafeString(functionName, context, arguments, 2);
            final String cc = getOptionalString(functionName, context, arguments, 3);
            final String bcc = getOptionalString(functionName, context, arguments, 4);
            final EmailMessage emailMessage = new EmailMessage(to, cc, bcc, subject, body);
            final XsltConfig xsltConfig = xsltConfigProvider.get();
            final String refusedRecipient = findRefusedRecipient(
                    xsltConfig.getAllowedEmailRecipients(), to, cc, bcc);

            if (refusedRecipient != null) {
                log(context, Severity.ERROR, LogUtil.message(
                        "Email not sent as '{}' is not an allowed recipient", refusedRecipient), null);
            } else if (pipelineContext.isStepping()) {
                log(context, Severity.INFO, LogUtil.message(
                        "Email not sent while stepping. To: '{}', Cc: '{}', Bcc: '{}', Subject: '{}'",
                        to, cc, bcc, subject), null);
            } else if (emailsSent >= xsltConfig.getMaxEmailsPerRun()) {
                log(context, Severity.ERROR, LogUtil.message(
                        "Email not sent as the limit of {} emails in one run has been reached",
                        xsltConfig.getMaxEmailsPerRun()), null);
            } else {
                // Counted before sending, so failed sends count too.
                emailsSent++;
                LOGGER.debug("Sending email {}", emailMessage);
                emailService.sendEmail(emailMessage);
            }
        } catch (final XPathException | RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return EmptyAtomicSequence.getInstance();
    }

    /// Finds the first recipient that is not allowed.
    ///
    /// @param allowedRecipients The allowed addresses and `@domain` entries, or empty to allow any recipient.
    /// @param addressLists      The `;` separated recipient lists, any of which may be null.
    /// @return The first recipient that is not allowed, or null if all are allowed.
    static String findRefusedRecipient(final List<String> allowedRecipients, final String... addressLists) {
        if (allowedRecipients.isEmpty()) {
            return null;
        }
        final Set<String> allowed = allowedRecipients.stream()
                .map(recipient -> recipient.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (final String addressList : addressLists) {
            if (addressList != null) {
                // Split as the email service does.
                for (final String part : addressList.split(";")) {
                    final String address = part.trim().toLowerCase(Locale.ROOT);
                    if (!address.isEmpty() && !isAllowed(allowed, address)) {
                        return part.trim();
                    }
                }
            }
        }
        return null;
    }

    private static boolean isAllowed(final Set<String> allowed, final String address) {
        return PLAIN_ADDRESS.matcher(address).matches()
               && (allowed.contains(address) || allowed.contains(address.substring(address.indexOf('@'))));
    }

    private String getOptionalString(final String functionName,
                                     final XPathContext context,
                                     final Sequence[] arguments,
                                     final int index) throws XPathException {
        return arguments.length > index
                ? getSafeString(functionName, context, arguments, index)
                : null;
    }
}
