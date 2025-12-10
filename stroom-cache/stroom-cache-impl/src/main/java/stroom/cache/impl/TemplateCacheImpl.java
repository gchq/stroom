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

package stroom.cache.impl;


import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.cache.api.TemplateCache;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.NullSafe;
import stroom.util.string.TemplateUtil;
import stroom.util.string.TemplateUtil.Template;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class TemplateCacheImpl implements TemplateCache {

    public static final String CACHE_NAME = "Template Cache";

    /**
     * Template {@link String} => {@link Template}
     */
    private final LoadingStroomCache<String, Template> cache;

    @Inject
    public TemplateCacheImpl(final CacheManager cacheManager) {
        // TODO config ought to come from config somewhere
        this.cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> CacheConfig.builder()
                        .maximumSize(1_000)
                        .build(),
                TemplateUtil::parseTemplate);
    }

    @Override
    public Template getTemplate(final String template) {
        // Not much point caching the empty instance
        if (NullSafe.isEmptyString(template)) {
            return Template.EMPTY_TEMPLATE;
        } else {
            return cache.get(template);
        }
    }

    @Override
    public void evict(final String template) {
        if (NullSafe.isNonEmptyString(template)) {
            cache.remove(template);
        }
    }

    @Override
    public String toString() {
        return cache.name() + ": " + cache.size();
    }
}
