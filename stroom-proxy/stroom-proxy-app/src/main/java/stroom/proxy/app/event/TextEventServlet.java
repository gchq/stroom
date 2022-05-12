/*
 * Copyright 2016 Crown Copyright
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
import stroom.meta.api.StandardHeaderArguments;
import stroom.pipeline.xml.util.XMLWriter;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.ReceiveDataConfig;
import stroom.proxy.app.handler.AttributeMapFilterFactory;
import stroom.proxy.app.handler.ProxyId;
import stroom.proxy.repo.CSVFormatter;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapValidator;
import stroom.receive.common.StroomStreamException;
import stroom.security.api.RequestAuthenticator;
import stroom.security.api.UserIdentity;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;
import stroom.util.shared.IsServlet;
import stroom.util.shared.Unauthenticated;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;

/**
 * <p>
 * Servlet that streams files to disk based on meta input arguments.
 * </p>
 */
@Unauthenticated
public class TextEventServlet extends HttpServlet implements IsServlet {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TextEventServlet.class);

    private static final Logger RECEIVE_LOG = LoggerFactory.getLogger("receive");

    private final AttributeMapFilter attributeMapFilter;
    private final LogStream logStream;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final RequestAuthenticator requestAuthenticator;
    private final ProxyId proxyId;

    private static final Set<String> PATH_SPECS = Set.of("/event/text");

    private final Provider<EventAppenders> eventAppendersProvider;


    @Inject
    public TextEventServlet(final Provider<EventAppenders> eventAppendersProvider,
                            final AttributeMapFilterFactory attributeMapFilterFactory,
                            final LogStream logStream,
                            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                            final RequestAuthenticator requestAuthenticator,
                            final ProxyId proxyId) {
        this.eventAppendersProvider = eventAppendersProvider;
        this.logStream = logStream;
        this.attributeMapFilter = attributeMapFilterFactory.create();
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.requestAuthenticator = requestAuthenticator;
        this.proxyId = proxyId;
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
            stream(request, response);
        } catch (final RuntimeException e) {
            StroomStreamException.sendErrorResponse(request, response, e);
        }
    }

    private void stream(final HttpServletRequest request, final HttpServletResponse response) {
        Metrics.measure("ProxyRequestHandler - stream", () -> {
            final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();

            final long startTimeMs = System.currentTimeMillis();
            final AttributeMap attributeMap = AttributeMapUtil.create(request);
            final String authorisationHeader = attributeMap.get(HttpHeaders.AUTHORIZATION);

            // Create a new proxy id for the stream and report it back to the sender,
            final String attributeKey = proxyId.getId();
            final String attributeName = UUID.randomUUID().toString();
            attributeMap.put(attributeKey, attributeName);
            LOGGER.debug(() -> "Adding proxy id attribute: " + attributeKey + ": " + attributeName);
            try (final PrintWriter writer = response.getWriter()) {
                writer.println(attributeKey + ": " + attributeName);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }

            // If token authentication is required but no token is supplied then error.
            if (receiveDataConfig.isRequireTokenAuthentication() &&
                    (authorisationHeader == null || authorisationHeader.isBlank())) {
                throw new StroomStreamException(StroomStatusCode.CLIENT_TOKEN_REQUIRED, attributeMap);
            }

            // Authenticate the request token if there is one.
            final Optional<UserIdentity> optionalUserIdentity = requestAuthenticator.authenticate(request);

            // Add the user identified in the token (if present) to the attribute map.
            optionalUserIdentity
                    .map(UserIdentity::getId)
                    .ifPresent(id -> attributeMap.put("UploadUser", id));

            if (receiveDataConfig.isRequireTokenAuthentication() && optionalUserIdentity.isEmpty()) {
                // If token authentication is required, but we could not verify the token then error.
                throw new StroomStreamException(StroomStatusCode.CLIENT_TOKEN_NOT_AUTHORISED, attributeMap);

            } else {
                final int rc = Metrics.measure("ProxyRequestHandler - handle1", () -> {
                    int returnCode = HttpServletResponse.SC_OK;

                    // Remove authorization header from attributes.
                    attributeMap.remove(HttpHeaders.AUTHORIZATION);

                    try {
                        // Validate the supplied attributes.
                        AttributeMapValidator.validate(
                                attributeMap,
                                receiveDataConfig::getMetaTypes);

                        final String feedName = attributeMap.get(StandardHeaderArguments.FEED);
                        if (feedName == null || feedName.trim().isEmpty()) {
                            throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
                        }
                        final String typeName = attributeMap.get(StandardHeaderArguments.TYPE);

                        try (final ByteCountInputStream inputStream =
                                new ByteCountInputStream(request.getInputStream())) {
                            // Test to see if we are going to accept this stream or drop the data.
                            if (attributeMapFilter.filter(attributeMap)) {
                                // Consume the data
                                Metrics.measure("ProxyRequestHandler - handle", () -> {
                                    final StringBuilder sb = new StringBuilder();

                                    try {
                                        final Reader reader =
                                                new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8);
                                        final char[] buffer = new char[4096];
                                        int len = 0;
                                        final long maxChars = 100_000;
                                        while ((len = reader.read(buffer)) != -1) {
                                            sb.append(buffer, 0, len);
                                            if (sb.length() > maxChars) {
                                                throw new RuntimeException("Event size exceeded maximum " +
                                                        maxChars +
                                                        " characters");
                                            }
                                        }
                                    } catch (final IOException e) {
                                        LOGGER.error(e::getMessage, e);
                                        throw new UncheckedIOException(e);
                                    }

                                    writeEvent(attributeMap, sb.toString());
                                });

                                final long duration = System.currentTimeMillis() - startTimeMs;
                                logStream.log(
                                        RECEIVE_LOG,
                                        attributeMap,
                                        "RECEIVE",
                                        request.getRequestURI(),
                                        returnCode,
                                        inputStream.getCount(),
                                        duration);

                            } else {
                                // Just read the stream in and ignore it
                                final byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];
                                while (inputStream.read(buffer) >= 0) {
                                    // Ignore data.
                                    if (LOGGER.isTraceEnabled()) {
                                        LOGGER.trace(new String(buffer));
                                    }
                                }
                                returnCode = HttpServletResponse.SC_OK;
                                LOGGER.warn("\"Dropped event\",{}", CSVFormatter.format(attributeMap));

                                final long duration = System.currentTimeMillis() - startTimeMs;
                                logStream.log(
                                        RECEIVE_LOG,
                                        attributeMap,
                                        "DROP",
                                        request.getRequestURI(),
                                        returnCode,
                                        inputStream.getCount(),
                                        duration);
                            }
                        }
                    } catch (final StroomStreamException e) {
                        StroomStreamException.sendErrorResponse(request, response, e);
                        returnCode = e.getStroomStatusCode().getCode();

                        LOGGER.warn("\"handleException()\",{},\"{}\"",
                                CSVFormatter.format(attributeMap),
                                CSVFormatter.escape(e.getMessage()));

                        final long duration = System.currentTimeMillis() - startTimeMs;
                        if (StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVED_DATA.equals(e.getStroomStatusCode())) {
                            logStream.log(
                                    RECEIVE_LOG,
                                    attributeMap,
                                    "REJECT",
                                    request.getRequestURI(),
                                    returnCode,
                                    -1,
                                    duration);
                        } else {
                            logStream.log(
                                    RECEIVE_LOG,
                                    attributeMap,
                                    "ERROR",
                                    request.getRequestURI(),
                                    returnCode,
                                    -1,
                                    duration);
                        }

                    } catch (final IOException | RuntimeException e) {
                        RuntimeException unwrappedException = StroomStreamException.sendErrorResponse(request,
                                response,
                                e);
                        returnCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

                        LOGGER.error("\"handleException()\",{}", CSVFormatter.format(attributeMap), unwrappedException);
                        final long duration = System.currentTimeMillis() - startTimeMs;
                        logStream.log(
                                RECEIVE_LOG,
                                attributeMap,
                                "ERROR",
                                request.getRequestURI(),
                                returnCode,
                                -1,
                                duration);
                    }

                    return returnCode;
                });

                response.setStatus(rc);
            }
        });
    }

    private void writeEvent(final AttributeMap attributeMap,
                            final String data) {
        eventAppendersProvider.get().consume(attributeMap, outputStream -> {
            try {
                final Writer writer = new OutputStreamWriter(outputStream);
                final XMLWriter xml = new XMLWriter(writer);
                xml.setOutputXMLDecl(false);
                xml.startDocument();
                xml.startElement("", "event", "event", new AttributesImpl());

                xml.startElement("", "head", "head", new AttributesImpl());
                attributeMap.forEach((k, v) -> {
                    try {
                        final AttributesImpl attributes = new AttributesImpl();
                        attributes.addAttribute("", "name", "name", "string", k);
                        attributes.addAttribute("", "value", "value", "string", v);
                        xml.startElement("", "meta", "meta", attributes);
                        xml.endElement("", "meta", "meta");
                    } catch (final SAXException e) {
                        LOGGER.error(e::getMessage, e);
                        throw new RuntimeException(e.getMessage(), e);
                    }
                });
                xml.endElement("", "head", "head");

                xml.startElement("", "body", "body", new AttributesImpl());

                final char[] chars = data.toCharArray();
                xml.characters(chars, 0, chars.length);

                xml.endElement("", "body", "body");
                xml.endElement("", "event", "event");
                xml.endDocument();
                writer.flush();
            } catch (final SAXException | IOException e) {
                LOGGER.error(e::getMessage, e);
                throw new RuntimeException(e.getMessage(), e);
            }
        });
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
            String header = headers.nextElement();
            trace.append("request.getHeader('");
            trace.append(header);
            trace.append("')='");
            trace.append(request.getHeader(header));
            trace.append("'\n");
        }

        final Enumeration<String> attributes = request.getAttributeNames();
        while (attributes.hasMoreElements()) {
            String attr = attributes.nextElement();
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
