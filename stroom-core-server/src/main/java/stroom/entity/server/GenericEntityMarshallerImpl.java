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

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import stroom.entity.shared.BaseEntity;

@Component
public class GenericEntityMarshallerImpl implements GenericEntityMarshaller, BeanPostProcessor {
    private final Map<String, EntityMarshaller<?, ?>> map = new HashMap<>();

    @Override
    public Object postProcessBeforeInitialization(final Object bean, final String beanName) throws BeansException {
        if (bean instanceof EntityMarshaller<?, ?>) {
            final EntityMarshaller<?, ?> marshaller = (EntityMarshaller<?, ?>) bean;
            map.put(marshaller.getEntityType(), marshaller);
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        return bean;
    }

    @Override
    public <E extends BaseEntity> E marshal(final String entityType, final E entity) {
        if (entity == null) {
            return null;
        }

        E ent = entity;
        final Marshaller<E, ?> marshaller = getMarshaller(entityType);
        if (marshaller != null) {
            ent = marshaller.marshal(entity);
        }
        return ent;
    }

    @Override
    public <E extends BaseEntity> E unmarshal(final String entityType, final E entity) {
        if (entity == null) {
            return null;
        }

        E ent = entity;
        final Marshaller<E, ?> marshaller = getMarshaller(entityType);
        if (marshaller != null) {
            ent = marshaller.unmarshal(entity);
        }
        return ent;
    }

    @Override
    public <E extends BaseEntity> Object getObject(final String entityType, final E entity) {
        if (entity == null) {
            return null;
        }

        Object object = null;
        final Marshaller<E, ?> marshaller = getMarshaller(entityType);
        if (marshaller != null) {
            marshaller.unmarshal(entity);
            object = marshaller.getObject(entity);
        }
        return object;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <E extends BaseEntity> void setObject(final String entityType, final E entity, final Object object) {
        final Marshaller<E, Object> marshaller = (Marshaller<E, Object>) getMarshaller(entityType);
        if (marshaller != null) {
            marshaller.setObject(entity, object);
            marshaller.marshal(entity);
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends BaseEntity> Marshaller<E, ?> getMarshaller(final String entityType) {
        return (Marshaller<E, ?>) map.get(entityType);
    }
}
