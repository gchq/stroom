package stroom.security.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.UserIdentity;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;
import stroom.util.shared.StroomLoggingOperation;
import stroom.util.shared.StroomLoggingOperationType;
import stroom.util.string.EncodingUtil;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.web.bindery.requestfactory.server.Logging;
import io.dropwizard.setup.Environment;
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
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StroomServerLoggingFilterImpl implements StroomServerLoggingFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomServerLoggingFilterImpl.class);

    private static final String NOTIFICATION_PREFIX = "NOTE";
    private static final String RESPONSE_PREFIX = "RESP";
    private static final String REQUEST_PREFIX = "REQ";
    private static final String LOGGING_ID_PROPERTY = "StroomLogging.id";
    private static final String ENTITY_LOGGER_PROPERTY = "StroomLogging.entity";
    private final int maxEntitySize = 10000;
    private final AtomicLong _id = new AtomicLong(0);
    private Map<String, List<LoggingInfo>> loggingInfoMap;

    private final Environment environment;
    private final Map<RestResourcesBinder.ResourceType, Provider<RestResource>> providerMap;
    private final DocumentEventLog documentEventLog;

    @Inject
    StroomServerLoggingFilterImpl(final Environment environment, final Map<RestResourcesBinder.ResourceType,
            Provider<RestResource>> providerMap, DocumentEventLog documentEventLog) {
        this.environment = environment;
        this.providerMap = providerMap;
        this.documentEventLog = documentEventLog;
    }
    public String getAuthenticatedUser (){
        final UserIdentity userIdentity = CurrentUserState.current();
        if (userIdentity != null) {
            return userIdentity.getId();
        }
        return "None-or-unknown";
    }

    void log(final StringBuilder b) {
        System.out.println(b);
    }

    private StringBuilder prefixId(final StringBuilder b, final long id) {
        b.append(Long.toString(id)).append(" ");
        return b;
    }

    @Override
    public void aroundWriteTo(final WriterInterceptorContext writerInterceptorContext)
            throws IOException, WebApplicationException {
        final LoggingStream stream = (LoggingStream) writerInterceptorContext.getProperty(ENTITY_LOGGER_PROPERTY);
        writerInterceptorContext.proceed();
        if (true) {
            if (stream != null) {

                writeToDocumentLog (stream.getLoggingInfo(), stream.getEntity());
                log(stream.getStringBuilder(MessageUtils.getCharset(writerInterceptorContext.getMediaType())));
            }
        }
    }

    private void writeToDocumentLog (LoggingInfo info, Object entity){
        if (info == null){
            return;
        }

        switch (info.getOperationType()){
            case DELETE:
                documentEventLog.delete(entity,null);
                break;
        }
    }

    void printRequestLine(final StringBuilder b, final String note, final long id, final String method, final URI uri) {
        prefixId(b, id).append(NOTIFICATION_PREFIX)
                .append(note)
                .append(" on thread ").append(Thread.currentThread().getName())
                .append("\n");
        prefixId(b, id).append(REQUEST_PREFIX).append(method).append(" ")
                .append(uri.toASCIIString()).append("\n");
    }

    void printResponseLine(final StringBuilder b, final String note, final long id, final int status) {
        prefixId(b, id).append(NOTIFICATION_PREFIX)
                .append(note)
                .append(" on thread ").append(Thread.currentThread().getName()).append("\n");
        prefixId(b, id).append(RESPONSE_PREFIX)
                .append(Integer.toString(status))
                .append("\n");
    }

    void printPrefixedHeaders(final StringBuilder b,
                              final long id,
                              final String prefix,
                              final MultivaluedMap<String, String> headers) {
        for (final Map.Entry<String, List<String>> headerEntry : headers.entrySet()) {
            final List<?> val = headerEntry.getValue();
            final String header = headerEntry.getKey();

            if (val.size() == 1) {
                prefixId(b, id).append(prefix).append(header).append(": ").append(val.get(0)).append("\n");
            } else {
                final StringBuilder sb = new StringBuilder();
                boolean add = false;
                for (final Object s : val) {
                    if (add) {
                        sb.append(',');
                    }
                    add = true;
                    sb.append(s);
                }
                prefixId(b, id).append(prefix).append(header).append(": ").append(sb.toString()).append("\n");
            }
        }
    }

