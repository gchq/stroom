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

package stroom.cache.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;


class CacheClearClusterHandler extends AbstractTaskHandler<CacheClearClusterTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheClearClusterHandler.class);

    private final CacheManagerService stroomCacheManager;
    private final Security security;

    @Inject
    CacheClearClusterHandler(final CacheManagerService stroomCacheManager,
                             final Security security) {
        this.stroomCacheManager = stroomCacheManager;
        this.security = security;
    }

    @SuppressWarnings({"rawtypes"})
    @Override
    public VoidResult exec(final CacheClearClusterTask task) {
        return security.secureResult(() -> {
            try {
                if (task == null) {
                    throw new RuntimeException("No task supplied");
                }
                stroomCacheManager.findClear(task.getCriteria());

            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }

            return new VoidResult();
        });
    }
}
