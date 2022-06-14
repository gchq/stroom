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
import stroom.pipeline.xml.util.XMLWriter;
import stroom.receive.common.StroomStreamException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.Metrics;
import stroom.util.shared.IsServlet;
import stroom.util.shared.Unauthenticated;

import org.apache.http.HttpStatus;
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
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>
 * Servlet that streams files to disk based on meta input arguments.
 * </p>
 */
@Unauthenticated
public class TextEventServlet extends HttpServlet implements IsServlet {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TextEventServlet.class);

    private static final Set<String> PATH_SPECS = Set.of("/event/text");

    private final Provider<EventAppenders> eventAppendersProvider;

    private final ReceiveDataHelper receiveDataHelper;


    @Inject
    public TextEventServlet(final Provider<EventAppenders> eventAppendersProvider,
                            final ReceiveDataHelper receiveDataHelper) {
        this.eventAppendersProvider = eventAppendersProvider;
        this.receiveDataHelper = receiveDataHelper;
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
            final String idProperty = receiveDataHelper.process(request, attributeMap -> {
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
            });

            try (final PrintWriter writer = response.getWriter()) {
                writer.println(idProperty);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
            response.setStatus(HttpStatus.SC_OK);

        } catch (final StroomStreamException e) {
            e.sendErrorResponse(response);

        } catch (final RuntimeException e) {
            final StroomStreamException stroomStreamException =
                    StroomStreamException.create(e,
                            AttributeMapUtil.create(request));
            stroomStreamException.sendErrorResponse(response);
        }
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
