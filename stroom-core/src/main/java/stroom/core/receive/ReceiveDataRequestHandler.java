/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.core.receive;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaService;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.receive.common.RequestAuthenticator;
import stroom.receive.common.RequestHandler;
import stroom.receive.common.StreamTargetStreamHandlers;
import stroom.receive.common.StroomStreamException;
import stroom.receive.common.StroomStreamProcessor;
import stroom.receive.common.StroomStreamStatus;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskProgressHandler;
import stroom.util.cert.CertificateExtractor;
import stroom.util.concurrent.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * <p>
 * Handle the incoming requests and stream them to disk checking a few things.
 * </p>
 */
class ReceiveDataRequestHandler implements RequestHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveDataRequestHandler.class);

    private final SecurityContext securityContext;
    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final StreamTargetStreamHandlers streamTargetStreamHandlerProvider;
    private final TaskContextFactory taskContextFactory;
    private final MetaService metaService;
    private final RequestAuthenticator requestAuthenticator;
    private final CertificateExtractor certificateExtractor;
    private final ReceiptIdGenerator receiptIdGenerator;

    @Inject
    public ReceiveDataRequestHandler(final SecurityContext securityContext,
                                     final AttributeMapFilterFactory attributeMapFilterFactory,
                                     final StreamTargetStreamHandlers streamTargetStreamHandlerProvider,
                                     final TaskContextFactory taskContextFactory,
                                     final MetaService metaService,
                                     final RequestAuthenticator requestAuthenticator,
                                     final CertificateExtractor certificateExtractor,
                                     final ReceiptIdGenerator receiptIdGenerator) {
        this.securityContext = securityContext;
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.streamTargetStreamHandlerProvider = streamTargetStreamHandlerProvider;
        this.taskContextFactory = taskContextFactory;
        this.metaService = metaService;
        this.requestAuthenticator = requestAuthenticator;
        this.certificateExtractor = certificateExtractor;
        this.receiptIdGenerator = receiptIdGenerator;
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        securityContext.asProcessingUser(() -> {
            final Instant receivedTime = Instant.now();
            final AttributeMapFilter attributeMapFilter = attributeMapFilterFactory.create();
            final UniqueId receiptId = receiptIdGenerator.generateId();
            final AttributeMap attributeMap = AttributeMapUtil.create(
                    request,
                    certificateExtractor,
                    receivedTime,
                    receiptId);

            // Authenticate the request depending on the configured auth methods.
            // Adds sender details to the attributeMap
            final UserIdentity userIdentity = requestAuthenticator.authenticate(request, attributeMap);

            final String feedName;
            if (attributeMapFilter.filter(attributeMap)) {
                debug("Receiving data", attributeMap);

                feedName = NullSafe.trim(attributeMap.get(StandardHeaderArguments.FEED));

                // Get the type name from the header arguments if supplied.
                final String typeName = NullSafe.trim(attributeMap.get(StandardHeaderArguments.TYPE));

                taskContextFactory.context("Receiving Data", taskContext -> {
                    final Consumer<Long> progressHandler =
                            new TaskProgressHandler(taskContext, "Receiving " + feedName + " - ");
                    try (final InputStream inputStream = request.getInputStream()) {
                        streamTargetStreamHandlerProvider.handle(feedName, typeName, attributeMap, handler -> {
                            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                                    attributeMap,
                                    handler,
                                    progressHandler);
                            stroomStreamProcessor.processInputStream(inputStream, "", receivedTime);
                        });
                    } catch (final RuntimeException | IOException e) {
                        LOGGER.debug(e.getMessage(), e);
                        throw StroomStreamException.create(e, attributeMap);
                    }
                }).run();
            } else {
                // Drop the data.
                debug("Dropping data", attributeMap);
            }

            // Set the response status.
            final StroomStatusCode stroomStatusCode = StroomStatusCode.OK;
            response.setStatus(stroomStatusCode.getHttpCode());

            // TODO Should we put receiptId in content or a resp header?
            LOGGER.debug(() -> "Writing receipt id attribute to response: " + receiptId);
//            response.setHeader(StandardHeaderArguments.RECEIPT_ID, receiptId);
            try (final PrintWriter writer = response.getWriter()) {
                if (writer != null) {
                    writer.println(receiptId);
                }
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }

            logSuccess(new StroomStreamStatus(stroomStatusCode, attributeMap));
        });
    }

    private void logSuccess(final StroomStreamStatus stroomStreamStatus) {
        LOGGER.info(() -> "Returning success response " + stroomStreamStatus);
    }

    private void debug(final String message, final AttributeMap attributeMap) {
        if (LOGGER.isDebugEnabled()) {
            final List<String> keys = attributeMap
                    .keySet()
                    .stream()
                    .sorted()
                    .toList();
            final StringBuilder sb = new StringBuilder();
            keys.forEach(key -> {
                sb.append(key);
                sb.append("=");
                sb.append(attributeMap.get(key));
                sb.append(",");
            });
            if (!sb.isEmpty()) {
                sb.setLength(sb.length() - 1);
            }

            LOGGER.debug(message + " (" + sb + ")");
        }
    }
}
