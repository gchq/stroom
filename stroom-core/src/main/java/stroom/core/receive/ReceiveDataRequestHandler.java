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

import stroom.feed.api.FeedProperties;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.InputStreamUtils;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.receive.common.ReceiveDataConfig;
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
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * <p>
 * Stroom's handler for handling the incoming requests. Proxy has it's own handler.
 * </p>
 * <p>
 * Performs authentication, passes the attributeMap to the chain of {@link AttributeMapFilter}s
 * then for anything that passes,  streams it to disk.
 * </p>
 */
class ReceiveDataRequestHandler implements RequestHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveDataRequestHandler.class);

    private final SecurityContext securityContext;
    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final StreamTargetStreamHandlers streamTargetStreamHandlerProvider;
    private final TaskContextFactory taskContextFactory;
    private final RequestAuthenticator requestAuthenticator;
    private final CertificateExtractor certificateExtractor;
    private final ReceiptIdGenerator receiptIdGenerator;
    private final ContentAutoCreationService contentAutoCreationService;
    private final FeedProperties feedProperties;
    private final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider;
    private final ReceiveDataConfig receiveDataConfig;

    @Inject
    public ReceiveDataRequestHandler(final SecurityContext securityContext,
                                     final AttributeMapFilterFactory attributeMapFilterFactory,
                                     final StreamTargetStreamHandlers streamTargetStreamHandlerProvider,
                                     final TaskContextFactory taskContextFactory,
                                     final RequestAuthenticator requestAuthenticator,
                                     final CertificateExtractor certificateExtractor,
                                     final ReceiptIdGenerator receiptIdGenerator,
                                     final ContentAutoCreationService contentAutoCreationService,
                                     final FeedProperties feedProperties,
                                     final Provider<AutoContentCreationConfig> autoContentCreationConfigProvider,
                                     final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        this.securityContext = securityContext;
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.streamTargetStreamHandlerProvider = streamTargetStreamHandlerProvider;
        this.taskContextFactory = taskContextFactory;
        this.requestAuthenticator = requestAuthenticator;
        this.certificateExtractor = certificateExtractor;
        this.receiptIdGenerator = receiptIdGenerator;
        this.contentAutoCreationService = contentAutoCreationService;
        this.feedProperties = feedProperties;
        this.autoContentCreationConfigProvider = autoContentCreationConfigProvider;
        this.receiveDataConfig = receiveDataConfigProvider.get();
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        securityContext.asProcessingUser(() -> {
            final Instant receivedTime = Instant.now();
            final UniqueId receiptId = receiptIdGenerator.generateId();
            final AttributeMap attributeMap = AttributeMapUtil.create(
                    request,
                    certificateExtractor,
                    receivedTime,
                    receiptId);

            // Authenticate the request depending on the configured auth methods.
            // Adds sender details to the attributeMap
            final UserIdentity userIdentity = requestAuthenticator.authenticate(request, attributeMap);

            // Get the type name from the header arguments if supplied.
            final String typeName = NullSafe.string(attributeMap.get(StandardHeaderArguments.TYPE));
            final AttributeMapFilter attributeMapFilter = attributeMapFilterFactory.create();

            try {
                if (attributeMapFilter.filter(attributeMap)) {
                    // The filters should ensure we have a supplied or auto-generated feed name
                    // by this point.
                    // The FeedExistenceAttributeMapFilter will have ensured that the FeedDoc has been
                    // auto-created or exists.
                    final String feedName = getFeedName(attributeMap);

                    LOGGER.debug(() -> LogUtil.message(
                            "Receiving data - feed: '{}', type: {}, receiptId: {}, attributeMap: {}",
                            feedName, typeName, receiptId, attributeMapToString(attributeMap)));

                    receiveData(request, feedName, typeName, attributeMap);
                } else {
                    // Drop the data.
                    final String feedName = getFeedName(attributeMap);
                    LOGGER.debug(() -> LogUtil.message(
                            "Dropping data - feed: '{}', type: {}, receiptId: {}, attributeMap: {}",
                            feedName, typeName, receiptId, attributeMapToString(attributeMap)));
                }
            } catch (final StroomStreamException e) {
                final String feedName = getFeedName(attributeMap);
                LOGGER.debug(() -> LogUtil.message(
                        "Rejecting data - feed: '{}', type: {}, receiptId: {}, attributeMap: {}",
                        feedName, typeName, receiptId, attributeMapToString(attributeMap)));
                throw e;
            }

            // Set the response status.
            final StroomStatusCode stroomStatusCode = StroomStatusCode.OK;
            response.setStatus(stroomStatusCode.getHttpCode());
            response.setContentType(ContentType.TEXT_PLAIN.toString());

            // Write the receiptId into the response as plain text
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

    private String getFeedName(final AttributeMap attributeMap) {
        return NullSafe.string(attributeMap.get(StandardHeaderArguments.FEED));
    }

    private void receiveData(final HttpServletRequest request,
                             final String feedName,
                             final String typeName,
                             final AttributeMap attributeMap) {
        taskContextFactory.context("Receiving Data", taskContext -> {

            final Consumer<Long> progressHandler = new TaskProgressHandler(
                    taskContext, "Receiving " + feedName + " - ");

            try (final InputStream boundedInputStream = InputStreamUtils.getBoundedInputStream(request.getInputStream(),
                    receiveDataConfig.getMaxRequestSize())) {
                streamTargetStreamHandlerProvider.handle(feedName, typeName, attributeMap, handler -> {
                    final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                            attributeMap,
                            handler,
                            progressHandler,
                            receiveDataConfig);
                    stroomStreamProcessor.processInputStream(boundedInputStream);
                });
            } catch (final RuntimeException | IOException e) {
                LOGGER.debug(e.getMessage(), e);
                throw StroomStreamException.create(e, attributeMap);
            }
        }).run();
    }

    private void logSuccess(final StroomStreamStatus stroomStreamStatus) {
        LOGGER.info(() -> "Returning success response " + stroomStreamStatus);
    }

    private String attributeMapToString(final AttributeMap attributeMap) {
        // we log feed/type separately
        return NullSafe.map(attributeMap)
                .entrySet()
                .stream()
                .filter(entry -> !Objects.equals(entry.getKey(), StandardHeaderArguments.FEED))
                .filter(entry -> !Objects.equals(entry.getKey(), StandardHeaderArguments.TYPE))
                .sorted(Entry.comparingByKey())
                .map(entry ->
                        String.join("=", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
    }
}
