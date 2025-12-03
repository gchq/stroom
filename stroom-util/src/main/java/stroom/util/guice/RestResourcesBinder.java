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

package stroom.util.guice;

import stroom.util.shared.RestResource;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

import java.util.Objects;

public final class RestResourcesBinder {

    private final MapBinder<ResourceType, RestResource> mapBinder;

    private RestResourcesBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, ResourceType.class, RestResource.class);
    }

    public static RestResourcesBinder create(final Binder binder) {
        return new RestResourcesBinder(binder);
    }

    public <T extends RestResource> RestResourcesBinder bind(final Class<T> resourceClass) {
        mapBinder.addBinding(new ResourceType(resourceClass)).to(resourceClass);
        return this;
    }

    public static class ResourceType {

        private final Class<?> resourceClass;

        <T extends RestResource> ResourceType(final Class<T> resourceClass) {
            this.resourceClass = resourceClass;
        }

        public Class<?> getResourceClass() {
            return resourceClass;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ResourceType that = (ResourceType) o;
            return Objects.equals(resourceClass, that.resourceClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resourceClass);
        }
    }
}
