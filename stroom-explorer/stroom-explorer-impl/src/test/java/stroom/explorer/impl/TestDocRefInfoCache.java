package stroom.explorer.impl;

import stroom.cache.impl.CacheManagerImpl;
import stroom.docref.DocContentMatch;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypeGroup;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.svg.shared.SvgImage;
import stroom.util.NullSafe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
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
        final ExplorerActionHandlers explorerActionHandlers = new ExplorerActionHandlers(
                Set.of(
                        new MyExplorerActionHandler(Set.of(DOC_REF_1, DOC_REF_2)),
                        new MyExplorerActionHandler(Set.of(DOC_REF_3, DOC_REF_4))));

        docRefInfoCache = new DocRefInfoCache(
                cacheManager,
                explorerActionHandlers,
                ExplorerConfig::new,
                securityContext);
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


    private static class MyExplorerActionHandler implements ExplorerActionHandler {

        private final Map<String, DocRef> docRefs = new HashMap<>();
        private final String type;

        private MyExplorerActionHandler(final Set<DocRef> docRefs) {
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
        public List<DocContentMatch> findByContent(final String pattern, final boolean regex, final boolean matchCase) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<DocRef> listDocuments() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DocRef> findByNames(final List<String> names, final boolean allowWildCards) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocRef createDocument(final String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocRef copyDocument(final DocRef docRef,
                                   final String name,
                                   final boolean makeNameUnique,
                                   final Set<String> existingNames) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocRef moveDocument(final String uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocRef renameDocument(final String uuid, final String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteDocument(final String uuid) {
            throw new UnsupportedOperationException();
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

        @Override
        public DocumentType getDocumentType() {
            return new DocumentType(
                    DocumentTypeGroup.CONFIGURATION,
                    type,
                    type,
                    SvgImage.OK);
        }

        @Override
        public Map<DocRef, Set<DocRef>> getDependencies() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<DocRef> getDependencies(final DocRef docRef) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remapDependencies(final DocRef docRef, final Map<DocRef, DocRef> remappings) {
            throw new UnsupportedOperationException();
        }
    }
}
