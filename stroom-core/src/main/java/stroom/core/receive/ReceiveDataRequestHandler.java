/*
 * Copyright 2024 Crown Copyright
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

package stroom.core.receive;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaService;
import stroom.meta.api.StandardHeaderArguments;
import stroom.pipeline.writer.CSVFormatter;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapValidator;
import stroom.receive.common.RequestAuthenticator;
import stroom.receive.common.RequestHandler;
import stroom.receive.common.StreamTargetStreamHandlers;
import stroom.receive.common.StroomStreamException;
import stroom.receive.common.StroomStreamProcessor;
import stroom.receive.common.StroomStreamStatus;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskProgressHandler;
import stroom.util.NullSafe;
import stroom.util.cert.CertificateExtractor;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
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

    @Inject
    public ReceiveDataRequestHandler(final SecurityContext securityContext,
                                     final AttributeMapFilterFactory attributeMapFilterFactory,
                                     final StreamTargetStreamHandlers streamTargetStreamHandlerProvider,
                                     final TaskContextFactory taskContextFactory,
                                     final MetaService metaService,
                                     final RequestAuthenticator requestAuthenticator,
                                     final CertificateExtractor certificateExtractor) {
        this.securityContext = securityContext;
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.streamTargetStreamHandlerProvider = streamTargetStreamHandlerProvider;
        this.taskContextFactory = taskContextFactory;
        this.metaService = metaService;
        this.requestAuthenticator = requestAuthenticator;
        this.certificateExtractor = certificateExtractor;
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        securityContext.asProcessingUser(() -> {
            final Instant receivedTime = Instant.now();
            final AttributeMapFilter attributeMapFilter = attributeMapFilterFactory.create();
            final AttributeMap attributeMap = AttributeMapUtil.create(request, certificateExtractor);

            // Authenticate the request using token or cert depending on configuration
            // Adds sender details to the attributeMap
            requestAuthenticator.authenticate(request, attributeMap);

            // Validate the supplied attributes.
            AttributeMapValidator.validate(attributeMap, metaService::getTypes);

            final String feedName;
            if (attributeMapFilter.filter(attributeMap)) {
                debug("Receiving data", attributeMap);

                feedName = NullSafe.trim(attributeMap.get(StandardHeaderArguments.FEED));

                // Get the type name from the header arguments if supplied.
                String typeName = NullSafe.trim(attributeMap.get(StandardHeaderArguments.TYPE));

                taskContextFactory.context("Receiving Data", taskContext -> {
                    final Consumer<Long> progressHandler =
                            new TaskProgressHandler(taskContext, "Receiving " + feedName + " - ");
                    try (final InputStream inputStream = request.getInputStream()) {
                        streamTargetStreamHandlerProvider.handle(feedName, typeName, attributeMap, handler -> {
                            final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                                    attributeMap,
                                    handler,
                                    progressHandler);
                            stroomStreamProcessor.processRequestHeader(request, receivedTime);
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
            logSuccess(new StroomStreamStatus(stroomStatusCode, attributeMap));
        });
    }

    private void logSuccess(final StroomStreamStatus stroomStreamStatus) {
        LOGGER.info(() -> "Returning success response " + stroomStreamStatus);
    }

    private void debug(final String message, final AttributeMap attributeMap) {
        if (LOGGER.isDebugEnabled()) {
            final String attrs = CSVFormatter.format(attributeMap);
            LOGGER.debug(message + " (" + attrs + ")");
        }
    }
}
