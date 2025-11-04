package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.security.mock.MockSecurityContext;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestDocRefInfoServiceImpl {

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

    private static final Map<String, DocRefInfo> CACHE_DATA = DOC_REFS.stream()
            .map(docRef -> new DocRefInfo(
                    docRef,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    "user1",
                    "user1",
                    null))
            .collect(Collectors.toMap(
                    docRefInfo -> docRefInfo.getDocRef().getUuid(),
                    Function.identity()));

    @Mock
    private DocRefInfoCache mockDocRefInfoCache;
    @Mock
    private ExplorerActionHandlers mockExplorerActionHandlers;

    private MockSecurityContext mockSecurityContext = new MockSecurityContext();

    DocRefInfoServiceImpl docRefInfoService;

    @BeforeEach
    void setUp() {
        docRefInfoService = new DocRefInfoServiceImpl(
                mockDocRefInfoCache,
                () -> mockSecurityContext,
                mockExplorerActionHandlers);
    }

    private void initMockCache() {
        Mockito
                .doAnswer(invocation -> {
                    final DocRef docRef = invocation.getArgument(0);
                    return Optional.ofNullable(CACHE_DATA.get(docRef.getUuid()));
                })
                .when(mockDocRefInfoCache).get(Mockito.any(DocRef.class));
    }

    @Test
    void decorate_list_null() {

        final List<DocRef> docRefs = docRefInfoService.decorate((List<DocRef>) null);

        assertThat(docRefs)
                .isEmpty();
    }

    @Test
    void decorate_list_empty() {

        final List<DocRef> docRefs = docRefInfoService.decorate(Collections.emptyList());

        assertThat(docRefs)
                .isEmpty();
    }

    @Test
    void decorate_list_oneItemUnchanged() {

        final List<DocRef> inputDocRefs = List.of(DOC_REF3);

        final List<DocRef> outputDocRefs = docRefInfoService.decorate(inputDocRefs);

        assertThat(outputDocRefs)
                .containsExactlyElementsOf(inputDocRefs);
    }


    @Test
    void decorate_list_twoDecorated() {

        initMockCache();

        final List<DocRef> inputDocRefs = List.of(
                DOC_REF1.withoutName(),
                DOC_REF2.withoutName(),
                DOC_REF3);

        final List<DocRef> outputDocRefs = docRefInfoService.decorate(inputDocRefs);

        assertThat(outputDocRefs)
                .hasSize(3);

        assertThat(outputDocRefs)
                .extracting(DocRef::getUuid)
                .containsExactlyElementsOf(DOC_REFS.stream()
                        .map(DocRef::getUuid)
                        .toList());

        assertThat(outputDocRefs)
                .extracting(DocRef::getType)
                .containsExactlyElementsOf(DOC_REFS.stream()
                        .map(DocRef::getType)
                        .toList());

        assertThat(outputDocRefs)
                .extracting(DocRef::getName)
                .containsExactlyElementsOf(DOC_REFS.stream()
                        .map(DocRef::getName)
                        .toList());
    }

    @Test
    void decorate_null() {

        Assertions
                .assertThatThrownBy(() -> {
                    final DocRef docRef = docRefInfoService.decorate((DocRef) null);
                })
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void decorate_unchanged() {

        final DocRef docRef = docRefInfoService.decorate(DOC_REF3);

        assertThat(docRef)
                .isEqualTo(DOC_REF3);
    }

    @Test
    void decorate_changed() {
        initMockCache();

        final DocRef docRef = docRefInfoService.decorate(DOC_REF3.withoutName());

        assertThat(docRef)
                .isEqualTo(DOC_REF3);
    }

    @Test
    void decorate_changed_force() {
        initMockCache();

        final DocRef docRef = docRefInfoService.decorate(DOC_REF3.withoutName(), true);

        assertThat(docRef)
                .isEqualTo(DOC_REF3);
    }

    @Test
    void decorate_outOfDateName_noForce() {
        final DocRef input = DOC_REF3.copy()
                .name(DOC_REF3.getName() + "XXX")
                .build();
        final DocRef docRef = docRefInfoService.decorate(input);

        assertThat(docRef.getName())
                .isEqualTo(input.getName());
    }

    @Test
    void decorate_outOfDateName_force() {
        initMockCache();

        final DocRef input = DOC_REF3.copy()
                .name(DOC_REF3.getName() + "XXX")
                .build();
        final DocRef docRef = docRefInfoService.decorate(input, true);

        assertThat(docRef.getName())
                .isEqualTo(DOC_REF3.getName());
    }
}
