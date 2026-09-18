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

package stroom.config.global.impl;

import stroom.quickfilter.shared.QuickFilterHistoryKey;

import java.util.List;

public interface QuickFilterHistoryDao {

    /**
     * @return {@code userUuid}'s filters for {@code key}, most recently used first, at most
     * {@code limit}
     */
    List<String> fetch(String userUuid, QuickFilterHistoryKey key, int limit);

    /**
     * Insert, or if the filter is already there bump it to the top, then forget everything
     * beyond the {@code limit} most recent.
     */
    void recordUse(String userUuid, QuickFilterHistoryKey key, String filterText, long nowMs, int limit);

    int clear(String userUuid, QuickFilterHistoryKey key);

    int deleteAll(String userUuid);
}
