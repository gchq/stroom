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

package stroom.datasource;

import com.google.inject.AbstractModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.properties.StroomPropertyService;
import stroom.security.SecurityContext;
import stroom.servlet.HttpServletRequestHolder;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;

public class DatasourceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DataSourceProviderRegistry.class).to(DataSourceProviderRegistryImpl.class);
    }
}