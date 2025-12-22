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

package stroom.query.common.v2;

import stroom.query.api.TimeFilter;

public class DeleteCommand implements LmdbQueueItem {

    private final Key parentKey;
    private final TimeFilter timeFilter;

    public DeleteCommand(final Key parentKey, final TimeFilter timeFilter) {
        this.parentKey = parentKey;
        this.timeFilter = timeFilter;
    }

    public Key getParentKey() {
        return parentKey;
    }

    public TimeFilter getTimeFilter() {
        return timeFilter;
    }
}
