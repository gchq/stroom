package stroom.rs.logging.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.rs.logging.api.StroomServerLoggingFilter;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.StroomLoggingOperation;
import stroom.util.shared.StroomLoggingOperationType;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.glassfish.jersey.message.MessageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StroomServerLoggingFilterImpl implements StroomServerLoggingFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomServerLoggingFilterImpl.class);

    private static final String LOGGING_ID_PROPERTY = "StroomLogging.id";
//    private static final String RESPONSE_ENTITY_LOGGER_PROPERTY = "stroom.stream.output";
//    private static final String REQUEST_ENTITY_LOGGER_PROPERTY = "stroom.rs.logging.stream";
    private static final String REQUEST_LOG_INFO_PROPERTY = "stroom.rs.logging.request";
    private final int maxEntitySize = 10000000;
    private final AtomicLong _id = new AtomicLong(0);
    private Map<String, List<LoggingInfo>> loggingInfoMap;

    private final Map<RestResourcesBinder.ResourceType, Provider<RestResource>> providerMap;
    private final DocumentEventLog documentEventLog;
    private final ObjectMapper objectMapper;

    @Inject
    StroomServerLoggingFilterImpl(final Map<RestResourcesBinder.ResourceType,
            Provider<RestResource>> providerMap, DocumentEventLog documentEventLog) {
        this.providerMap = providerMap;
        this.documentEventLog = documentEventLog;
        this.objectMapper = createMapper(false);
    }
//    public String getAuthenticatedUser (){
//        final UserIdentity userIdentity = CurrentUserState.current();
//        if (userIdentity != null) {
//            return userIdentity.getId();
//        }
//        return "None-or-unknown";
//    }


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
            writeToDocumentLog (requestInfo.getLoggingInfo(), requestInfo.getRequestEntity(),
                    writerInterceptorContext.getEntity());
                    //stream.getResponseEntity());
        }
    }

    private void writeToDocumentLog (LoggingInfo info, Object requestEntity, Object responseEntity){
        if (info == null){
            return;
        }

        switch (info.getOperationType()){
            case DELETE:
                documentEventLog.delete(requestEntity,null);
                break;
            case VIEW:
                documentEventLog.view(responseEntity,null);
                break;
            case CREATE:
                documentEventLog.create(requestEntity,null);
                break;
        }
    }

//    void printRequestLine(final StringBuilder b, final String note, final long id, final String method, final URI uri) {
//        prefixId(b, id).append(NOTIFICATION_PREFIX)
//                .append(note)
//                .append(" on thread ").append(Thread.currentThread().getName())
//                .append("\n");
//        prefixId(b, id).append(REQUEST_PREFIX).append(method).append(" ")
//                .append(uri.toASCIIString()).append("\n");
//    }
//
//    void printResponseLine(final StringBuilder b, final String note, final long id, final int status) {
//        prefixId(b, id).append(NOTIFICATION_PREFIX)
//                .append(note)
//                .append(" on thread ").append(Thread.currentThread().getName()).append("\n");
//        prefixId(b, id).append(RESPONSE_PREFIX)
//                .append(status)
//                .append("\n");
//    }
//
//    void printPrefixedHeaders(final StringBuilder b,
//                              final long id,
//                              final String prefix,
//                              final MultivaluedMap<String, String> headers) {
//        for (final Map.Entry<String, List<String>> headerEntry : headers.entrySet()) {
//            final List<?> val = headerEntry.getValue();
//            final String header = headerEntry.getKey();
//
//            if (val.size() == 1) {
//                prefixId(b, id).append(prefix).append(header).append(": ").append(val.get(0)).append("\n");
//            } else {
//                final StringBuilder sb = new StringBuilder();
//                boolean add = false;
//                for (final Object s : val) {
//                    if (add) {
//                        sb.append(',');
//                    }
//                    add = true;
//                    sb.append(s);
//                }
//                prefixId(b, id).append(prefix).append(header).append(": ").append(sb.toString()).append("\n");
//            }
//        }
//    }

//        Set<Entry<String, List<String>>> getSortedHeaders(final Set<Map.Entry<String, List<String>>> headers) {
//            final TreeSet<Entry<String, List<String>>> sortedHeaders = new TreeSet<Map.Entry<String, List<String>>>(COMPARATOR);
//            sortedHeaders.addAll(headers);
//            return sortedHeaders;
//        }

