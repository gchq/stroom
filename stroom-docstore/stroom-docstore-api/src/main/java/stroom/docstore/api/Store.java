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

package stroom.docstore.api;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docref.HasFindDocsByName;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.AbstractDoc.AbstractBuilder;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.util.shared.Message;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface Store<D extends AbstractDoc>
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

    interface DocumentCreator<D extends AbstractDoc> {

        D create(final String uuid,
                 final String name,
                 final String version,
                 final Long createTime,
                 final Long updateTime,
                 final String createUser,
                 final String updateUser);
    }
}
