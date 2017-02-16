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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.server.util.XMLMarshallerUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

public class ObjectMarshaller<E> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectMarshaller.class);

    private final Class<E> clazz;
    private final JAXBContext jaxbContext;

    public ObjectMarshaller(Class<E> clazz) {
        this.clazz = clazz;
        try {
            jaxbContext = JAXBContext.newInstance(clazz);
        } catch (final JAXBException e) {
            LOGGER.error("Unable to create a new JAXBContext!", e);
            throw new RuntimeException(e.getMessage());
        }
    }

    public String marshal(final E object) {
        String xml = null;

        if (object != null) {
            try {
                xml = XMLMarshallerUtil.marshal(jaxbContext, object);
            } catch( final Exception e){
                LOGGER.debug("Problem marshalling %s", object, e);
                LOGGER.warn("Problem marshalling %s - %s (enable debug for full trace)", object, String.valueOf(e));
            }
        }

        return xml;
    }

    public E unmarshal(final String xml) {
        E object = null;
        if (xml != null && xml.length() > 0) {
            try {
                object = XMLMarshallerUtil.unmarshal(jaxbContext, clazz, xml);
            } catch (final Exception e) {
                LOGGER.debug("Problem unmarshalling\n%s", xml, e);
                LOGGER.warn("Problem unmarshalling - %s(enable debug for full trace)", String.valueOf(e));
            }
        }
        return object;
    }
}
