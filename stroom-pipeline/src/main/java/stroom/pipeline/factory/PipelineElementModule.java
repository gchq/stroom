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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

import java.util.Objects;

public abstract class PipelineElementModule extends AbstractModule {

    private MapBinder<ElementType, Element> mapBinder;

    @Override
    protected void configure() {
        mapBinder = MapBinder.newMapBinder(binder(), ElementType.class, Element.class);
        configureElements();
    }

    /**
     * Override this method to call {@link #bindElement}.
     */
    protected abstract void configureElements();

    protected <T extends Element> void bindElement(final Class<T> elementClass) {
        mapBinder.addBinding(new ElementType(elementClass)).to(elementClass);
    }

    public static class ElementType {

        private final Class<?> elementClass;

        <T extends Element> ElementType(final Class<T> elementClass) {
            this.elementClass = elementClass;
        }

        public Class<?> getElementClass() {
            return elementClass;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ElementType that = (ElementType) o;
            return Objects.equals(elementClass, that.elementClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(elementClass);
        }
    }
}
