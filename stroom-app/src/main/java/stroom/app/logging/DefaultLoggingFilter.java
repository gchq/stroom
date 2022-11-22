package stroom.app.logging;

import stroom.util.logging.NoResponseBodyLogging;

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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

/**
 * This custom logging feature class extends built-in Jersey logging by providing control over when response body
 * contents are logged. The built-in behaviour when verbosity `PAYLOAD_ALL` is specified, is to log response body
 * contents, regardless of MIME type.
 * <p>
 * For endpoints returning sensitive or binary data, it doesn't make sense to log this information, so in such cases,
 * the API method should be annotated with `@NoResponseBodyLogging`.
 */
public class DefaultLoggingFilter extends LoggingFeature implements ContainerRequestFilter, ContainerResponseFilter,
        ClientRequestFilter, ClientResponseFilter, WriterInterceptor {

    public static final String ENTITY_LOGGER_PROPERTY = LoggingFeature.class.getName();
    private static final int MAX_ENTITY_SIZE = 8 * 1024;
    private static final Logger logger = Logger.getLogger(ENTITY_LOGGER_PROPERTY);

    public DefaultLoggingFilter(Logger logger, Level level, Verbosity verbosity, Integer maxEntitySize) {
        super(logger, level, verbosity, maxEntitySize);
    }

    @Override
    public boolean configure(FeatureContext context) {
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

        logger.info(sb.toString());
    }

    /**
     * Server response
     */
    @Override
    public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext responseContext)
            throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d\n", responseContext.getStatus()));
        printHeaders(sb, responseContext.getStringHeaders(), "< ");

        // If the API method has the `@NoResponseBodyLogging` annotation, do not log the response body
        final boolean logResponseBody = Arrays
                .stream(responseContext.getEntityAnnotations())
                .noneMatch(annotation -> annotation instanceof NoResponseBodyLogging);
        if (responseContext.hasEntity() && logResponseBody) {
            final OutputStream outputStream = new LoggingStream(sb, responseContext.getEntityStream());
            responseContext.setEntityStream(outputStream);
            requestContext.setProperty(ENTITY_LOGGER_PROPERTY, outputStream);
        } else {
            logger.info(sb.toString());
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
            final OutputStream outputStream = new LoggingStream(sb, requestContext.getEntityStream());
            requestContext.setEntityStream(outputStream);
            requestContext.setProperty(ENTITY_LOGGER_PROPERTY, outputStream);
        } else {
            logger.info(sb.toString());
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
            logger.info(sb.toString());
        }
    }

    @Override
    public void aroundWriteTo(final WriterInterceptorContext writerInterceptorContext) throws IOException,
            WebApplicationException {
        final LoggingStream stream = (LoggingStream) writerInterceptorContext.getProperty(ENTITY_LOGGER_PROPERTY);
        writerInterceptorContext.proceed();
        if (stream != null) {
            logger.info(stream.getStringBuilder(MessageUtils.getCharset(writerInterceptorContext.getMediaType()))
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
        inputStream.mark(MAX_ENTITY_SIZE + 1);
        final byte[] entity = new byte[MAX_ENTITY_SIZE + 1];
        final int entitySize = inputStream.read(entity);
        sb.append(new String(entity, 0, Math.min(entitySize, MAX_ENTITY_SIZE), charset));
        if (entitySize > MAX_ENTITY_SIZE) {
            sb.append("...[truncated]");
        }
        sb.append('\n');
        inputStream.reset();
        return inputStream;
    }

    private static class LoggingStream extends FilterOutputStream {

        private final StringBuilder stringBuilder;
        private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        LoggingStream(final StringBuilder stringBuilder, final OutputStream innerStream) {
            super(innerStream);
            this.stringBuilder = stringBuilder;
        }

        public StringBuilder getStringBuilder(Charset charset) {
            final byte[] entity = byteArrayOutputStream.toByteArray();

            stringBuilder.append(new String(entity, 0, Math.min(entity.length, MAX_ENTITY_SIZE), charset));
            if (entity.length > MAX_ENTITY_SIZE) {
                stringBuilder.append("...[truncated]");
            }
            stringBuilder.append('\n');

            return stringBuilder;
        }

        public void write(final int i) throws IOException {
            if (byteArrayOutputStream.size() <= MAX_ENTITY_SIZE) {
                byteArrayOutputStream.write(i);
            }
            out.write(i);
        }
    }
}
