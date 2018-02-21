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

package stroom.entity;

import javassist.Modifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import stroom.entity.util.XMLMarshallerUtil;
import stroom.entity.shared.BaseEntity;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;

public abstract class EntityMarshaller<E extends BaseEntity, O> implements Marshaller<E, O> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityMarshaller.class);

    private final JAXBContext jaxbContext;

    public EntityMarshaller() {
        try {
            jaxbContext = JAXBContext.newInstance(getObjectType());
        } catch (final JAXBException e) {
            LOGGER.error(MarkerFactory.getMarker("FATAL"), "Unable to create new JAXBContext for object type!", e);
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public E marshal(final E entity) {
        try {
            Object object = getObject(entity);

            // Strip out references to empty collections.
            try {
                object = XMLMarshallerUtil.removeEmptyCollections(object);
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }

            final String data = XMLMarshallerUtil.marshal(jaxbContext, object);
            setData(entity, data);
        } catch (final Exception e) {
            LOGGER.debug("Problem marshaling {} {}", new Object[]{entity.getClass(), entity.getId()}, e);
            LOGGER.warn("Problem marshaling {} {} - {} (enable debug for full trace)", new Object[]{entity.getClass(),
                    entity.getId(), String.valueOf(e)});
        }
        return entity;
    }

    @Override
    public E unmarshal(final E entity) {
        try {
            final String data = getData(entity);
            final O object = XMLMarshallerUtil.unmarshal(jaxbContext, getObjectType(), data);
            setObject(entity, object);
        } catch (final Exception e) {
            LOGGER.debug("Unable to unmarshal entity!", e);
            LOGGER.warn(e.getMessage());
        }
        return entity;
    }

    protected abstract String getData(E entity);

    protected abstract void setData(E entity, String data);

    protected abstract Class<O> getObjectType();

    protected abstract String getEntityType();
}
