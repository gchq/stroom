/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.DataReceiptMetrics;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.receive.common.RequestAuthenticator;
import stroom.receive.common.RequestHandler;
import stroom.receive.common.StroomStreamException;
import stroom.security.api.CommonSecurityContext;
import stroom.security.api.UserIdentity;
import stroom.util.cert.CertificateExtractor;
import stroom.util.concurrent.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Main entry point to handling datafeed requests into Stroom-Proxy.
 * Stroom has its own handler.
 */
public class ProxyRequestHandler implements RequestHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyRequestHandler.class);
    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");
    private static final String ZERO_CONTENT = "0";

    private final RequestAuthenticator requestAuthenticator;
    private final CertificateExtractor certificateExtractor;
    private final ReceiverFactory receiverFactory;
    private final ReceiptIdGenerator receiptIdGenerator;
    private final DataReceiptMetrics dataReceiptMetrics;
    private final CommonSecurityContext commonSecurityContext;
    private final LogStream logStream;

    @Inject
    public ProxyRequestHandler(final RequestAuthenticator requestAuthenticator,
                               final CertificateExtractor certificateExtractor,
                               final ReceiverFactory receiverFactory,
                               final ReceiptIdGenerator receiptIdGenerator,
                               final DataReceiptMetrics dataReceiptMetrics,
                               final CommonSecurityContext commonSecurityContext,
                               final LogStream logStream) {
        this.requestAuthenticator = requestAuthenticator;
        this.certificateExtractor = certificateExtractor;
        this.receiverFactory = receiverFactory;
        this.receiptIdGenerator = receiptIdGenerator;
        this.dataReceiptMetrics = dataReceiptMetrics;
        this.commonSecurityContext = commonSecurityContext;
        this.logStream = logStream;
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        dataReceiptMetrics.timeRequest(() -> {
            doHandle(request, response);
        });
    }

    private void doHandle(final HttpServletRequest request, final HttpServletResponse response) {

        final Instant receiveTime = Instant.now();
        // Create a new proxy id for the request, so we can track progress of the stream
        // through the various proxies and into stroom and report back the ID to the sender,
        final UniqueId receiptId = receiptIdGenerator.generateId();
        AttributeMap attributeMap = null;
        try {
            // Create attribute map from headers.
            attributeMap = AttributeMapUtil.create(
                    request,
                    certificateExtractor,
                    receiveTime,
                    receiptId);
            final AttributeMap finAttributeMap = attributeMap;

            LOGGER.debug(() -> LogUtil.message(
                    "handle() - requestUri: {}, remoteHost/Addr: {}, attributeMap: {}, ",
                    request.getRequestURI(),
                    Objects.requireNonNullElseGet(
                            request.getRemoteHost(),
                            request::getRemoteAddr),
                    finAttributeMap));

            // Authorise request.
            final UserIdentity userIdentity = requestAuthenticator.authenticate(request, attributeMap);

            LOGGER.debug("handle() - userIdentity: {}", userIdentity);

            // Treat differently depending on compression type.
            final String compression = AttributeMapUtil.validateAndNormaliseCompression(
                    attributeMap,
                    compressionVal -> new StroomStreamException(
                            StroomStatusCode.UNKNOWN_COMPRESSION, finAttributeMap, compressionVal));

            final Receiver receiver;
            final String contentLength = attributeMap.get(StandardHeaderArguments.CONTENT_LENGTH);
            dataReceiptMetrics.recordContentLength(contentLength);
            if (ZERO_CONTENT.equals(contentLength)) {
                LOGGER.warn("process() - Skipping Zero Content " + attributeMap);
                receiver = null;
            } else {
                // We have authenticated the user to accept the data, but from here on,
                // we run as the processing user as the request user won't have perms to do
                // things like check feed status.
                receiver = commonSecurityContext.asProcessingUserResult(() -> {
                    final Receiver receiver2 = receiverFactory.get(finAttributeMap);
                    receiver2.receive(
                            receiveTime,
                            finAttributeMap,
                            request.getRequestURI(),
                            request::getInputStream);
                    return receiver2;
                });
            }

            response.setStatus(HttpStatus.SC_OK);

            LOGGER.debug(() -> LogUtil.message(
                    "Writing proxy receipt id {} to response. Receiver: {}, duration: {}, compression: '{}'",
                    receiptId,
                    NullSafe.get(receiver, Object::getClass, Class::getSimpleName),
                    Duration.between(receiveTime, Instant.now()),
                    compression));
            try (final PrintWriter writer = response.getWriter()) {
                writer.println(receiptId);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        } catch (final Exception e) {
            final StroomStreamException stroomStreamException = StroomStreamException.create(
                    e, Objects.requireNonNullElseGet(attributeMap, AttributeMap::new));
            logStream.log(
                    RECEIVE_LOG,
                    stroomStreamException,
                    request.getRequestURI(),
                    receiptId.toString(),
                    -1,
                    Duration.between(receiveTime, Instant.now()).toMillis());
            // Craft an error response for the client
            stroomStreamException.sendErrorResponse(response);
        }
    }
}
