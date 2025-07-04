package stroom.docstore.api;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docref.HasFindDocsByName;
import stroom.docstore.shared.Doc;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.util.shared.Message;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface Store<D extends Doc>
        extends DocumentActionHandler<D>, HasFindDocsByName, ContentIndexable {
    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    DocRef createDocument(String name);

    DocRef copyDocument(String originalUuid,
                        String newName);

    DocRef moveDocument(DocRef docRef);

    DocRef renameDocument(DocRef docRef, String name);

    void deleteDocument(DocRef docRef);

    DocRefInfo info(DocRef docRef);

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    Map<DocRef, Set<DocRef>> getDependencies(BiConsumer<D, DependencyRemapper> mapper);

    Set<DocRef> getDependencies(DocRef docRef, BiConsumer<D, DependencyRemapper> mapper);

    void remapDependencies(DocRef docRef, Map<DocRef, DocRef> remappings, BiConsumer<D, DependencyRemapper> mapper);

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    /**
     * Creates the named document, using the supplied {@link DocumentCreator} to
     * provide the initial document skeleton. This allows doc store implementors
     * to provide custom skeleton content.
     */
    DocRef createDocument(final String name, final DocumentCreator<D> documentCreator);

    boolean exists(DocRef docRef);

    DocRef importDocument(
            DocRef docRef,
            Map<String, byte[]> dataMap,
            ImportState importState,
            ImportSettings importSettings);

    Map<String, byte[]> exportDocument(DocRef docRef,
                                       List<Message> messageList,
                                       Function<D, D> filter);

    /**
     * List all documents of this stores type
     */
    List<DocRef> list();

    // This is only used to migrate pipelines. Do not use!!!
    @Deprecated
    void migratePipelines(Function<Map<String, byte[]>, Optional<Map<String, byte[]>>> function);

    interface DocumentCreator<D extends Doc> {

        D create(final String type,
                 final String uuid,
                 final String name,
                 final String version,
                 final Long createTime,
                 final Long updateTime,
                 final String createUser,
                 final String updateUser);
    }
}
