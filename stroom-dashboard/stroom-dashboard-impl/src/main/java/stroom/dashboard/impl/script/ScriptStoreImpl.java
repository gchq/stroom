/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.dashboard.impl.script;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.script.shared.ScriptDoc;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Message;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

@Singleton
class ScriptStoreImpl implements ScriptStore {

    private final Store<ScriptDoc> store;
    private final SecurityContext securityContext;

    @Inject
    ScriptStoreImpl(final StoreFactory storeFactory,
                    final ScriptSerialiser serialiser,
                    final SecurityContext securityContext) {
        this.store = storeFactory.createStore(serialiser, ScriptDoc.TYPE, ScriptDoc.class);
        this.securityContext = securityContext;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name) {
        return store.createDocument(name);
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        return store.copyDocument(docRef.getUuid(), newName);
    }

    @Override
    public DocRef moveDocument(final DocRef docRef) {
        return store.moveDocument(docRef);
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        return store.renameDocument(docRef, name);
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        store.deleteDocument(docRef);
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        return store.info(docRef);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return store.getDependencies(createMapper());
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return store.getDependencies(docRef, createMapper());
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        store.remapDependencies(docRef, remappings, createMapper());
    }

    private BiConsumer<ScriptDoc, DependencyRemapper> createMapper() {
        return (doc, dependencyRemapper) -> {
            if (doc.getDependencies() != null) {
                doc.setDependencies(doc.getDependencies()
                        .stream()
                        .map(dependencyRemapper::remap)
                        .toList());
            }
        };
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public ScriptDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public ScriptDoc writeDocument(final ScriptDoc document) {
        return store.writeDocument(document);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Set<DocRef> listDocuments() {
        return store.listDocuments();
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final Map<String, byte[]> dataMap,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        return store.importDocument(docRef, dataMap, importState, importSettings);
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef,
                                              final boolean omitAuditFields,
                                              final List<Message> messageList) {
        if (omitAuditFields) {
            return store.exportDocument(docRef, messageList, new AuditFieldFilter<>());
        }
        return store.exportDocument(docRef, messageList, d -> d);
    }

    @Override
    public String getType() {
        return store.getType();
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public List<DocRef> list() {
        return store.list();
    }

    @Override
    public List<DocRef> findByNames(final List<String> name, final boolean allowWildCards) {
        return store.findByNames(name, allowWildCards);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }

    @Override
    public List<ScriptDoc> fetchLinkedScripts(final DocRef script, final Set<DocRef> loadedScripts) {
        return securityContext.secureResult(() -> {
            // Elevate the users permissions for the duration of this task so they can read the script if
            // they have 'use' permission.
            return securityContext.useAsReadResult(() -> {
                final List<ScriptDoc> scripts = new ArrayList<>();

                final Set<DocRef> uiLoadedScripts;
                if (loadedScripts == null) {
                    uiLoadedScripts = new HashSet<>();
                } else {
                    uiLoadedScripts = loadedScripts;
                }

                // Load the script and it's dependencies.
                loadScripts(script, uiLoadedScripts, new HashSet<>(), scripts);

                return scripts;
            });
        });
    }

    private void loadScripts(final DocRef docRef,
                             final Set<DocRef> uiLoadedScripts,
                             final Set<DocRef> loadedScripts,
                             final List<ScriptDoc> scripts) {
        // Prevent circular reference loading with this set.
        if (!loadedScripts.contains(docRef)) {
            loadedScripts.add(docRef);


            final ScriptDoc loadedScript = readDocument(docRef);
            if (loadedScript != null) {
                // Add required dependencies first.
                if (loadedScript.getDependencies() != null) {
                    for (final DocRef dep : loadedScript.getDependencies()) {
                        loadScripts(dep, uiLoadedScripts, loadedScripts, scripts);
                    }
                }

                // Add this script.
                if (!uiLoadedScripts.contains(docRef)) {
                    uiLoadedScripts.add(docRef);
                    scripts.add(loadedScript);
                }
            }
        }
    }
}
