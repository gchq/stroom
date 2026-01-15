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

package stroom.pipeline.factory;

import stroom.docref.DocRef;
import stroom.docref.HasType;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ElementRegistry {

    private static final String SOURCE = "Source";
    private static final PipelineElementType SOURCE_ELEMENT_TYPE = new PipelineElementType(
            SOURCE,
            SOURCE,
            null,
            new String[]{
                    PipelineElementType.ROLE_SOURCE,
                    PipelineElementType.ROLE_HAS_TARGETS,
                    PipelineElementType.VISABILITY_SIMPLE},
            SvgImage.PIPELINE_STREAM);

    private final Map<String, Class<Element>> elementMap = new HashMap<>();
    private final Map<String, Map<String, Method>> propertyMap = new HashMap<>();
    private final Map<PipelineElementType, Map<String, PipelinePropertyType>> propertyTypes = new HashMap<>();
    private final Map<String, PipelineElementType> elementTypes = new HashMap<>();

    @SuppressWarnings("unchecked")
    ElementRegistry(final Collection<Class<?>> elementClasses) {
        // Make sure source exists.
        elementTypes.put(SOURCE, SOURCE_ELEMENT_TYPE);
        propertyMap.put(SOURCE, Collections.emptyMap());
        propertyTypes.put(SOURCE_ELEMENT_TYPE, Collections.emptyMap());

        for (final Class<?> elementClass : elementClasses) {
            final ConfigurableElement configurableElement = elementClass.getAnnotation(ConfigurableElement.class);
            // Not all elements are configurable so won't necessarily have an annotation.
            if (configurableElement != null) {
                if (!Element.class.isAssignableFrom(elementClass)) {
                    throw new PipelineFactoryException(
                            "Class " + elementClass.getName() + " is not an instance of PipelineElement");
                }

                final String elementTypeName = configurableElement.type();
                final Class<Element> clazz = (Class<Element>) elementClass;
                final Class<Element> existing = elementMap.put(elementTypeName, clazz);

                // Check that there isn't a factory already associated with the
                // name.
                if (existing != null) {
                    throw new PipelineFactoryException("PipelineElement \"" + existing +
                                                       "\" has already been registered for \"" +
                                                       elementTypeName + "\"");
                }

                registerProperties(clazz, configurableElement);
            }
        }
    }

    private void registerProperties(final Class<Element> elementClass, final ConfigurableElement configurableElement) {
        final String elementTypeName = configurableElement.type();

        // Remember this element and associated properties for UI purposes.
        final PipelineElementType elementType = createElementType(configurableElement);
        final Map<String, PipelinePropertyType> properties = new HashMap<>();

        // Register available properties.
        final Method[] methods = elementClass.getMethods();
        for (final Method method : methods) {
            final PipelineProperty property = method.getAnnotation(PipelineProperty.class);
            final PipelinePropertyDocRef docRefProperty = method.getAnnotation(PipelinePropertyDocRef.class);
            if (property != null) {
                String name = method.getName();
                if (!name.startsWith("set") || name.length() <= 3) {
                    throw new PipelineFactoryException("PipelineProperty \"" + name + "\" on \"" + elementTypeName +
                                                       "\" must start with 'set'");
                }

                // Convert the setter to a camel case property name.
                name = makePropertyName(name);

                final Class<?>[] parameters = method.getParameterTypes();

                if (parameters.length != 1) {
                    throw new PipelineFactoryException("PipelineProperty \"" + name + "\" on \"" + elementTypeName +
                                                       "\" must have only 1 parameter");
                }

                final Map<String, Method> map = propertyMap.computeIfAbsent(elementTypeName, k -> new HashMap<>());

                final Class<?> clazz = parameters[0];
                // Make sure the class type is one we know how to deal with.

                if (!String.class.isAssignableFrom(clazz) &&
                    !boolean.class.equals(clazz) &&
                    !int.class.equals(clazz) &&
                    !long.class.equals(clazz) &&
                    !DocRef.class.isAssignableFrom(clazz) &&
                    !PipelineReference.class.isAssignableFrom(clazz)) {
                    throw new PipelineFactoryException("PipelineProperty \"" +
                                                       name +
                                                       "\" on \"" +
                                                       elementTypeName +
                                                       "\" has an unexpected type of \"" +
                                                       clazz.getName() +
                                                       "\"");
                }

                if (DocRef.class.isAssignableFrom(clazz)) {
                    if (null == docRefProperty) {
                        throw new PipelineFactoryException("PipelineProperty \"" +
                                                           name +
                                                           "\" on \"" +
                                                           elementTypeName +
                                                           "\" is a DocRef, so also needs \"" +
                                                           PipelinePropertyDocRef.class.getName() +
                                                           "\"");
                    }
                }

                final Method existing = map.put(name, method);
                if (existing != null) {
                    throw new PipelineFactoryException(
                            "PipelineProperty \"" + name + "\" on \"" + elementTypeName + "\" already exists");
                }

                // Remember this element and associated properties for UI
                // purposes.
                final PipelinePropertyType propertyType = createPropertyType(elementType,
                        method,
                        property,
                        docRefProperty);
                properties.put(propertyType.getName(), propertyType);
            }

            elementTypes.put(elementTypeName, elementType);
            propertyTypes.put(elementType, properties);
        }
    }

    private PipelineElementType createElementType(final ConfigurableElement element) {
        String displayValue = element.displayValue();
        if (NullSafe.isBlankString(displayValue)) {
            displayValue = element.type().replaceAll("([A-Z][a-z])", " $1");
        }
        return new PipelineElementType(
                element.type(),
                displayValue,
                element.category(),
                element.roles(),
                element.icon());
    }

    private PipelinePropertyType createPropertyType(final PipelineElementType elementType,
                                                    final Method method,
                                                    final PipelineProperty property,
                                                    final PipelinePropertyDocRef docRefProperty) {
        // Convert the setter to a camel case property name.
        final String propertyName = makePropertyName(method.getName());
        final Class<?> paramType = method.getParameterTypes()[0];
        String typeName = paramType.getSimpleName();

        if (HasType.class.isAssignableFrom(paramType) && !DocRef.class.isAssignableFrom(paramType)) {
            try {
                typeName = ((HasType) paramType.getDeclaredConstructor(new Class[0]).newInstance()).getType();
            } catch (final InstantiationException
                           | IllegalAccessException
                           | NoSuchMethodException
                           | InvocationTargetException e) {
                throw new PipelineFactoryException("Unable to create global property for type " +
                                                   typeName + " (reason: " + e.getMessage() + ")");
            }
        }

        return PipelinePropertyType.builder()
                .elementType(elementType)
                .name(propertyName)
                .type(typeName)
                .description(property.description())
                .defaultValue(property.defaultValue())
                .displayPriority(property.displayPriority())
                .pipelineReference(paramType.equals(PipelineReference.class))
                .docRefTypes((docRefProperty != null)
                        ? docRefProperty.types()
                        : null)
                .build();
    }

    private String makePropertyName(final String methodName) {
        // Convert the setter to a camel case property name.
        String name = methodName.substring(3);
        name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        return name;
    }

    public Method getMethod(final String elementType, final String name) {
        final Map<String, Method> map = propertyMap.get(elementType);
        if (map != null) {
            return map.get(name);
        }

        return null;
    }

    public Map<PipelineElementType, Map<String, PipelinePropertyType>> getPropertyTypes() {
        return propertyTypes;
    }

    public PipelineElementType getElementType(final String typeName) {
        return elementTypes.get(typeName);
    }

    public PipelinePropertyType getPropertyType(final PipelineElementType element, final String propertyName) {
        final Map<String, PipelinePropertyType> properties = propertyTypes.get(element);
        if (properties == null) {
            return null;
        }

        return properties.get(propertyName);
    }

    public Class<Element> getElementClass(final String elementType) {
        return elementMap.get(elementType);
    }
}
