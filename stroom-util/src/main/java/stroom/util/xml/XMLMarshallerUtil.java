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

package stroom.util.xml;

import stroom.util.io.StreamUtil;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import javassist.Modifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public final class XMLMarshallerUtil {

    private XMLMarshallerUtil() {
        // Utility class.
    }

    public static Object removeEmptyCollections(final Object obj) {
        return deepClone(obj, 0);
    }

    private static Object deepClone(final Object obj, final int depth) {
        Object result = null;
        try {
            if (obj != null) {
                Class<?> clazz = obj.getClass();
                final Object clone;

                try {
                    clone = clazz.getConstructor().newInstance();
                } catch (final NoSuchMethodException
                               | InvocationTargetException
                               | InstantiationException
                               | IllegalAccessException e) {
                    return obj;
                }

                while (clazz != null) {
                    for (final Field field : clazz.getDeclaredFields()) {
                        if (!(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()))) {
                            field.setAccessible(true);
                            try {
                                Object o = field.get(obj);
                                if (o != null) {
                                    if (o instanceof Collection<?>) {
                                        final Collection<?> collection = (Collection<?>) o;
                                        if (collection.size() == 0) {
                                            o = null;
                                        }
                                    } else if (field.getType().isArray()) {
                                        if (Array.getLength(o) == 0) {
                                            o = null;
                                        }
                                    } else if (!field.getType().equals(String.class)) {
                                        o = deepClone(o, depth + 1);
                                    }
                                }

                                field.set(clone, o);

                                if (o != null) {
                                    result = clone;
                                }
                            } catch (final IllegalAccessException e) {
                                throw new RuntimeException(e.getMessage(), e);
                            }
                        }
                    }
                    clazz = clazz.getSuperclass();
                }

                // If we are at the depth of the initial object then ensure we
                // return a clone of the
                // initial object as we don't want null tobe returned unless
                // null was supplied.
                if (result == null && depth == 0) {
                    result = clone;
                }
            }
        } catch (final RuntimeException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        return result;
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
        } catch (final IOException | JAXBException | TransformerConfigurationException e) {
            throw new RuntimeException(e.getMessage(), e);
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
