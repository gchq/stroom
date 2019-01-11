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
 *
 */

package stroom.index;

import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;


class DeleteIndexShardClusterHandler extends AbstractTaskHandler<DeleteIndexShardClusterTask<?>, VoidResult> {
    private final IndexShardManager indexShardManager;
    private final Security security;

    @Inject
    DeleteIndexShardClusterHandler(final IndexShardManager indexShardManager,
                                   final Security security) {
        this.indexShardManager = indexShardManager;
        this.security = security;
    }

    @Override
    public VoidResult exec(final DeleteIndexShardClusterTask<?> task) {
        return security.secureResult(() -> {
            if (task == null) {
                throw new RuntimeException("No task supplied");
            }
            indexShardManager.findDelete(task.getCriteria());
            return new VoidResult();
        });
    }
}
