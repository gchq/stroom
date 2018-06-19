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

package stroom.streamstore.store;

import stroom.security.Security;
import stroom.data.meta.api.StreamMetaService;
import stroom.streamstore.shared.DeleteStreamAction;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.SharedInteger;

import javax.inject.Inject;

@TaskHandlerBean(task = DeleteStreamAction.class)
class DeleteStreamHandler extends AbstractTaskHandler<DeleteStreamAction, SharedInteger> {
    private final StreamMetaService streamMetaService;
    private final Security security;

    @Inject
    DeleteStreamHandler(final StreamMetaService streamMetaService,
                        final Security security) {
        this.streamMetaService = streamMetaService;
        this.security = security;
    }

    @Override
    public SharedInteger exec(final DeleteStreamAction task) {
        return security.secureResult(() -> {
            return new SharedInteger(streamMetaService.findDelete(task.getCriteria()));
        });
    }
}
