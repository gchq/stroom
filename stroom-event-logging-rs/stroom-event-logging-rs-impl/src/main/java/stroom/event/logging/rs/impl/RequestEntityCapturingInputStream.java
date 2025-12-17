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

package stroom.event.logging.rs.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Parameter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static stroom.event.logging.rs.impl.RestResourceAutoLoggerImpl.LOGGER;

class RequestEntityCapturingInputStream extends BufferedInputStream {

    private static final int MAX_ENTITY_SIZE = 64 * 1024 * 1024;
    private Object requestEntity;
    private final Class<?> requestParamClass;
    private final boolean constructed;

    public RequestEntityCapturingInputStream(final ResourceInfo resourceInfo,
                                             final InputStream original,
                                             final ObjectMapper objectMapper,
                                             final Charset charset) throws IOException {
        super(original);
        this.requestParamClass = findRequestParamClass(resourceInfo);
        readEntity(objectMapper, charset);
        constructed = true;
    }

    private void readEntity(final ObjectMapper objectMapper, final Charset charset) {

        if (requestParamClass != null) {
            try {
                mark(MAX_ENTITY_SIZE + 1);
                requestEntity = objectMapper.readValue(new InputStreamReader(this, charset), requestParamClass);
            } catch (final Exception ex) {
                //Indicates that this request type cannot be constructed in this way.
                requestEntity = null;
            } finally {
                try {
                    reset();
                } catch (final IOException e) {
                    LOGGER.error("Unable to reset stream", e);
                }
            }
        }

    }

    private Class<?> findRequestParamClass(final ResourceInfo resourceInfo) {
        if (resourceInfo.getResourceMethod().getParameterCount() == 0) {
            return null;
        }

        //Select the first param that isn't provided by the context itself (or parameters)
        final List<Class<?>> suppliedParams = Arrays.stream(resourceInfo.getResourceMethod().getParameters())
                .sequential()
                .filter(p ->
                        AnnotationUtil.getInheritedParameterAnnotation(
                                Context.class, resourceInfo.getResourceMethod(), p) == null)
                .filter(p ->
                        AnnotationUtil.getInheritedParameterAnnotation(
                                PathParam.class, resourceInfo.getResourceMethod(), p) == null)
                .filter(p ->
                        AnnotationUtil.getInheritedParameterAnnotation(
                                QueryParam.class, resourceInfo.getResourceMethod(), p) == null)
                .map(Parameter::getType)
                .collect(Collectors.toList());

        if (suppliedParams.isEmpty()) {
            return null;
        }
        if (suppliedParams.size() > 1) {
            LOGGER.error(() -> "Multiple parameters to resource method " +
                    resourceInfo.getResourceMethod().getName() +
                    " on " +
                    resourceInfo.getResourceClass().getSimpleName());
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
