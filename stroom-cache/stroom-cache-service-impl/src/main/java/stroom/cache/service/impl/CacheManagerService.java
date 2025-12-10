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

package stroom.cache.service.impl;

import stroom.util.shared.cache.CacheIdentity;
import stroom.util.shared.cache.CacheInfo;

import java.util.List;

/**
 * This class maintains several caches used throughout the application.
 */
public interface CacheManagerService {

    List<String> getCacheNames();

    List<CacheIdentity> getCacheIdentities();

    List<CacheInfo> find(FindCacheInfoCriteria criteria);

    Long clear(FindCacheInfoCriteria criteria);

    void evictExpiredElements();

    Long evictExpiredElements(FindCacheInfoCriteria criteria);
}
