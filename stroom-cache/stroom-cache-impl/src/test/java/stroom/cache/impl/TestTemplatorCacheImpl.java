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
import stroom.util.string.TemplateUtil.Templator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTemplatorCacheImpl {

    @Test
    void test() {
        try (final CacheManager cacheManager = new CacheManagerImpl()) {
            final TemplatorCacheImpl templatorCache = new TemplatorCacheImpl(cacheManager);

            final Templator template1 = templatorCache.getTemplator("foo");
            final String output1 = template1.buildGenerator()
                    .generate();
            assertThat(output1)
                    .isEqualTo("foo");

            final Templator template2 = templatorCache.getTemplator("foo ${xxx}");
            final String output2 = template2.buildGenerator()
                    .addReplacement("xxx", "bar")
                    .generate();
            assertThat(output2)
                    .isEqualTo("foo bar");

            final Templator template3 = templatorCache.getTemplator("foo");
            assertThat(template3)
                    .isSameAs(template1);

            final Templator template4 = templatorCache.getTemplator("foo ${xxx}");
            assertThat(template4)
                    .isSameAs(template2);
        }
    }

    @Test
    void evict() {
        try (final CacheManager cacheManager = new CacheManagerImpl()) {
            final TemplatorCacheImpl templatorCache = new TemplatorCacheImpl(cacheManager);

            final Templator template1 = templatorCache.getTemplator("foo");
            final String output1 = template1.buildGenerator()
                    .generate();
            assertThat(output1)
                    .isEqualTo("foo");

            final Templator template3 = templatorCache.getTemplator("foo");
            assertThat(template3)
                    .isSameAs(template1);

            templatorCache.evict("foo");

            final Templator template4 = templatorCache.getTemplator("foo");
            assertThat(template4)
                    .isNotSameAs(template1);
        }
    }
}
