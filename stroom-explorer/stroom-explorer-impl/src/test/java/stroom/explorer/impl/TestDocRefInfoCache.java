package stroom.explorer.impl;

import stroom.cache.impl.CacheManagerImpl;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentActionHandlers;
import stroom.docstore.api.DocumentType;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.NullSafe;
import stroom.util.shared.Document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocRefInfoCache {

    public static final String TYPE_FOO = "foo";
    public static final String TYPE_BAR = "bar";
    public static final DocRef DOC_REF_1 = DocRef.builder()
            .randomUuid()
            .type(TYPE_FOO)
            .build();
    public static final DocRef DOC_REF_2 = DocRef.builder()
            .randomUuid()
            .type(TYPE_FOO)
            .build();
    public static final DocRef DOC_REF_3 = DocRef.builder()
            .randomUuid()
            .type(TYPE_BAR)
            .build();
    public static final DocRef DOC_REF_4 = DocRef.builder()
            .randomUuid()
            .type(TYPE_BAR)
            .build();

    final CacheManagerImpl cacheManager = new CacheManagerImpl();
    final SecurityContext securityContext = new MockSecurityContext();
    DocRefInfoCache docRefInfoCache;

    @BeforeEach
    void setUp() {
        final DocumentActionHandlers documentActionHandlers = new DocumentActionHandlers(
                Map.of(
                        new DocumentType(TYPE_FOO), new MyDocumentActionHandler(Set.of(DOC_REF_1, DOC_REF_2)),
                        new DocumentType(TYPE_BAR), new MyDocumentActionHandler(Set.of(DOC_REF_3, DOC_REF_4))));

        docRefInfoCache = new DocRefInfoCache(
                cacheManager,
                ExplorerConfig::new,
                securityContext,
                () -> documentActionHandlers);
    }

    @Test
    void testGet() {
        final DocRef docRef = DOC_REF_1;
        final Optional<DocRefInfo> docRefInfo = docRefInfoCache.get(docRef);

        assertThat(docRefInfo)
                .isNotEmpty();
        assertThat(docRefInfo.get().getDocRef())
                .isEqualTo(docRef);
        assertThat(docRefInfoCache.get(docRef.getUuid()))
                .isEqualTo(docRefInfoCache.get(docRef));
    }

    @Test
    void testGet_noType() {
        DocRef docRef = DOC_REF_1;
        Optional<DocRefInfo> docRefInfo = docRefInfoCache.get(stripType(docRef));

        assertThat(docRefInfo)
                .isNotEmpty();
        assertThat(docRefInfo.get().getDocRef())
                .isEqualTo(docRef);
        assertThat(docRefInfoCache.get(docRef.getUuid()))
                .isEqualTo(docRefInfoCache.get(docRef));

        docRef = DOC_REF_4;
        docRefInfo = docRefInfoCache.get(stripType(docRef));

        assertThat(docRefInfo)
                .isNotEmpty();
        assertThat(docRefInfo.get().getDocRef())
                .isEqualTo(docRef);
        assertThat(docRefInfoCache.get(docRef.getUuid()))
                .isEqualTo(docRefInfoCache.get(docRef));
    }

    @Test
    void testGet_noName() {
        DocRef docRef = DOC_REF_1;
        final Optional<DocRefInfo> docRefInfo = docRefInfoCache.get(docRef.withoutName());

        assertThat(docRefInfo)
                .isNotEmpty();
        assertThat(docRefInfo.get().getDocRef())
                .isEqualTo(docRef);
    }

    private static DocRef stripType(final DocRef docRef) {
        return docRef.copy()
                .type(null)
                .build();
    }


    // --------------------------------------------------------------------------------


    private static class MyDocumentActionHandler implements DocumentActionHandler {

        private final Map<String, DocRef> docRefs = new HashMap<>();
        private final String type;

        public MyDocumentActionHandler(final Set<DocRef> docRefs) {
            docRefs.forEach(docRef -> this.docRefs.put(docRef.getUuid(), docRef));
            final Set<String> types = docRefs.stream()
                    .map(DocRef::getType)
                    .collect(Collectors.toSet());
            if (types.size() != 1) {
                throw new IllegalArgumentException("Expecting one type only");
            }
            this.type = types.iterator().next();
        }

        @Override
        public Document readDocument(final DocRef docRef) {
            return null;
        }

        @Override
        public Document writeDocument(final Document document) {
            return null;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public DocRefInfo info(final String uuid) {
            return NullSafe.get(
                    docRefs.get(uuid),
                    docRef -> new DocRefInfo(
                            docRef,
                            System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            "user1",
                            "user1",
                            null));
        }
    }
}
