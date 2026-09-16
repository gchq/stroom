/*
 * Copyright 2022 Crown Copyright
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

package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.repo.CSVFormatter;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.receive.common.RequestAuthenticator;
import stroom.receive.common.StroomStreamException;
import stroom.receive.common.StroomStreamStatus;
import stroom.security.api.CommonSecurityContext;
import stroom.security.api.UserIdentity;
import stroom.util.cert.CertificateExtractor;
import stroom.util.concurrent.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.Metrics;

import com.codahale.metrics.Timer;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class ReceiveDataHelper {

    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveDataHelper.class);

    private final RequestAuthenticator requestAuthenticator;
    private final CertificateExtractor certificateExtractor;
    private final CommonSecurityContext commonSecurityContext;
    private final LogStream logStream;
    private final ReceiptIdGenerator receiptIdGenerator;
    private final Timer receiveStreamTimer;
    private final Timer receiveHandleTimer;

    @Inject
    public ReceiveDataHelper(final RequestAuthenticator requestAuthenticator,
                             final CertificateExtractor certificateExtractor,
                             final CommonSecurityContext commonSecurityContext,
                             final LogStream logStream,
                             final ReceiptIdGenerator receiptIdGenerator,
                             final Metrics metrics) {
        this.requestAuthenticator = requestAuthenticator;
        this.certificateExtractor = certificateExtractor;
        this.commonSecurityContext = commonSecurityContext;
        this.logStream = logStream;
        this.receiptIdGenerator = receiptIdGenerator;

        this.receiveStreamTimer = metrics.registrationBuilder(getClass())
                .addNamePart(Metrics.RECEIVE)
                .addNamePart(Metrics.STREAM)
                .timer()
                .createAndRegister();
        this.receiveHandleTimer = metrics.registrationBuilder(getClass())
                .addNamePart(Metrics.RECEIVE)
                .addNamePart(Metrics.HANDLE)
                .timer()
                .createAndRegister();
    }

    /**
     * Authenticate the sender, build the attribute map with a fresh receipt id, and run the handler
     * as the processing user, mapping anything it throws to the status the sender should see.
     */
    public UniqueId process(final HttpServletRequest request,
                            final Handler handler) throws StroomStreamException {
        final Instant startTime = Instant.now();
        // Create a new proxy id for the request, so we can track progress and report back the UUID to the sender,
        final UniqueId receiptId = receiptIdGenerator.generateId();

        // Create attribute map from headers.
        final AttributeMap attributeMap = AttributeMapUtil.create(
                request,
                certificateExtractor,
                startTime,
                receiptId);
        try {
            receiveStreamTimer.time(() -> {
                // Authorise request.
                final UserIdentity userIdentity = requestAuthenticator.authenticate(request, attributeMap);
                LOGGER.debug("process() - userIdentity: {}", userIdentity);

                receiveHandleTimer.time(() -> {
                    // The sender is authenticated; from here on run as the processing user, because
                    // the receipt policy checks feed status and the sender has no right to.
                    commonSecurityContext.asProcessingUser(() ->
                            handler.handle(request, attributeMap, receiptId));
                });
            });
        } catch (final Throwable e) {
            // Add the proxy request id to help error diagnosis.
            final AttributeMap errAttributeMap = AttributeMapUtil.create(
                    request,
                    certificateExtractor,
                    startTime,
                    receiptId);
            final StroomStreamException stroomStreamException = StroomStreamException.create(
                    e, errAttributeMap);

            final StroomStreamStatus status = stroomStreamException.getStroomStreamStatus();

            LOGGER.debug(() -> LogUtil.message("\"handleException()\",{},\"{}\"",
                    CSVFormatter.format(stroomStreamException.getAttributeMap(), true),
                    CSVFormatter.escape(stroomStreamException.getMessage())));

            final long durationMs = System.currentTimeMillis() - startTime.toEpochMilli();

            logStream.log(
                    RECEIVE_LOG,
                    stroomStreamException,
                    request.getRequestURI(),
                    receiptId.toString(),
                    -1,
                    durationMs);

            throw stroomStreamException;
        }

        return receiptId;
    }


    // --------------------------------------------------------------------------------


    public interface Handler {

        void handle(HttpServletRequest request,
                    AttributeMap attributeMap,
                    UniqueId receiptId);
    }
}
