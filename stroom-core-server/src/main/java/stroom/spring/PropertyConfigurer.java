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

package stroom.spring;

import stroom.node.server.StroomPropertyService;
import stroom.util.spring.PropertyProvider;

import javax.inject.Inject;

/**
 * Our own PropertyPlaceholderConfigurer that provides properties to Spring
 * beans via XML configuration or value injection. This bean just defers
 * property resolution to the <code>StroomPropertyService</code> bean that in turn
 * defers property resolution to <code>StroomProperties</code>
 */
public class PropertyConfigurer implements PropertyProvider {
    private final StroomPropertyService stroomPropertyService;

    @Inject
    PropertyConfigurer(final StroomPropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;
    }

    @Override
    public String getProperty(final String name) {
        return stroomPropertyService.getProperty(name);
    }
}
