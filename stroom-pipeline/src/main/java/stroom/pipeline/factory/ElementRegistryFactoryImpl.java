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

import stroom.pipeline.factory.PipelineElementModule.ElementType;

import com.google.inject.Provider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public class ElementRegistryFactoryImpl implements ElementRegistryFactory, ElementFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElementRegistryFactoryImpl.class);

    private final Map<ElementType, Provider<Element>> elementMap;
    private volatile ElementRegistry registry;

    @Inject
    ElementRegistryFactoryImpl(final Map<ElementType, Provider<Element>> elementMap) {
        this.elementMap = elementMap;
    }

    /**
     * Implements an idiom to prevent double checked locking.
     */
    @Override
    public ElementRegistry get() {
        ElementRegistry result = registry;
        if (result == null) {
            synchronized (this) {
                result = registry;
                if (result == null) {
                    registry = result = create();
                }
            }
        }

        return result;
    }

    private ElementRegistry create() {
        final ElementRegistry registry;

        // Fudge a task scope context as many of the beans require one before
        // they can be created.
        LOGGER.info("Initialising pipeline element registry.");
        final List<Class<?>> elementClasses = new ArrayList<>();
        for (final ElementType elementType : elementMap.keySet()) {
            final Class<?> clazz = elementType.getElementClass();
            elementClasses.add(clazz);

            if (LOGGER.isDebugEnabled()) {
                final ConfigurableElement pipelineElement = clazz.getAnnotation(ConfigurableElement.class);
                if (pipelineElement != null) {
                    LOGGER.debug("Registering pipeline element " + pipelineElement.type());
                }
            }
        }

        registry = new ElementRegistry(elementClasses);
        LOGGER.info("Finished initialising pipeline element registry.");

        return registry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Element> T getElementInstance(final Class<T> elementClass) {
        try {
            final ElementType elementType = new ElementType(elementClass);
            final Provider<Element> elementProvider = elementMap.get(elementType);
            if (elementProvider == null) {
                LOGGER.error("No element provider found for " + elementClass);
                throw new RuntimeException("No element provider found for " + elementClass);
            }
            return (T) elementProvider.get();
        } catch (final RuntimeException rex) {
            LOGGER.error("Failed to load {}", elementClass, rex);
            throw rex;
        }
    }
}
