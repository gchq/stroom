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

package stroom.data.store.impl;

import stroom.data.store.api.DataService;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.guice.ServletBinder;

import com.google.inject.AbstractModule;

public class DataStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DataService.class)
                .to(DataServiceImpl.class);

        ServletBinder.create(binder())
                .bind(ImportFileServlet.class)
                .bind(AutoImport.class);

        RestResourcesBinder.create(binder())
                .bind(DataResourceImpl.class)
                .bind(DataDownloadResourceImpl.class);
    }
}