//    InputStream recordInboundEntity(LoggingInfo loggingInfo, InputStream stream, final Charset charset) throws IOException {
//        LoggingInputStream inputStream = new LoggingInputStream(loggingInfo, stream, charset);
//
//        return inputStream;
//    }

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

        Optional<String> resourcePath = getResourcePath(resourceClass);
        if (resourcePath.isEmpty()){
            LOGGER.warn("Unable to determine HTTP method for class " + resourceClass.getName());
            return Stream.empty();
        }

        return Arrays.stream(resourceClass.getMethods()).sequential().flatMap(m -> {
            Optional<String> methodPath = getMethodPath (m);
            if (methodPath.isEmpty()) {
                return Stream.empty();
            }

            Optional<String> httpMethod = getHttpMethod (m, resourceClass);
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

    private Optional<String> getHttpMethod(Method method, Class<?> resourceClass){
        Optional<String> httpMethod = getHttpMethod(method);
        if (httpMethod.isPresent()){
            return httpMethod;
        }
        return getHttpMethod(resourceClass);
    }

    private Optional<String> getHttpMethod(AnnotatedElement element) {
        Optional <? extends Annotation> annotation = Optional.ofNullable(getInheritedAnnotation(GET.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.GET);
        }
        annotation = Optional.ofNullable(getInheritedAnnotation(PUT.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.PUT);
        }
        annotation = Optional.ofNullable(getInheritedAnnotation(POST.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.POST);
        }
        annotation = Optional.ofNullable(getInheritedAnnotation(DELETE.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.DELETE);
        }
        annotation = Optional.ofNullable(getInheritedAnnotation(PATCH.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.PATCH);
        }
        annotation = Optional.ofNullable(getInheritedAnnotation(HEAD.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.HEAD);
        }
        annotation = Optional.ofNullable(getInheritedAnnotation(OPTIONS.class, element));
        if (annotation.isPresent()) {
            return Optional.of(HttpMethod.OPTIONS);
        }

        return Optional.empty();
    }

    private static String stripVariableFromPath (String originalPath){
        if (originalPath.contains("{")){
            originalPath = originalPath.substring(0, originalPath.indexOf('{'));
        }
        return originalPath;
    }

    private static String createCallKey (String method, String originalPath){
        originalPath = stripVariableFromPath(originalPath);
        return method + ":" + originalPath;
    }

    private Optional<String> getMethodPath(final Method method) {
        Path annotation = getInheritedAnnotation(Path.class, method);
        if (annotation != null){
            return Optional.of(annotation.value());
        } else {
            return Optional.empty();
        }
    }

    private static <A extends Annotation> A getInheritedAnnotation(
            Class<A> annotationClass, AnnotatedElement element)
    {
        A annotation = element.getAnnotation(annotationClass);
        if (annotation == null && element instanceof Method)
            annotation = getOverriddenAnnotation(annotationClass, (Method) element);
        return annotation;
    }

    private static <A extends Annotation> A getOverriddenAnnotation(
            Class<A> annotationClass, Method method)
    {
        final Class<?> methodClass = method.getDeclaringClass();
        final String name = method.getName();
        final Class<?>[] params = method.getParameterTypes();

        // prioritize all superclasses over all interfaces
        final Class<?> superclass = methodClass.getSuperclass();
        if (superclass != null)
        {
            final A annotation =
                    getOverriddenAnnotationFrom(annotationClass, superclass, name, params);
            if (annotation != null)
                return annotation;
        }

        // depth-first search over interface hierarchy
        for (final Class<?> intf : methodClass.getInterfaces())
        {
            final A annotation =
                    getOverriddenAnnotationFrom(annotationClass, intf, name, params);
            if (annotation != null)
                return annotation;
        }

        return null;
    }

    private static <A extends Annotation> A getOverriddenAnnotationFrom(
            Class<A> annotationClass, Class<?> searchClass, String name, Class<?>[] params)
    {
        try
        {
            final Method method = searchClass.getMethod(name, params);
            final A annotation = method.getAnnotation(annotationClass);
            if (annotation != null)
                return annotation;
            return getOverriddenAnnotation(annotationClass, method);
        }
        catch (final NoSuchMethodException e)
        {
            return null;
        }
    }

    private Optional<String> getResourcePath(final Class<?> restResourceClass) {
        final Path pathAnnotation = restResourceClass.getAnnotation(Path.class);
        return Optional.ofNullable(pathAnnotation)
                .or(() ->
                        // No Path annotation on the RestResource so look for it in all interfaces
                        Arrays.stream(restResourceClass.getInterfaces())
                                .map(clazz -> clazz.getAnnotation(Path.class))
                                .filter(Objects::nonNull)
                                .findFirst())
                .map(path ->
                        ResourcePaths.buildAuthenticatedApiPath(path.value()));
    }





    @Override
    public void filter(final ContainerRequestContext context) throws IOException {

        final long id = _id.incrementAndGet();
        context.setProperty(LOGGING_ID_PROPERTY, id);

//        String user = getAuthenticatedUser();

        LoggingInfo loggingInfo = findLoggingInfo(context);

        if (context.hasEntity()) {
            final LoggingInputStream stream = new LoggingInputStream(loggingInfo, context.getEntityStream(),
                    MessageUtils.getCharset(context.getMediaType()));
            context.setEntityStream(stream);
//            context.setProperty(REQUEST_ENTITY_LOGGER_PROPERTY, stream);
            context.setProperty(REQUEST_LOG_INFO_PROPERTY, new RequestInfo(stream.getLoggingInfo(), stream.getRequestEntity()));
        }

    }


    @Override
    public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext responseContext)
            throws IOException {

//        final Object requestId = requestContext.getProperty(LOGGING_ID_PROPERTY);
//        final long id = requestId != null ? (Long) requestId : _id.incrementAndGet();
//
//        final StringBuilder b = new StringBuilder();
//
////        LoggingInputStream inputStream = (LoggingInputStream) requestContext.getProperty(REQUEST_ENTITY_LOGGER_PROPERTY);
//        RequestInfo requestInfo = (RequestInfo) requestContext.getProperty(REQUEST_LOG_INFO_PROPERTY);
//
//        final LoggingInfo loggingInfo;
//        if (requestInfo != null) {
//            loggingInfo = requestInfo.getLoggingInfo();
//        } else {
//            //todo check whether this branch ever executes
//            System.err.println("*** Check and remove ***");
//            loggingInfo = findLoggingInfo(requestContext);
//        }
////        if (responseContext.hasEntity()) {
////            final OutputStream stream = new LoggingOutputStream(b, responseContext.getEntityStream(),
////                    loggingInfo,
////                    ((inputStream != null)? inputStream.getRequestEntity() : null),
////                    responseContext.getEntity());
////            responseContext.setEntityStream(stream);
////            requestContext.setProperty(RESPONSE_ENTITY_LOGGER_PROPERTY, stream);
////        } else if (inputStream != null){
////            writeToDocumentLog(loggingInfo, inputStream.getRequestEntity(), null);
////        }

    }

    private static ObjectMapper createMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }

    private static class LoggingInfo {
        private final String httpMethod;
        private final String path;
        private final StroomLoggingOperationType operation;
        private final Method method;
        private final Class <?> resourceClass;

        public LoggingInfo(String httpMethod, String path, Method javaMethod, Class<?> resourceClass){
            this.httpMethod = httpMethod;
            this.path = path;
            this.method = javaMethod;
            this.resourceClass = resourceClass;
            operation = findOperation();
        }

        private Optional<StroomLoggingOperationType> getOperationType(final Class<?> restResourceClass) {
            final StroomLoggingOperation opAnnotation = restResourceClass.getAnnotation(StroomLoggingOperation.class);
            return Optional.ofNullable(opAnnotation)
                    .or(() ->
                            // No operation annotation on the RestResource so look for it in all interfaces
                            Arrays.stream(restResourceClass.getInterfaces())
                                    .map(clazz -> clazz.getAnnotation(StroomLoggingOperation.class))
                                    .filter(Objects::nonNull)
                                    .findFirst())
                    .map(op -> op.value());
        }

        private final StroomLoggingOperationType findOperation(){
            Optional<StroomLoggingOperationType> type = getOperationType(resourceClass);
            if (type.isPresent()){
                return type.get();
            } else if (HttpMethod.DELETE.equals(httpMethod)){
                return StroomLoggingOperationType.DELETE;
            } else if (method.getName().startsWith("get")){
                return StroomLoggingOperationType.VIEW;
            } else if (method.getName().startsWith("fetch")) {
                return StroomLoggingOperationType.VIEW;
            } else if (method.getName().startsWith("read")){
                return StroomLoggingOperationType.VIEW;
            } else if (method.getName().startsWith("create")){
                return StroomLoggingOperationType.CREATE;
            } else if (method.getName().startsWith("delete")){
                return StroomLoggingOperationType.DELETE;
            } else if (method.getName().startsWith("update")){
                return StroomLoggingOperationType.UPDATE;
            }  else if (method.getName().startsWith("save")){
                return StroomLoggingOperationType.UPDATE;
            } else if (method.getName().startsWith("find")){
                return StroomLoggingOperationType.SEARCH;
            } else if (method.getName().startsWith("search")){
                return StroomLoggingOperationType.SEARCH;
            }  else if (method.getName().startsWith("list")){
                return StroomLoggingOperationType.SEARCH;
            } else if (method.getName().startsWith("import")){
                return StroomLoggingOperationType.IMPORT;
            } else if (method.getName().startsWith("export")){
                return StroomLoggingOperationType.EXPORT;
            } else if (method.getName().startsWith("upload")){
                return StroomLoggingOperationType.IMPORT;
            } else if (method.getName().startsWith("download")){
                return StroomLoggingOperationType.EXPORT;
            } else if (method.getName().startsWith("set")){
                return StroomLoggingOperationType.UPDATE;
            } else if (method.getName().startsWith("copy")){
                return StroomLoggingOperationType.COPY;
            }
            return StroomLoggingOperationType.UNKNOWN;
        }

        public String getHttpMethod() {
            return httpMethod;
        }

        public String getPath() {
            return path;
        }

        public StroomLoggingOperationType getOperationType() {
            return operation;
        }

        public String createCallKey (){
            return StroomServerLoggingFilterImpl.createCallKey(httpMethod, path);
        }

        public Optional<Class> getRequestParamClass (){
            if (method.getParameterCount() == 0){
                return Optional.empty();
            } else if (method.getParameterCount() == 1){
                return Optional.of(method.getParameters()[0].getType());
            }
            //Can't work with multiple parameters.
            return null;
        }
    }

    class LoggingInputStream extends BufferedInputStream {
        private Object requestEntity;
        private final LoggingInfo loggingInfo;
        private boolean constructed;

        LoggingInputStream (final LoggingInfo loggingInfo, final InputStream original, final Charset charset) throws IOException {
            super(original);
            this.loggingInfo = loggingInfo;
            readEntity(charset);
            constructed = true;
        }

        private void readEntity(final Charset charset) throws IOException {
            if (loggingInfo != null){
                mark(maxEntitySize + 1);
                Optional<Class> requestClass = loggingInfo.getRequestParamClass();

                if (requestClass != null){
                    Object obj = objectMapper.readValue(new InputStreamReader(this, charset), requestClass.get());
                    requestEntity = obj;
                } else {
                    LOGGER.warn("Unable to determine type of object for automatic logging. HTTP request path is" + loggingInfo.getPath());
                }
                reset();
            }
        }

        public Object getRequestEntity() {
            return requestEntity;
        }

        public LoggingInfo getLoggingInfo() {
            return loggingInfo;
        }

        @Override
        public void close() throws IOException {
            if (constructed) {
                super.close();
            }
        }
    }

    /**
     * Helper class used to log an entity to the output stream up to the specified maximum number of bytes.
     */
