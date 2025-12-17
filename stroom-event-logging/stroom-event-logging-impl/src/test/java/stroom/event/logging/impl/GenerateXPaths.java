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

package stroom.event.logging.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.google.common.base.Strings;
import event.logging.Events;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBIntrospector;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * This was a partial attempt at generating all the possible xpaths for the event logging schema,
 * baring infinite recursion.
 * </p><p>
 * Seems to be missing some elements due to getField returning null. Also is not doing anything about
 * choice elements or handling sub-classed things.
 * </p>
 */
@Disabled
public class GenerateXPaths {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GenerateXPaths.class);

    @Test
    void name() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();
//        final Events events = new Events();
        final List<String> xPaths = new ArrayList<>();
        inspectClass(objectMapper,
                xPaths,
                new StringBuilder(),
                "Events",
                Events.class,
                1,
                false);

        xPaths.stream()
                .sorted()
                .distinct()
                .forEach(System.out::println);
    }

    private <T> void inspectClass(final ObjectMapper objectMapper,
                                  final List<String> xPaths,
                                  final StringBuilder xPathBuilder,
                                  final String name,
                                  final Class<T> clazz,
                                  final int depth,
                                  final boolean hasMultiple) throws Exception {

        if (depth <= 20) {
            final String padding = Strings.repeat(" ", (depth - 1) * 2);
//            final T object = clazz.getConstructor().newInstance();
            final JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
            final JAXBIntrospector jaxbIntrospector = jaxbContext.createJAXBIntrospector();
//            final String elementName = jaxbIntrospector.getElementName(object).getLocalPart();

            final JavaType userType = objectMapper.getTypeFactory().constructType(clazz);
            final BeanDescription beanDescription =
                    objectMapper.getSerializationConfig().introspect(userType);

            final String xPath = xPathBuilder.toString();
            if (xPath.endsWith(name)
                || xPath.endsWith(name + "[0]")
                || (xPath.endsWith("/Groups/Group[0]") && name.equals("Groups"))) {
                // Hack to stop infinite recursion
            } else {
                xPathBuilder.append("/")
                        .append(name);
                if (hasMultiple) {
                    xPathBuilder.append("[0]");
                }
                xPaths.add(xPathBuilder.toString());

                System.out.println(LogUtil.message("{}{}, depth: {}, xpath: {}", padding, name, depth, xPathBuilder));

                for (final BeanPropertyDefinition property : beanDescription.findProperties()) {
                    inspectProperty(objectMapper, xPaths, xPathBuilder, depth, property);
                }
            }
        }
    }

    private void inspectProperty(final ObjectMapper objectMapper,
                                 final List<String> xPaths,
                                 final StringBuilder xPathBuilder,
                                 final int depth,
                                 final BeanPropertyDefinition property) throws Exception {
        final String propName = property.getName();
        try {
            final AnnotatedField propField = property.getField();
            final XmlElement xmlElemAnno = propField.getAnnotation(XmlElement.class);
            final XmlAttribute xmlAttrAnno = propField.getAnnotation(XmlAttribute.class);
            final AnnotatedMethod setter = property.getSetter();
            final AnnotatedMethod getter = property.getGetter();
            final JavaType propType = property.getPrimaryType();
            final Class<?> propClass = propType.getRawClass();

            if (xmlElemAnno != null) {
                final String propElmName = xmlElemAnno.name();
                if (propClass.getName().startsWith("event.logging")
                    && !Void.class.equals(propClass)) {

                    inspectClass(objectMapper,
                            xPaths,
                            new StringBuilder(
                                    xPathBuilder),
                            propElmName,
                            propClass,
                            depth + 1,
                            false);
                } else if (List.class.isAssignableFrom(propClass)) {
                    final JavaType propSubType = propType.findTypeParameters(List.class)[0];
                    if (propSubType.getRawClass().getName().startsWith("event.logging")
                        && !Void.class.equals(propSubType.getRawClass())) {
                        inspectClass(objectMapper,
                                xPaths,
                                new StringBuilder(xPathBuilder),
                                propElmName,
                                propSubType.getRawClass(),
                                depth + 1,
                                true);
                    }
                } else {
                    // not recursing
                }
            } else if (xmlAttrAnno != null) {
                xPaths.add(xPathBuilder +
                           "/@" +
                           xmlAttrAnno.name());
            }
        } catch (final Exception e) {
            LOGGER.error(LogUtil.message("Unable to inspect prop {} at path {} - {}",
                    propName, xPathBuilder.toString(), e.getMessage()));
        }
    }

//    private <T> void inspectObject(final ObjectMapper objectMapper,
//                                   final List<String> xPaths,
//                                   final StringBuilder xPathBuilder,
//                                   final String name,
//                                   final T object,
//                                   final int depth) throws JAXBException {
//
//        if (depth <= 3) {
////            final ObjectInfo<T> objectInfo = PropertyUtil.getObjectInfo(
////                    objectMapper, name, object);
//            final String padding = Strings.repeat(" ", (depth - 1) * 2);
//            final Class<T> objType = (Class<T>) object.getClass();
//
//            final JAXBContext jaxbContext = JAXBContext.newInstance(objType);
//            final JAXBIntrospector jaxbIntrospector = jaxbContext.createJAXBIntrospector();
//            final String elementName = jaxbIntrospector.getElementName(object).getLocalPart();
//
//            xPathBuilder.append("/")
//                    .append(elementName);
//
//            System.out.println(LogUtil.message("{}{}", padding, elementName));
//
//            final List<Method> setters = Arrays.stream(objType.getMethods())
//                    .filter(method -> method.getName().startsWith("set"))
//                    .toList();
//
//            setters.forEach(setter -> {
//                final Class<?> setterType = setter.getParameterTypes()[0];
//                if (setterType.getName().startsWith("event.logging")) {
//                    // One of ours so recurse
//                    try {
//                        final Constructor<?> constructor = setterType.getConstructor();
//                        final Object setterValue = constructor.newInstance();
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    }
//                } else {
//
//                }
//
//
//            });
//
////            System.out.println(LogUtil.message("{}{} - {} - {}",
////                    padding,
////                    objectInfo.getName(),
////                    objectInfo.getObjectType(),
////                    elementName));
//
////            System.out.println(LogUtil.message("{}{} => {}", padding, name2, prop));
//
////        objectInfo.getPropertyMap().forEach((name2, prop) -> {
////                final Class<?> valueClass = prop.getValueClass();
////
////
////                final Method setter = prop.getSetter();
////                final Type paramType = setter.getGenericParameterTypes()[0];
////                paramType.getTypeName();
////                System.out.println(LogUtil.message("{}{} - type {}", name2, paramType));
//
//
////            });
//        }
//    }
}
