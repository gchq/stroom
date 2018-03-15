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
import stroom.entity.StroomEntityManager;
import stroom.node.shared.ClientPropertiesService;

public class PropertyModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StroomPropertyService.class).to(StroomPropertyServiceImpl.class);
        bind(ClientPropertiesService.class).to(ClientPropertiesServiceImpl.class);
        bind(GlobalPropertyService.class).to(GlobalPropertyServiceImpl.class);
    }



//    @Bean
//    public GlobalPropertyUpdater globalPropertyUpdater(final GlobalPropertyService globalPropertyService) {
//        return new GlobalPropertyUpdater(globalPropertyService);
//    }
//
}