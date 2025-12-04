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

package stroom.processor.impl.db.migration.legacyqd;

import stroom.util.xml.XMLMarshallerUtil;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractXMLSerialiser<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractXMLSerialiser.class);

    private final JAXBContext jaxbContext;

    public AbstractXMLSerialiser() throws JAXBException {
        this.jaxbContext = getJaxbContext();
    }

    public String serialise(T object) {
        try {
            try {
                // Strip out references to empty collections.
                object = (T) XMLMarshallerUtil.removeEmptyCollections(object);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            return XMLMarshallerUtil.marshal(jaxbContext, object);

        } catch (final RuntimeException e) {
            LOGGER.debug("Problem serialising {} {}", new Object[]{getSerialisableClass(), object}, e);
            LOGGER.warn("Problem serialising {} {} - {} (enable debug for full trace)",
                    getSerialisableClass(), object, String.valueOf(e));
        }
        return null;
    }

    public T deserialise(final String xml) {
        try {
            return XMLMarshallerUtil.unmarshal(jaxbContext, getSerialisableClass(), xml);
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to deserialise", e);
            LOGGER.warn(e.getMessage());
        }
        return null;
    }

    private JAXBContext getJaxbContext() throws JAXBException {

        try {
            return JAXBContext.newInstance(getSerialisableClass());
        } catch (final JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract Class<? extends T> getSerialisableClass();
}
