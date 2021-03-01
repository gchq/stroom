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
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.RequestHandler;
import stroom.receive.common.StreamTargetStreamHandlers;
import stroom.receive.common.StroomStreamException;
import stroom.receive.common.StroomStreamProcessor;
import stroom.security.api.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>
 * Handle the incoming requests and stream them to disk checking a few things.
 * </p>
 */
class ReceiveDataRequestHandler implements RequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiveDataRequestHandler.class);

    private final SecurityContext securityContext;
    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final StreamTargetStreamHandlers streamTargetStreamHandlerProvider;

    @Inject
    public ReceiveDataRequestHandler(final SecurityContext securityContext,
                                     final AttributeMapFilterFactory attributeMapFilterFactory,
                                     final StreamTargetStreamHandlers streamTargetStreamHandlerProvider) {
        this.securityContext = securityContext;
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.streamTargetStreamHandlerProvider = streamTargetStreamHandlerProvider;
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response) {
        securityContext.asProcessingUser(() -> {
            final AttributeMapFilter attributeMapFilter = attributeMapFilterFactory.create();

            final AttributeMap attributeMap = AttributeMapUtil.create(request);
            if (attributeMapFilter.filter(attributeMap)) {
                debug("Receiving data", attributeMap);
                try (final InputStream inputStream = request.getInputStream()) {
                    streamTargetStreamHandlerProvider.handle(attributeMap, handler -> {
                        final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                                attributeMap,
                                handler);
                        stroomStreamProcessor.processRequestHeader(request);
                        stroomStreamProcessor.process(inputStream, "");
                    });
                } catch (final RuntimeException | IOException e) {
                    LOGGER.error(e.getMessage(), e);
                    StroomStreamException.create(e);
                }
            } else {
                // Drop the data.
                debug("Dropping data", attributeMap);
            }

            // Set the response status.
            response.setStatus(StroomStatusCode.OK.getHttpCode());
            LOGGER.info("handleRequest response " + StroomStatusCode.OK);
        });
    }

    private void debug(final String message, final AttributeMap attributeMap) {
        if (LOGGER.isDebugEnabled()) {
            final List<String> keys = attributeMap.keySet().stream().sorted().collect(Collectors.toList());
            final StringBuilder sb = new StringBuilder();
            keys.forEach(key -> {
                sb.append(key);
                sb.append("=");
                sb.append(attributeMap.get(key));
                sb.append(",");
            });
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }

            LOGGER.debug(message + " (" + sb.toString() + ")");
        }
    }
}
