package stroom.rs.logging.impl;

import stroom.rs.logging.api.StroomServerLoggingFilter;


import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.glassfish.jersey.message.MessageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;


public class StroomServerLoggingFilterImpl implements StroomServerLoggingFilter {
    static final Logger LOGGER = LoggerFactory.getLogger(StroomServerLoggingFilterImpl.class);

    private static final String REQUEST_LOG_INFO_PROPERTY = "stroom.rs.logging.request";

    private final RequestEventLog requestEventLog;
    private final ObjectMapper objectMapper;
    private final Provider<ResourcePathMap> resourcePathMapProvider;

    @Inject
    StroomServerLoggingFilterImpl(RequestEventLog requestEventLog, Provider<ResourcePathMap> resourcePathMapProvider) {
        this.requestEventLog = requestEventLog;
        this.resourcePathMapProvider = resourcePathMapProvider;
        this.objectMapper = createObjectMapper();
    }

    private static class RequestInfo {
        private final LoggingInfo loggingInfo;
        private final Object requestEntity;
        public RequestInfo(final LoggingInfo loggingInfo, final Object requestEntity){
            this.loggingInfo = loggingInfo;
            this.requestEntity = requestEntity;
        }

        public LoggingInfo getLoggingInfo() {
            return loggingInfo;
        }

        public Object getRequestEntity() {
            return requestEntity;
        }
    }

    @Override
    public void aroundWriteTo(final WriterInterceptorContext writerInterceptorContext)
            throws IOException, WebApplicationException {
        writerInterceptorContext.proceed();
        final Object entity = writerInterceptorContext.getProperty(REQUEST_LOG_INFO_PROPERTY);

        if (entity != null) {
            RequestInfo requestInfo = (RequestInfo) entity;
            requestEventLog.log (requestInfo.getLoggingInfo(), requestInfo.getRequestEntity(),
                    writerInterceptorContext.getEntity());
        }
    }

    @Override
    public void filter(final ContainerRequestContext context) throws IOException {
        LoggingInfo loggingInfo = resourcePathMapProvider.get().lookup(context);

        if (context.hasEntity()) {
            final LoggingInputStream stream = new LoggingInputStream(loggingInfo, context.getEntityStream(),
                    objectMapper, MessageUtils.getCharset(context.getMediaType()));
            context.setEntityStream(stream);

            context.setProperty(REQUEST_LOG_INFO_PROPERTY, new RequestInfo(stream.getLoggingInfo(), stream.getRequestEntity()));
        }

    }

    private static ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }

}