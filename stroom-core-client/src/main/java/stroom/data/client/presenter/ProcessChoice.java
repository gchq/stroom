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

public class ProcessChoice {

    private final int priority;
    private final boolean autoPriority;
    private final boolean reprocess;
    private final boolean enabled;
    private final Long minMetaCreateTimeMs;
    private final Long maxMetaCreateTimeMs;

    public ProcessChoice(final int priority,
                         final boolean autoPriority,
                         final boolean reprocess,
                         final boolean enabled,
                         final Long minMetaCreateTimeMs,
                         final Long maxMetaCreateTimeMs) {
        this.priority = priority;
        this.autoPriority = autoPriority;
        this.reprocess = reprocess;
        this.enabled = enabled;
        this.minMetaCreateTimeMs = minMetaCreateTimeMs;
        this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isAutoPriority() {
        return autoPriority;
    }

    public boolean isReprocess() {
        return reprocess;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs;
    }

    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs;
    }
}
