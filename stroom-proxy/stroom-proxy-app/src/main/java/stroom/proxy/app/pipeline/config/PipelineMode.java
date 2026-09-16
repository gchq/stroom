/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.config;

import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.store.FileStoreType;

/**
 * How the proxy is deployed. The mode is a property of the deployment, not of any one queue or
 * store: every queue and every store must fit it, and a mixture is a misconfiguration.
 */
public enum PipelineMode {
    /**
     * One node. Local directory queues and local file stores; in-flight work is recovered by the
     * same node restarting.
     */
    LOCAL,

    /**
     * Many nodes, any of which may be removed for ever at any time. Distributed queues and shared
     * file stores; any node may recover any in-flight work.
     */
    SHARED;

    public boolean accepts(final QueueType queueType) {
        return (queueType == QueueType.LOCAL_FILESYSTEM) == (this == LOCAL);
    }

    public boolean accepts(final FileStoreType fileStoreType) {
        return fileStoreType.isShared() == (this == SHARED);
    }

    /**
     * Whether a pipeline block states shared mode. Null-safe, because callers built before the
     * validator has run - the destination factories - must not fail on an absent mode; the
     * validator then refuses the boot for it.
     */
    public static boolean isShared(final ProxyPipelineConfig pipelineConfig) {
        return pipelineConfig != null && pipelineConfig.getMode() == SHARED;
    }
}
