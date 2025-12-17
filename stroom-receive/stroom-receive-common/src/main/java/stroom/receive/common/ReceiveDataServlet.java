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

package stroom.receive.common;

import stroom.meta.api.AttributeMapUtil;
import stroom.util.cert.CertificateExtractor;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.Unauthenticated;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Set;

/**
 * <p>
 * The /datafeed servlet.
 * </p>
 * <p>
 * Servlet that streams files to disk based on meta input arguments.
 * </p>
 */
@Unauthenticated
public class ReceiveDataServlet extends HttpServlet implements IsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiveDataServlet.class);

    public static final String DATA_FEED_PATH_PART = "/datafeed";

    // AWS ELB/ALB can't map paths so rather than add nginx or similar into the mix to map paths,
    // have a pair of aliases without 'noauth' in them.
    // This is somewhat inconsistent with our other servlets so far from ideal.
    private static final Set<String> PATH_SPECS = Set.of(
            DATA_FEED_PATH_PART,
            DATA_FEED_PATH_PART + "/*",
            ResourcePaths.addLegacyUnauthenticatedServletPrefix(DATA_FEED_PATH_PART),
            ResourcePaths.addLegacyUnauthenticatedServletPrefix(DATA_FEED_PATH_PART, "/*"),
            ResourcePaths.addLegacyAuthenticatedServletPrefix(DATA_FEED_PATH_PART),
            ResourcePaths.addLegacyAuthenticatedServletPrefix(DATA_FEED_PATH_PART, "/*"));

    private final Provider<RequestHandler> requestHandlerProvider;
    private final CertificateExtractor certificateExtractor;

    @Inject
    ReceiveDataServlet(final Provider<RequestHandler> requestHandlerProvider,
                       final CertificateExtractor certificateExtractor) {
        this.requestHandlerProvider = requestHandlerProvider;
        this.certificateExtractor = certificateExtractor;
    }

    /**
     * @see HttpServlet#doPost(HttpServletRequest, HttpServletResponse)
     */
    @Override
    public void doPost(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        handleRequest(request, response);
    }

    /**
     * @see HttpServlet#doPut(HttpServletRequest, HttpServletResponse)
     */
    @Override
    protected void doPut(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        handleRequest(request, response);
    }

    /**
     * Do handle the request.
     */
    private void handleRequest(final HttpServletRequest request, final HttpServletResponse response) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(getRequestTrace(request));
        }

        try {
            final RequestHandler requestHandler = requestHandlerProvider.get();
            requestHandler.handle(request, response);

        } catch (final StroomStreamException e) {
            e.sendErrorResponse(response);

        } catch (final RuntimeException e) {
            final StroomStreamException stroomStreamException =
                    StroomStreamException.create(e,
                            AttributeMapUtil.create(
                                    request,
                                    certificateExtractor,
                                    Instant.now(),
                                    null));
            stroomStreamException.sendErrorResponse(response);
        }
    }

    /**
     * Utility to log out some trace info.
     */
    private String getRequestTrace(final HttpServletRequest request) {
        final StringBuilder trace = new StringBuilder();
        trace.append("request.getAuthType()=");
        trace.append(request.getAuthType());
        trace.append("\n");
        trace.append("request.getProtocol()=");
        trace.append(request.getProtocol());
        trace.append("\n");
        trace.append("request.getScheme()=");
        trace.append(request.getScheme());
        trace.append("\n");
        trace.append("request.getQueryString()=");
        trace.append(request.getQueryString());
        trace.append("\n");

        final Enumeration<String> headers = request.getHeaderNames();
        while (headers.hasMoreElements()) {
            final String header = headers.nextElement();
            trace.append("request.getHeader('");
            trace.append(header);
            trace.append("')='");
            trace.append(request.getHeader(header));
            trace.append("'\n");
        }

        final Enumeration<String> attributes = request.getAttributeNames();
        while (attributes.hasMoreElements()) {
            final String attr = attributes.nextElement();
            trace.append("request.getAttribute('");
            trace.append(attr);
            trace.append("')='");
            trace.append(request.getAttribute(attr));
            trace.append("'\n");
        }

        trace.append("request.getRequestURI()=");
        trace.append(request.getRequestURI());

        return trace.toString();
    }

    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
