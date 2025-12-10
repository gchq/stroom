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

package stroom.util.logging;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.message.MessageUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This custom logging feature class extends built-in Jersey logging by providing control over when response body
 * contents are logged. The built-in behaviour when verbosity
 * {@link org.glassfish.jersey.logging.LoggingFeature.Verbosity#PAYLOAD_ANY} is specified, is to log response body
 * contents, regardless of MIME type. Logging is sent to the logger for {@link LoggingFeature}.
 * <p>
 * For endpoints returning sensitive or binary data, it doesn't make sense to log this information, so in such cases,
 * the API method should be annotated with `@NoResponseBodyLogging`.
 */
public class DefaultLoggingFilter
        extends LoggingFeature
        implements ContainerRequestFilter, ContainerResponseFilter, ClientRequestFilter,
        ClientResponseFilter, WriterInterceptor {

    public static final String ENTITY_LOGGER_PROPERTY = LoggingFeature.class.getName();
    private static final Logger LOGGER = Logger.getLogger(ENTITY_LOGGER_PROPERTY);
    public static final int DEFAULT_MAX_ENTITY_SIZE = LoggingFeature.DEFAULT_MAX_ENTITY_SIZE;
    private final int maxEntitySize;

    public DefaultLoggingFilter(final Logger logger,
                                final Level level,
                                final Verbosity verbosity,
                                final int maxEntitySize) {
        super(logger, level, verbosity, maxEntitySize);
        this.maxEntitySize = Math.max(0, maxEntitySize);
    }

    /**
     * @return A {@link DefaultLoggingFilter} configured to log requests/responses with their payloads,
     * using the logger for {@link LoggingFeature} with a log level of {@link Level#INFO} and a maximum
     * entity size of {@link DefaultLoggingFilter#DEFAULT_MAX_ENTITY_SIZE}.
     */
    public static DefaultLoggingFilter createWithDefaults() {
        return new DefaultLoggingFilter(
                LOGGER,
                Level.INFO,
                Verbosity.PAYLOAD_ANY,
                DEFAULT_MAX_ENTITY_SIZE);
    }

    @Override
    public boolean configure(final FeatureContext context) {
        context.register(this);
        return true;
    }

    /**
     * Server request
     */
    @Override
    public void filter(final ContainerRequestContext requestContext) throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s %s\n", requestContext.getMethod(),
                requestContext.getUriInfo().getAbsolutePath()));
        printHeaders(sb, requestContext.getHeaders(), "> ");
        if (requestContext.hasEntity()) {
            final InputStream loggingStream = printEntity(sb, requestContext.getEntityStream(),
                    MessageUtils.getCharset(requestContext.getMediaType()));
            requestContext.setEntityStream(loggingStream);
        }

        LOGGER.info(sb.toString());
    }

    /**
     * Server response
     */
    @Override
    public void filter(final ContainerRequestContext requestContext,
                       final ContainerResponseContext responseContext)
            throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d\n", responseContext.getStatus()));
        printHeaders(sb, responseContext.getStringHeaders(), "< ");

        // If the API method has the `@NoResponseBodyLogging` annotation, do not log the response body
        final boolean logResponseBody = Arrays
                .stream(responseContext.getEntityAnnotations())
                .noneMatch(annotation -> annotation instanceof NoResponseBodyLogging);
        if (responseContext.hasEntity() && logResponseBody) {
            final OutputStream outputStream = new LoggingStream(sb, responseContext.getEntityStream(), maxEntitySize);
            responseContext.setEntityStream(outputStream);
            requestContext.setProperty(ENTITY_LOGGER_PROPERTY, outputStream);
        } else {
            LOGGER.info(sb.toString());
        }
    }

    /**
     * Client request
     */
    @Override
    public void filter(final ClientRequestContext requestContext) throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s %s\n", requestContext.getMethod(), requestContext.getUri().getPath()));
        printHeaders(sb, requestContext.getStringHeaders(), "> ");
        if (requestContext.hasEntity()) {
            final OutputStream outputStream = new LoggingStream(
                    sb,
                    requestContext.getEntityStream(),
                    maxEntitySize);
            requestContext.setEntityStream(outputStream);
            requestContext.setProperty(ENTITY_LOGGER_PROPERTY, outputStream);
        } else {
            LOGGER.info(sb.toString());
        }
    }

    /**
     * Client response received
     */
    @Override
    public void filter(final ClientRequestContext requestContext, final ClientResponseContext responseContext)
            throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s %s\n", requestContext.getMethod(), requestContext.getUri().getPath()));
        sb.append(String.format("< %d\n", responseContext.getStatus()));
        printHeaders(sb, responseContext.getHeaders(), "< ");
        if (responseContext.hasEntity()) {
            final InputStream loggingStream = printEntity(sb, responseContext.getEntityStream(),
                    MessageUtils.getCharset(responseContext.getMediaType()));
            responseContext.setEntityStream(loggingStream);
        } else {
            LOGGER.info(sb.toString());
        }
    }

    @Override
    public void aroundWriteTo(final WriterInterceptorContext writerInterceptorContext) throws IOException,
            WebApplicationException {
        final LoggingStream stream = (LoggingStream) writerInterceptorContext.getProperty(ENTITY_LOGGER_PROPERTY);
        writerInterceptorContext.proceed();
        if (stream != null) {
            LOGGER.info(stream.getStringBuilder(MessageUtils.getCharset(writerInterceptorContext.getMediaType()))
                    .toString());
        }
    }

    private void printHeaders(final StringBuilder sb, final MultivaluedMap<String, String> headerMap,
                              final String linePrefix) {
        for (final Entry<String, List<String>> header : headerMap.entrySet()) {
            sb.append(String.format("%s%s: ", linePrefix, header.getKey()));
            if (header.getKey().equals("Authorization")) {
                sb.append("<redacted>");
            } else {
                sb.append(String.join(",", header.getValue()));
            }
            sb.append("\n");
        }
    }

    /**
     * Write the contents of a request/response body, up to a certain length
     */
    private InputStream printEntity(final StringBuilder sb, InputStream inputStream, final Charset charset)
            throws IOException {
        if (!inputStream.markSupported()) {
            inputStream = new BufferedInputStream(inputStream);
        }
        inputStream.mark(maxEntitySize + 1);
        final byte[] entity = new byte[maxEntitySize + 1];
        final int entitySize = inputStream.read(entity);
        sb.append(new String(entity, 0, Math.min(entitySize, maxEntitySize), charset));
        if (entitySize > maxEntitySize) {
            sb.append("...[truncated]");
        }
        sb.append('\n');
        inputStream.reset();
        return inputStream;
    }

    private static class LoggingStream extends FilterOutputStream {

        private final StringBuilder stringBuilder;
        private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        private final int maxEntitySize;

        LoggingStream(final StringBuilder stringBuilder, final OutputStream innerStream, final int maxEntitySize) {
            super(innerStream);
            this.stringBuilder = stringBuilder;
            this.maxEntitySize = maxEntitySize;
        }

        public StringBuilder getStringBuilder(final Charset charset) {
            final byte[] entity = byteArrayOutputStream.toByteArray();

            stringBuilder.append(new String(entity, 0, Math.min(entity.length, maxEntitySize), charset));
            if (entity.length > maxEntitySize) {
                stringBuilder.append("...[truncated]");
            }
            stringBuilder.append('\n');

            return stringBuilder;
        }

        public void write(final int i) throws IOException {
            if (byteArrayOutputStream.size() <= maxEntitySize) {
                byteArrayOutputStream.write(i);
            }
            out.write(i);
        }
    }
}
