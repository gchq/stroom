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

package stroom.dashboard.impl;

import stroom.dashboard.shared.DashboardDoc;
import stroom.docstore.api.DocumentStoreBinder;
import stroom.query.language.functions.FunctionFactory;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class DashboardModule extends AbstractModule {

    @Override
    protected void configure() {
        DocumentStoreBinder.create(binder())
                .bind(DashboardDoc.TYPE, DashboardStore.class, DashboardStoreImpl.class);

        bind(DashboardService.class).to(DashboardServiceImpl.class);
        bind(FunctionFactory.class).asEagerSingleton();

        RestResourcesBinder.create(binder())
                .bind(DashboardResourceImpl.class);

    }
}
