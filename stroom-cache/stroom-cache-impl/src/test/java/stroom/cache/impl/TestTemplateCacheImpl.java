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
import stroom.util.string.TemplateUtil.Template;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTemplateCacheImpl {

    @Test
    void test() {
        try (final CacheManager cacheManager = new CacheManagerImpl()) {
            final TemplateCacheImpl templatorCache = new TemplateCacheImpl(cacheManager);

            final Template template1 = templatorCache.getTemplate("foo");
            final String output1 = template1.buildExecutor()
                    .execute();
            assertThat(output1)
                    .isEqualTo("foo");

            final Template template2 = templatorCache.getTemplate("foo ${xxx}");
            final String output2 = template2.buildExecutor()
                    .addReplacement("xxx", "bar")
                    .execute();
            assertThat(output2)
                    .isEqualTo("foo bar");

            final Template template3 = templatorCache.getTemplate("foo");
            assertThat(template3)
                    .isSameAs(template1);

            final Template template4 = templatorCache.getTemplate("foo ${xxx}");
            assertThat(template4)
                    .isSameAs(template2);
        }
    }

    @Test
    void evict() {
        try (final CacheManager cacheManager = new CacheManagerImpl()) {
            final TemplateCacheImpl templatorCache = new TemplateCacheImpl(cacheManager);

            final Template template1 = templatorCache.getTemplate("foo");
            final String output1 = template1.buildExecutor()
                    .execute();
            assertThat(output1)
                    .isEqualTo("foo");

            final Template template3 = templatorCache.getTemplate("foo");
            assertThat(template3)
                    .isSameAs(template1);

            templatorCache.evict("foo");

            final Template template4 = templatorCache.getTemplate("foo");
            assertThat(template4)
                    .isNotSameAs(template1);
        }
    }
}
