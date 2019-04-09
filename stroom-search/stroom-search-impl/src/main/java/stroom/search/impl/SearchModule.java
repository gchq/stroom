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

package stroom.search.impl;

import com.google.inject.AbstractModule;
import stroom.index.api.EventSearch;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.RestResource;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

public class SearchModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new SearchElementModule());

        bind(EventSearch.class).to(EventSearchImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(LuceneSearchResponseCreatorManager.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(StroomIndexQueryResourceImpl.class);

        TaskHandlerBinder.create(binder())
                .bind(AsyncSearchTask.class, AsyncSearchTaskHandler.class)
                .bind(ClusterSearchTask.class, ClusterSearchTaskHandler.class)
                .bind(EventSearchTask.class, EventSearchTaskHandler.class);
    }
}