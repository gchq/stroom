/*
 * Copyright 2020 Crown Copyright
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

import stroom.util.shared.AutoLogged;
import stroom.util.shared.AutoLogged.OperationType;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import java.lang.reflect.Method;
import java.util.Optional;

public class ContainerResourceInfo {
    private final ContainerRequestContext requestContext;
    private final ResourceInfo resourceInfo;
    private final OperationType operationType;

    public ContainerResourceInfo(final ResourceInfo resourceInfo, final ContainerRequestContext requestContext) {
        this.resourceInfo = resourceInfo;
        this.requestContext = requestContext;
        this.operationType = findOperationType(getMethod(), getResourceClass(), requestContext.getMethod());
    }

    public ContainerRequestContext getRequestContext() {
        return requestContext;
    }

    public ResourceInfo getResourceInfo() {
        return resourceInfo;
    }

    public Class<?> getResourceClass() {
        return resourceInfo.getResourceClass();
    }

    public Method getMethod(){
        return resourceInfo.getResourceMethod();
    }

    public OperationType getOperationType(){
        return operationType;
    }

    public String getTypeId(){
        //If method annotation provided use that on its own
        if ((getMethod().getAnnotation(AutoLogged.class) != null) &&
                (!getMethod().getAnnotation(AutoLogged.class).typeId().equals(AutoLogged.ALLOCATE_AUTOMATICALLY))){
            return getMethod().getAnnotation(AutoLogged.class).typeId();
        }
        String resourcePrefix = getResourceClass().getSimpleName();
        if ((getResourceClass().getAnnotation(AutoLogged.class) != null) &&
                (!getResourceClass().getAnnotation(AutoLogged.class).typeId().equals(AutoLogged.ALLOCATE_AUTOMATICALLY))){
            resourcePrefix = getResourceClass().getAnnotation(AutoLogged.class).typeId();
        }

        return resourcePrefix + "." + getMethod().getName();
    }

    public String getVerbFromAnnotations(){
        if ((getMethod().getAnnotation(AutoLogged.class) != null) &&
                (!getMethod().getAnnotation(AutoLogged.class).verb().equals(AutoLogged.ALLOCATE_AUTOMATICALLY))){
            return getMethod().getAnnotation(AutoLogged.class).verb();
        }
        return null;
    }

    private static Optional<OperationType> getOperationTypeFromAnnotations(final Method method,
                                                                           final Class<?> resourceClass){
        if (method.getAnnotation(AutoLogged.class) != null){
            return Optional.of(method.getAnnotation(AutoLogged.class).value());
        } else if (resourceClass.getAnnotation(AutoLogged.class) != null){
            return Optional.of(resourceClass.getAnnotation(AutoLogged.class).value());
        }
        return Optional.empty();
    }

    public Optional<OperationType> getOperationTypeFromAnnotations(){
        return getOperationTypeFromAnnotations(getMethod(), getResourceClass());
    }

    public boolean shouldLog (boolean logByDefault){

        Optional<OperationType> specifiedOperation = getOperationTypeFromAnnotations();
        if (specifiedOperation.isPresent()){
            return !specifiedOperation.get().equals(OperationType.UNLOGGED);
        }

        return logByDefault;
    }

    private OperationType findOperationType(final Method method,
                                            final Class<?> resourceClass,
                                            final String httpMethod) {
        Optional<OperationType> type = getOperationTypeFromAnnotations(method, resourceClass);
        if (type.isPresent() && !OperationType.ALLOCATE_AUTOMATICALLY.equals(type.get())){
            return type.get();
        } else if (HttpMethod.DELETE.equals(httpMethod)){
            return OperationType.DELETE;
        } else if (method.getName().startsWith("get")){
            return OperationType.VIEW;
        } else if (method.getName().startsWith("fetch")) {
            return OperationType.VIEW;
        } else if (method.getName().startsWith("read")){
            return OperationType.VIEW;
        } else if (method.getName().startsWith("create")){
            return OperationType.CREATE;
        } else if (method.getName().startsWith("delete")){
            return OperationType.DELETE;
        } else if (method.getName().startsWith("update")){
            return OperationType.UPDATE;
        }  else if (method.getName().startsWith("save")){
            return OperationType.UPDATE;
        } else if (method.getName().startsWith("find")){
            return OperationType.SEARCH;
        } else if (method.getName().startsWith("search")){
            return OperationType.SEARCH;
        }  else if (method.getName().startsWith("list")){
            return OperationType.SEARCH;
        } else if (method.getName().startsWith("import")){
            return OperationType.IMPORT;
        } else if (method.getName().startsWith("export")){
            return OperationType.EXPORT;
        } else if (method.getName().startsWith("upload")){
            return OperationType.IMPORT;
        } else if (method.getName().startsWith("download")){
            return OperationType.EXPORT;
        } else if (method.getName().startsWith("set")){
            return OperationType.UPDATE;
        } else if (method.getName().startsWith("copy")){
            return OperationType.COPY;
        }
        return OperationType.UNKNOWN;
    }

}