//    static class LoggingOutputStream extends FilterOutputStream {
//
//        private final StringBuilder b;
//        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        private final LoggingInfo loggingInfo;
//        private final Object requestEntity;
//        private final Object responseEntity;
//        /**
//         * Creates {@code LoggingStream} with the entity and the underlying output stream as parameters.
//         *
//         * @param b     contains the entity to log.
//         * @param inner the underlying output stream.
//         */
//        LoggingOutputStream(final StringBuilder b, final OutputStream inner, final LoggingInfo loggingInfo,
//                            final Object requestEntity, final Object responseEntity) {
//            super(inner);
//
//            this.b = b;
//            this.requestEntity = requestEntity;
//            this.responseEntity = responseEntity;
//            this.loggingInfo = loggingInfo;
//        }
//
//        public StringBuilder getStringBuilder(final Charset charset) {
//            // write entity to the builder
//            final byte[] entity = baos.toByteArray();
//
//            b.append(new String(entity, 0, Math.min(entity.length, maxEntitySize), charset));
//            if (entity.length > maxEntitySize) {
//                b.append("...more...");
//            }
//            b.append('\n');
//
//            return b;
//        }
//
//        public Object getRequestEntity() {
//            return requestEntity;
//        }
//
//        public Object getResponseEntity() {
//            return responseEntity;
//        }
//
//        public LoggingInfo getLoggingInfo() {
//            return loggingInfo;
//        }
//
//        @Override
//        public void write(final int i) throws IOException {
//            if (baos.size() <= maxEntitySize) {
//                baos.write(i);
//            }
//            out.write(i);
//        }
//    }
}