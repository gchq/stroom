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

package stroom.properties;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.FindService;
import stroom.node.shared.ClientPropertiesService;
import stroom.node.shared.GlobalProperty;

public class PropertyModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StroomPropertyService.class).to(StroomPropertyServiceImpl.class);
        bind(ClientPropertiesService.class).to(ClientPropertiesServiceImpl.class);
        bind(GlobalPropertyService.class).to(GlobalPropertyServiceImpl.class);

        final MapBinder<String, Object> entityServiceByTypeBinder = MapBinder.newMapBinder(binder(), String.class, Object.class);
        entityServiceByTypeBinder.addBinding(GlobalProperty.ENTITY_TYPE).to(GlobalPropertyServiceImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(GlobalPropertyServiceImpl.class);
    }
}