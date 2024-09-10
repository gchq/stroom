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

//    Set<DocRef> listDocuments();

    DocRef importDocument(
            DocRef docRef,
            Map<String, byte[]> dataMap,
            ImportState importState,
            ImportSettings importSettings);

    Map<String, byte[]> exportDocument(DocRef docRef,
                                       List<Message> messageList,
                                       Function<D, D> filter);

    List<DocRef> list();

    /**
     * Find by exact case-sensitive match on the name
     */
//    default List<DocRef> findByName(String name) {
//        return name != null
//                ? findByNames(List.of(name), false)
//                : Collections.emptyList();
//    }
//
//    /**
//     * Find by case-sensitive match on the name.
//     * If allowWildCards is true '*' can be used to denote a 0-many char wild card.
//     */
//    default List<DocRef> findByName(final String name,
//                                    final boolean allowWildCards) {
//        return name != null
//                ? findByNames(List.of(name), allowWildCards)
//                : Collections.emptyList();
//    }
//
//    List<DocRef> findByNames(final List<String> name,
//                             final boolean allowWildCards);

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
