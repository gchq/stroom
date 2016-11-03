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

package stroom.entity.server;

import stroom.entity.shared.BaseEntity;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * Interceptor to marshal and unmarshal entities using JAXB.
 * </p>
 */
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE)
@Component
public class MarshalingMethodInterceptor {
    @Resource
    private GenericEntityMarshaller genericEntityMarshaller;
    @Resource
    private MarshalOptions marshalOptions;

    @Pointcut("@within(stroom.entity.server.AutoMarshal)")
    public void hasAnnotationOnType() {
    }

    @Pointcut("@annotation(stroom.entity.server.AutoMarshal)")
    public void hasAnnotationOnMethod() {
    }

    @Pointcut("execution(public * find*(..))")
    public void isFind() {
    }

    @Pointcut("execution(public * load*(..))")
    public void isLoad() {
    }

    @Pointcut("execution(public * save(..))")
    public void isSave() {
    }

    @Pointcut("execution(public * copy(..))")
    public void isSaveAs() {
    }

    @Around("(hasAnnotationOnType() || hasAnnotationOnMethod()) && isFind()")
    public Object find(final ProceedingJoinPoint joinPoint) throws Throwable {
        if (marshalOptions.isDisabled()) {
            return joinPoint.proceed();
        }

        final Object result = joinPoint.proceed();
        if (result != null && result instanceof List<?>) {
            final List<?> list = (List<?>) result;
            for (final Object obj : list) {
                if (obj != null && obj instanceof BaseEntity) {
                    final BaseEntity entity = (BaseEntity) obj;
                    genericEntityMarshaller.unmarshal(entity.getType(), entity);
                }
            }
        }
        return result;
    }

    @Around("(hasAnnotationOnType() || hasAnnotationOnMethod()) && isLoad()")
    public Object load(final ProceedingJoinPoint joinPoint) throws Throwable {
        if (marshalOptions.isDisabled()) {
            return joinPoint.proceed();
        }

        final Object obj = joinPoint.proceed();
        if (obj != null && obj instanceof BaseEntity) {
            final BaseEntity entity = (BaseEntity) obj;
            genericEntityMarshaller.unmarshal(entity.getType(), entity);
        }
        return obj;
    }

    @Around("(hasAnnotationOnType() || hasAnnotationOnMethod()) && (isSave() || isSaveAs())")
    public Object save(final ProceedingJoinPoint joinPoint) throws Throwable {
        if (marshalOptions.isDisabled()) {
            return joinPoint.proceed();
        }

        if (joinPoint.getArgs().length == 0
                || (joinPoint.getArgs()[0] != null && !(joinPoint.getArgs()[0] instanceof BaseEntity))) {
            throw new RuntimeException("First argument is expected to be entity");
        }

        Object obj = joinPoint.getArgs()[0];
        if (obj != null && obj instanceof BaseEntity) {
            final BaseEntity entity = (BaseEntity) obj;
            genericEntityMarshaller.marshal(entity.getType(), entity);
        }
        obj = joinPoint.proceed();
        if (obj != null && obj instanceof BaseEntity) {
            final BaseEntity entity = (BaseEntity) obj;
            genericEntityMarshaller.unmarshal(entity.getType(), entity);
        }
        return obj;
    }
}