//        Set<Entry<String, List<String>>> getSortedHeaders(final Set<Map.Entry<String, List<String>>> headers) {
//            final TreeSet<Entry<String, List<String>>> sortedHeaders = new TreeSet<Map.Entry<String, List<String>>>(COMPARATOR);
//            sortedHeaders.addAll(headers);
//            return sortedHeaders;
//        }

    InputStream logInboundEntity(final StringBuilder b, InputStream stream, final Charset charset) throws IOException {
        if (!stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }
        stream.mark(maxEntitySize + 1);
        final byte[] entity = new byte[maxEntitySize + 1];
        final int entitySize = stream.read(entity);
        b.append(new String(entity, 0, Math.min(entitySize, maxEntitySize), charset));
        if (entitySize > maxEntitySize) {
            b.append("...more...");
        }
        b.append('\n');
        stream.reset();

//        ObjectMapper mapper = new ObjectMapper();
//        Object marsalled = mapper.readValue(new StringReader(EncodingUtil.asString(entity))), Something.class);
        return stream;
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
            if (methodPath.isEmpty()) {
                LOGGER.warn("Unable to determine HTTP method for api call " + methodPath.get());
                return Stream.empty();
            }

            return Stream.of(new LoggingInfo(
                    httpMethod.get(),
                    stripVariableFromPath(resourcePath.get() + methodPath.get()),
                    m, resourceClass));
        });
    }

    private Optional<String> getHttpMethod(Method method, Class resourceClass){
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

        final StringBuilder b = new StringBuilder();

        String user = getAuthenticatedUser();

        printRequestLine(b,  "User:" + user + " " +"Server has received a request", id, context.getMethod(), context.getUriInfo().getRequestUri());

        printPrefixedHeaders(b, id, REQUEST_PREFIX, context.getHeaders());

        if (context.hasEntity()) {
            context.setEntityStream(
                    logInboundEntity(b, context.getEntityStream(), MessageUtils.getCharset(context.getMediaType())));
        }

        log(b);
    }


    @Override
    public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext responseContext)
            throws IOException {

        String user = getAuthenticatedUser();

        final Object requestId = requestContext.getProperty(LOGGING_ID_PROPERTY);
        final long id = requestId != null ? (Long) requestId : _id.incrementAndGet();

        final StringBuilder b = new StringBuilder();

        printResponseLine(b, id + " User:" + user + " " + "Server responded with a response", id, responseContext.getStatus());
        printPrefixedHeaders(b, id, RESPONSE_PREFIX, responseContext.getStringHeaders());

        LoggingInfo loggingInfo = findLoggingInfo (requestContext.getMethod(),
                ResourcePaths.buildAuthenticatedApiPath(requestContext.getUriInfo().getPath()));

        if (loggingInfo != null){
            System.out.println("" + id + " Found a " + loggingInfo.getClass().getName()  + " on " + loggingInfo.getPath());



            if (!loggingInfo.getOperationType().equals(StroomLoggingOperationType.UNKNOWN)){
                System.out.println("" + id + " Logging operation type " + loggingInfo.getOperationType());
            }

        }
        if (responseContext.hasEntity() ) {
            System.out.println ("" + id + " Returning a " + responseContext.getEntity().getClass());

            final OutputStream stream = new LoggingStream(b, requestContext.getEntityStream(),
                    loggingInfo, responseContext.getEntity());
            responseContext.setEntityStream(stream);
            requestContext.setProperty(ENTITY_LOGGER_PROPERTY, stream);



            // not calling log(b) here - it will be called by the interceptor
        } else {
            log(b);
        }
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
    }

    /**
     * Helper class used to log an entity to the output stream up to the specified maximum number of bytes.
     */
    class LoggingStream extends FilterOutputStream {

        private final StringBuilder b;
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        private final LoggingInfo loggingInfo;
        private final Object entity;
        /**
         * Creates {@code LoggingStream} with the entity and the underlying output stream as parameters.
         *
         * @param b     contains the entity to log.
         * @param inner the underlying output stream.
         */
        LoggingStream(final StringBuilder b, final OutputStream inner, final LoggingInfo loggingInfo, final Object entity) {
            super(inner);

            this.b = b;
            this.entity = entity;
            this.loggingInfo = loggingInfo;
        }

        public StringBuilder getStringBuilder(final Charset charset) {
            // write entity to the builder
            final byte[] entity = baos.toByteArray();

            b.append(new String(entity, 0, Math.min(entity.length, maxEntitySize), charset));
            if (entity.length > maxEntitySize) {
                b.append("...more...");
            }
            b.append('\n');

            return b;
        }

        public Object getEntity () throws IOException{
            return entity;
        }

        public LoggingInfo getLoggingInfo() {
            return loggingInfo;
        }

        @Override
        public void write(final int i) throws IOException {
            if (baos.size() <= maxEntitySize) {
                baos.write(i);
            }
            out.write(i);
        }
    }
}