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

package stroom.data.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.meta.shared.MetaResource;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;

import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DataTypeUiManager {

    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);

    private final RestFactory restFactory;

    @Inject
    public DataTypeUiManager(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void getTypes(final Consumer<List<String>> consumer,
                         final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(META_RESOURCE)
                .method(MetaResource::getTypes)
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
