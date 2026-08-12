/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.docstore.impl;

import stroom.cache.impl.CacheManagerImpl;
import stroom.docref.DocRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestDocRefToNameCache {

    public static final String TYPE_FOO = "foo";
    public static final String TYPE_BAR = "bar";
    public static final DocRef DOC_REF_1 = DocRef.builder()
            .randomUuid()
            .type(TYPE_FOO)
            .name("test1")
            .build();
    public static final DocRef DOC_REF_2 = DocRef.builder()
            .randomUuid()
            .type(TYPE_FOO)
            .name("test2")
            .build();
    public static final DocRef DOC_REF_3 = DocRef.builder()
            .randomUuid()
            .type(TYPE_BAR)
            .name("test3")
            .build();
    public static final DocRef DOC_REF_4 = DocRef.builder()
            .randomUuid()
            .type(TYPE_BAR)
            .name("test4")
            .build();

    final CacheManagerImpl cacheManager = new CacheManagerImpl();
    DocRefToNameCache docRefToNameCache;

    @Mock
    private Persistence docFinder;

    @BeforeEach
    void setUp() {
        // Mock Persistence.readInfo() to return DocRefInfo for known doc refs
        Mockito.when(docFinder.getName(Mockito.any()))
                .thenAnswer(invocation -> {
                    final DocRef request = invocation.getArgument(0);
                    // Check known docs
                    for (final DocRef docRef : List.of(DOC_REF_1, DOC_REF_2, DOC_REF_3, DOC_REF_4)) {
                        if (request.equals(docRef)) {
                            return Optional.ofNullable(docRef.getName());
                        }
                    }
                    return Optional.empty();
                });

        docRefToNameCache = new DocRefToNameCache(
                cacheManager,
                DocStoreConfig::new,
                docFinder);
    }

    @Test
    void testGet() {
        final DocRef docRef = DOC_REF_1;
        final Optional<String> name = docRefToNameCache.getName(docRef);
        assertThat(name).isNotEmpty();
        assertThat(name.get()).isEqualTo(docRef.getName());
    }

    @Test
    void testGet_noName() {
        final DocRef docRef = DOC_REF_1;
        final Optional<String> docRefInfo = docRefToNameCache.getName(docRef.withoutName());
        assertThat(docRefInfo).isNotEmpty();
        assertThat(docRefInfo.get()).isEqualTo(docRef.getName());
    }
}
