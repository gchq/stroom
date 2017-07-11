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

package stroom.entity.server.util;

import stroom.util.io.StreamUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class XMLMarshallerUtil {
    private XMLMarshallerUtil() {
        // Utility class.
    }

    public static <T> String marshal(final JAXBContext context, final T obj, final XmlAdapter<?, ?>... adapters) {
        if (obj == null) {
            return null;
        }

        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final Marshaller marshaller = context.createMarshaller();

            if (adapters != null) {
                for (final XmlAdapter<?, ?> adapter : adapters) {
                    marshaller.setAdapter(adapter);
                }
            }

            final TransformerHandler transformerHandler = XMLUtil.createTransformerHandler(true);
            transformerHandler.setResult(new StreamResult(out));
            marshaller.marshal(obj, transformerHandler);

            return out.toString(StreamUtil.DEFAULT_CHARSET_NAME);
        } catch (final Throwable t) {
            throw new RuntimeException(t.getMessage(), t);
        }
    }

    public static <T> T unmarshal(final JAXBContext context, final Class<T> clazz, final String data,
            final XmlAdapter<?, ?>... adapters) {
        if (data != null) {
            final String trimmed = data.trim();
            if (trimmed.length() > 0) {
                try {
                    final Unmarshaller unmarshaller = context.createUnmarshaller();

                    if (adapters != null) {
                        for (final XmlAdapter<?, ?> adapter : adapters) {
                            unmarshaller.setAdapter(adapter);
                        }
                    }

                    final JAXBElement<T> jaxbElement = unmarshaller.unmarshal(
                            new StreamSource(new ByteArrayInputStream(trimmed.getBytes(StreamUtil.DEFAULT_CHARSET))),
                            clazz);
                    return jaxbElement.getValue();
                } catch (final JAXBException e) {
                    throw new RuntimeException("Invalid XML " + trimmed, e);
                }
            }
        }
        return null;
    }
}
