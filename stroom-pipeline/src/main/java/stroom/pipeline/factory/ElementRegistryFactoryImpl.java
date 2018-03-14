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

package stroom.pipeline.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.guice.StroomBeanStore;
import stroom.util.task.TaskScopeContextHolder;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Singleton
public class ElementRegistryFactoryImpl implements ElementRegistryFactory, ElementFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElementRegistryFactoryImpl.class);

    private final StroomBeanStore beanStore;
    private volatile ElementRegistry registry;

    @Inject
    ElementRegistryFactoryImpl(final StroomBeanStore beanStore) {
        this.beanStore = beanStore;
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
        ElementRegistry registry;

        // Fudge a task scope context as many of the beans require one before
        // they can be created.
        TaskScopeContextHolder.addContext(null);
        try {
            LOGGER.info("Initialising pipeline element registry.");
            final Set<Element> elements = beanStore.getBeansOfType(Element.class);
            final List<Class<?>> elementClasses = new ArrayList<>();
            for (final Element element : elements) {
                final Class<?> clazz = element.getClass();
                elementClasses.add(clazz);

                if (LOGGER.isDebugEnabled()) {
                    final ConfigurableElement pipelineElement = clazz.getAnnotation(ConfigurableElement.class);
                    LOGGER.debug("Registering pipeline element " + pipelineElement.type());
                }
            }

            registry = new ElementRegistry(elementClasses);
            LOGGER.info("Finished initialising pipeline element registry.");
        } finally {
            TaskScopeContextHolder.removeContext();
        }

        return registry;
    }

    @Override
    public <T extends Element> T getElementInstance(final Class<T> elementClass) {
        try {
            return beanStore.getBean(elementClass);
        } catch (final RuntimeException rex) {
            LOGGER.error("Failed to load {}", elementClass, rex);
            throw rex;
        }
    }
}
