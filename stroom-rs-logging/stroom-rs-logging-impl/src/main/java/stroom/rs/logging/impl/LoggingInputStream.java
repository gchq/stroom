package stroom.rs.logging.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Parameter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static stroom.rs.logging.impl.StroomServerLoggingFilterImpl.LOGGER;

class LoggingInputStream extends BufferedInputStream {
    private final static int MAX_ENTITY_SIZE = 64 * 1024 * 1024;
    private Object requestEntity;
    private final Class<?> requestParamClass;
    private final boolean constructed;

    public LoggingInputStream (final ResourceInfo resourceInfo, final InputStream original, final ObjectMapper objectMapper, final Charset charset) throws IOException {
        super(original);
        this.requestParamClass = findRequestParamClass(resourceInfo);
        readEntity(objectMapper, charset);
        constructed = true;
    }

    private void readEntity(final ObjectMapper objectMapper, final Charset charset)  {

        if (requestParamClass != null) {
            try {
                mark(MAX_ENTITY_SIZE + 1);
                requestEntity = objectMapper.readValue(new InputStreamReader(this, charset), requestParamClass);
                reset();
            } catch (Exception ex){
                //Indicates that this request type cannot be constructed in this way.
                requestEntity = null;
            }
        }

    }

    private Class<?> findRequestParamClass(final ResourceInfo resourceInfo){
        if (resourceInfo.getResourceMethod().getParameterCount() == 0){
            return null;
        }

        //Select the first param that isn't provided by the context itself (or parameters)
        List<Class<?>> suppliedParams = Arrays.stream(resourceInfo.getResourceMethod().getParameters()).sequential().
                filter(p -> AnnotationUtil.getInheritedParameterAnnotation(Context.class,
                        resourceInfo.getResourceMethod(), p) == null).
                filter(p -> AnnotationUtil.getInheritedParameterAnnotation(PathParam.class,
                        resourceInfo.getResourceMethod(), p) == null).
                filter(p -> AnnotationUtil.getInheritedParameterAnnotation(QueryParam.class,
                        resourceInfo.getResourceMethod(), p) == null).
                map(Parameter::getType).collect(Collectors.toList());
        if (suppliedParams.isEmpty()){
            return null;
        }
        if (suppliedParams.size() > 1){
            LOGGER.error("Multiple parameters to resource method " + resourceInfo.getResourceMethod().getName() + " on " + resourceInfo.getResourceClass().getSimpleName());
        }
        return suppliedParams.get(0);
    }

    public Object getRequestEntity() {
        return requestEntity;
    }

    @Override
    public void close() throws IOException {
        if (constructed) {
            super.close();
        }
    }
}
