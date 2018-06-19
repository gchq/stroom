/*
 * Copyright 2017 Crown Copyright
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

package stroom.data.store;

import stroom.entity.shared.DataSourceFields;
import stroom.security.Security;
import stroom.streamstore.shared.FetchFieldsAction;
import stroom.data.meta.api.StreamDataSource;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchFieldsAction.class)
class FetchFieldsHandler extends AbstractTaskHandler<FetchFieldsAction, DataSourceFields> {
    private final Security security;

    @Inject
    FetchFieldsHandler(final Security security) {
        this.security = security;
    }

    @Override
    public DataSourceFields exec(final FetchFieldsAction task) {
        return security.secureResult(() -> new DataSourceFields(StreamDataSource.getFields()));
    }
}
