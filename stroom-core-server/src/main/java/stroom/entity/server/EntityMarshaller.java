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

import stroom.entity.server.util.XMLMarshallerUtil;
import stroom.entity.shared.BaseEntity;
import stroom.util.logging.StroomLogger;
import javassist.Modifier;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;

public abstract class EntityMarshaller<E extends BaseEntity, O> implements Marshaller<E, O> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(EntityMarshaller.class);

    private final JAXBContext jaxbContext;

    public EntityMarshaller() {
        try {
            jaxbContext = JAXBContext.newInstance(getObjectType());
        } catch (final JAXBException e) {
            LOGGER.fatal(e, e);
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public E marshal(final E entity) {
        return marshal(entity, false, false);
    }

    @Override
    public E marshal(final E entity, final boolean external, final boolean ignoreErrors) {
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
            LOGGER.debug("Problem marshaling %s %s", entity.getClass(), entity.getId(), e);
            LOGGER.warn("Problem marshaling %s %s - %s (enable debug for full trace)", entity.getClass(),
                    entity.getId(), String.valueOf(e));
        }
        return entity;
    }

    @Override
    public E unmarshal(final E entity) {
        return unmarshal(entity, false, false);
    }

    @Override
    public E unmarshal(final E entity, final boolean external, final boolean ignoreErrors) {
        try {
            final String data = getData(entity);
            final O object = XMLMarshallerUtil.unmarshal(jaxbContext, getObjectType(), data);
            setObject(entity, object);
        } catch (final Exception e) {
            LOGGER.debug(e, e);
            LOGGER.warn(e.getMessage());
        }
        return entity;
    }

    protected abstract String getData(E entity);

    protected abstract void setData(E entity, String data);

    protected abstract Class<O> getObjectType();

    protected abstract String getEntityType();
}
