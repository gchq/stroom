package stroom.rs.logging.impl;

import stroom.rs.logging.api.StroomServerLoggingFilter;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StroomServerLoggingFilterImpl implements StroomServerLoggingFilter {
    static final Logger LOGGER = LoggerFactory.getLogger(StroomServerLoggingFilterImpl.class);

    private static final String REQUEST_LOG_INFO_PROPERTY = "stroom.rs.logging.request";

    private Map<String, List<LoggingInfo>> loggingInfoMap;

    private final Map<RestResourcesBinder.ResourceType, Provider<RestResource>> providerMap;
    private final RequestEventLog requestEventLog;
    private final ObjectMapper objectMapper;

    @Inject
    StroomServerLoggingFilterImpl(final Map<RestResourcesBinder.ResourceType,
            Provider<RestResource>> providerMap, RequestEventLog requestEventLog) {
        this.providerMap = providerMap;
        this.requestEventLog = requestEventLog;
        this.objectMapper = createMapper(false);
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


    LoggingInfo findLoggingInfo (ContainerRequestContext context){
        return findLoggingInfo (context.getMethod(),
                ResourcePaths.buildAuthenticatedApiPath(context.getUriInfo().getPath()));
    }

    LoggingInfo findLoggingInfo (String method, String path){
        String key = createCallKey(method, path);
        List<LoggingInfo> possibleMatches = getLoggingImfoMap().get(key);

        if (possibleMatches == null){
            return null;
        }

        if (possibleMatches.size() == 1){
            return possibleMatches.get(0);
        }

        //todo find the exact match using regex
        LOGGER.warn("Need to dedupe for method " + method  + " at " + path);
        return null;
    }

    synchronized Map<String, List<LoggingInfo>> getLoggingImfoMap(){
        if (loggingInfoMap == null){
            loggingInfoMap =  providerMap.keySet().stream().flatMap(this::findCallsForResource).
                    collect(Collectors.groupingBy(LoggingInfo::createCallKey));

        }

        return loggingInfoMap;
    }

    private Stream<LoggingInfo> findCallsForResource (RestResourcesBinder.ResourceType resourceType){
        Class<?> resourceClass = resourceType.getResourceClass();

        Optional<String> resourcePath = AnnotationUtil.getResourcePath(resourceClass);
        if (resourcePath.isEmpty()){
            LOGGER.warn("Unable to determine HTTP method for class " + resourceClass.getName());
            return Stream.empty();
        }

        return Arrays.stream(resourceClass.getMethods()).sequential().flatMap(m -> {
            Optional<String> methodPath = AnnotationUtil.getMethodPath (m);
            if (methodPath.isEmpty()) {
                return Stream.empty();
            }

            Optional<String> httpMethod = AnnotationUtil.getHttpMethod (m, resourceClass);
            if (httpMethod.isEmpty()) {
                LOGGER.warn("Unable to determine HTTP method for api call " + methodPath.get());
                return Stream.empty();
            }

            return Stream.of(new LoggingInfo(
                    httpMethod.get(),
                    stripVariableFromPath(resourcePath.get() + methodPath.get()),
                    m, resourceClass));
        });
    }

    private static String stripVariableFromPath (String originalPath){
        if (originalPath.contains("{")){
            originalPath = originalPath.substring(0, originalPath.indexOf('{'));
        }
        return originalPath;
    }

    static String createCallKey (String method, String originalPath){
        originalPath = stripVariableFromPath(originalPath);
        return method + ":" + originalPath;
    }

    @Override
    public void filter(final ContainerRequestContext context) throws IOException {
        LoggingInfo loggingInfo = findLoggingInfo(context);

        if (context.hasEntity()) {
            final LoggingInputStream stream = new LoggingInputStream(loggingInfo, context.getEntityStream(),
                    objectMapper, MessageUtils.getCharset(context.getMediaType()));
            context.setEntityStream(stream);

            context.setProperty(REQUEST_LOG_INFO_PROPERTY, new RequestInfo(stream.getLoggingInfo(), stream.getRequestEntity()));
        }

    }

    private static ObjectMapper createMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }

}