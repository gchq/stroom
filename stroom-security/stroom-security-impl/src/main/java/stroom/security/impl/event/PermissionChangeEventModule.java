/*
 * Copyright 2018 Crown Copyright
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

package stroom.security.impl.event;

import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class PermissionChangeEventModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(PermissionChangeEventBus.class).to(PermissionChangeEventBusImpl.class);
        bind(PermissionChangeResource.class).to(PermissionChangeResourceImpl.class);

        // Ensure the multibinder is created.
        Multibinder.newSetBinder(binder(), PermissionChangeEvent.Handler.class);

        RestResourcesBinder.create(binder())
                .bind(PermissionChangeResourceImpl.class);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}