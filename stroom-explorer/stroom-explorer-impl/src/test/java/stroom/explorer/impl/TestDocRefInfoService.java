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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.explorer.api.IsSpecialExplorerDataSource;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestDocRefInfoService {

    private static final DocRef DOC_REF1 = DocRef.builder()
            .randomUuid()
            .type("Type1")
            .name("Name1")
            .build();
    private static final DocRef DOC_REF2 = DocRef.builder()
            .randomUuid()
            .type("Type2")
            .name("Name2")
            .build();
    private static final DocRef DOC_REF3 = DocRef.builder()
            .randomUuid()
            .type("Type3")
            .name("Name3")
            .build();

    private static final List<DocRef> DOC_REFS = List.of(
            DOC_REF1,
            DOC_REF2,
            DOC_REF3);

    private static final Map<DocRef, DocRef> CACHE_DATA = DOC_REFS.stream()
            .collect(Collectors.toMap(Function.identity(), Function.identity()));

    @Mock
    private Set<IsSpecialExplorerDataSource> mockSpecialDataSources;
    @Mock
    private DocFinder docFinder;

    private final MockSecurityContext mockSecurityContext = new MockSecurityContext();

    DocRefInfoService docRefInfoService;

    @BeforeEach
    void setUp() {
        docRefInfoService = new DocRefInfoService(
                () -> mockSecurityContext,
                null,
                mockSpecialDataSources,
                docFinder);
    }

    private void initMockCache() {
        Mockito
                .doAnswer(invocation -> {
                    final DocRef docRef = invocation.getArgument(0);
                    return Optional.ofNullable(CACHE_DATA.get(docRef));
                })
                .when(docFinder).decorate(Mockito.any(DocRef.class));
        Mockito
                .doAnswer(invocation -> {
                    final DocRef docRef = invocation.getArgument(0);
                    return Optional.ofNullable(CACHE_DATA.get(docRef));
                })
                .when(docFinder).decorateIfExists(Mockito.any(DocRef.class));
    }

    private void initSearchables() {
        Mockito.doAnswer(invocation ->
                        Stream.of(new MySpecialDataSource1(), new MySpecialDataSource2()))
                .when(mockSpecialDataSources).stream();
    }

    @Test
    void decorate_null() {
        assertThat(docRefInfoService.decorate((DocRef) null)).isEmpty();
    }

    @Test
    void decorate_unchanged() {
        final Optional<DocRef> docRef = docRefInfoService.decorate(DOC_REF3);
        assertThat(docRef.orElse(DOC_REF3)).isEqualTo(DOC_REF3);
    }

    @Test
    void decorate_changed() {
        initMockCache();
        final Optional<DocRef> docRef = docRefInfoService.decorate(DOC_REF3.withoutName());
        assertThat(docRef.orElseThrow()).isEqualTo(DOC_REF3);
    }

    @Test
    void decorate_changed_force() {
        initMockCache();
        final Optional<DocRef> docRef = docRefInfoService.decorate(DOC_REF3.withoutName());
        assertThat(docRef.orElseThrow()).isEqualTo(DOC_REF3);
    }

    @Test
    void decorate_outOfDateName_noForce() {
        final DocRef input = DOC_REF3.copy()
                .name(DOC_REF3.getName() + "XXX")
                .build();
        final Optional<DocRef> result = docRefInfoService.decorate(input);
        assertThat(result.orElse(input).getName()).isEqualTo(input.getName());
    }

    @Test
    void decorate_outOfDateName_force() {
        initMockCache();

        final DocRef input = DOC_REF3.copy()
                .name(DOC_REF3.getName() + "XXX")
                .build();
        final Optional<DocRef> docRef = docRefInfoService.decorate(input);
        assertThat(docRef.orElseThrow().getName())
                .isEqualTo(DOC_REF3.getName());
    }

    @Test
    void decorateSearchable() {
        initMockCache();
        initSearchables();

        final DocRef input = MySpecialDataSource1.DUAL_DOC_REF.copy()
                .name(MySpecialDataSource1.DUAL_DOC_REF.getName() + "XXX")
                .build();
        final Optional<DocRef> docRef = docRefInfoService.decorate(input);
        assertThat(docRef.orElseThrow().getName())
                .isEqualTo(MySpecialDataSource1.DUAL_DOC_REF.getName());
    }

//    @Test
//    void findByType() {
//        initSearchables();
//        final List<DocRef> docRefs = docRefInfoService.findByType(MySpecialDataSource1.TYPE);
//        assertThat(docRefs)
//                .containsExactly(MySpecialDataSource1.DUAL_DOC_REF);
//    }


    // --------------------------------------------------------------------------------


    private static class MySpecialDataSource1 implements IsSpecialExplorerDataSource {

        public static final String TYPE = "Dual";
        public static final DocRef DUAL_DOC_REF = DocRef.builder()
                .type(TYPE)
                .randomUuid()
                .name(TYPE)
                .build();

        @Override
        public List<DocRef> getDataSourceDocRefs() {
            return List.of(DUAL_DOC_REF);
        }
    }


    // --------------------------------------------------------------------------------


    private static class MySpecialDataSource2 implements IsSpecialExplorerDataSource {

        public static final String TYPE = "Tasks";
        public static final DocRef TASKS_DOC_REF = DocRef.builder()
                .type(TYPE)
                .randomUuid()
                .name(TYPE)
                .build();

        @Override
        public List<DocRef> getDataSourceDocRefs() {
            return List.of(TASKS_DOC_REF);
        }
    }

}
