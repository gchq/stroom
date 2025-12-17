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

package stroom.dictionary.impl;

import stroom.dictionary.api.DictionaryStore;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.WordList;
import stroom.dictionary.shared.WordList.Builder;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefDecorator;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.string.StringUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Singleton
class DictionaryStoreImpl implements DictionaryStore, WordListProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DictionaryStoreImpl.class);

    public static final boolean IS_DE_DUP_DEFAULT = false;
    private final Store<DictionaryDoc> store;

    @Inject
    DictionaryStoreImpl(final StoreFactory storeFactory,
                        final DictionarySerialiser serialiser) {
        this.store = storeFactory.createStore(serialiser, DictionaryDoc.TYPE, DictionaryDoc::builder);
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

    private BiConsumer<DictionaryDoc, DependencyRemapper> createMapper() {
        return (doc, dependencyRemapper) -> {
            if (doc.getImports() != null) {
                final List<DocRef> replacedDocRefImports = doc
                        .getImports()
                        .stream()
                        .map(dependencyRemapper::remap)
                        .toList();
                doc.setImports(replacedDocRefImports);
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
    public DictionaryDoc readDocument(final DocRef docRef) {
        final DictionaryDoc dictionaryDoc = store.readDocument(docRef);
        return dictionaryDoc;
    }

    @Override
    public DictionaryDoc writeDocument(final DictionaryDoc document) {
        return store.writeDocument(document);
    }

//    /**
//     * Ensure all the imports have the correct names. Modifies the import list.
//     */
//    private void decorateImports(final DictionaryDoc dictionaryDoc) {
//        if (dictionaryDoc != null && NullSafe.hasItems(dictionaryDoc.getImports())) {
//            final DocRefInfoService docRefInfoService = docRefInfoServiceProvider.get();
//            final List<DocRef> decoratedImports = dictionaryDoc.getImports()
//                    .stream()
//                    .map(docRef -> decorateDocRef(docRefInfoService, docRef))
//                    .toList();
//            dictionaryDoc.setImports(decoratedImports);
//        }
//    }

    private DocRef decorateDocRef(final DocRefDecorator docRefDecorator,
                                  final DocRef docRef) {
        if (docRef == null) {
            return null;
        } else if (docRefDecorator == null) {
            return docRef;
        } else {
            try {
                return docRefDecorator.decorate(docRef, true);
            } catch (final Exception e) {
                // Likely docRef doesn't exist, so we will just leave it as is, i.e.
                // a broken dep
                LOGGER.debug(() -> LogUtil.message("Unable to decorate docRef {}: {}",
                        docRef, LogUtil.exceptionMessage(e)), e);
                return docRef;
            }
        }
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
    public Optional<DocRef> findByUuid(final String uuid) {
        try {
            final DocRefInfo docRefInfo = store.info(new DocRef(DictionaryDoc.TYPE, uuid));
            return Optional.ofNullable(docRefInfo.getDocRef());
        } catch (final RuntimeException e) {
            // Expected permission exception for some users.
            LOGGER.debug(e::getMessage, e);
        }
        return Optional.empty();
    }

    @Override
    public List<DocRef> findByName(final String name) {
        return findByNames(List.of(name), false);
    }

    @Override
    public List<DocRef> findByNames(final List<String> names, final boolean allowWildCards) {
        return store.findByNames(names, allowWildCards);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }

    @Override
    public List<DocRef> list() {
        return store.list();
    }

    @Override
    public String getCombinedData(final DocRef docRef) {
        return getCombinedWordList(docRef, null, IS_DE_DUP_DEFAULT).asString();
    }

    @Override
    public String[] getWords(final DocRef dictionaryRef) {
        return getCombinedWordList(dictionaryRef, null, IS_DE_DUP_DEFAULT).asWordArray();
    }

    @Override
    public WordList getCombinedWordList(final DocRef dictionaryRef,
                                        final DocRefDecorator docRefDecorator) {
        return getCombinedWordList(dictionaryRef, docRefDecorator, IS_DE_DUP_DEFAULT);
    }

    public WordList getCombinedWordList(final DocRef dictionaryRef,
                                        final DocRefDecorator docRefDecorator,
                                        final boolean deDup) {
        final Builder builder = WordList.builder(deDup);
        final Set<DocRef> visited = new HashSet<>();
        final Stack<DocRef> visitPath = new Stack<>();

        doGetCombinedWordList(docRefDecorator, builder, dictionaryRef, visited, visitPath);

        final WordList wordList = builder.build();

        LOGGER.debug(() -> LogUtil.message("Returning wordList with {} 'words' and {} sources.",
                wordList.size(), wordList.sourceCount()));

        return wordList;
    }

    private void doGetCombinedWordList(final DocRefDecorator docRefDecorator,
                                       final WordList.Builder wordListBuilder,
                                       final DocRef docRef,
                                       final Set<DocRef> visited,
                                       final Stack<DocRef> visitPath) {

        // As we are adding the docRef to the WordList, we want to ensure it
        // has a name and the correct name
        final DocRef decorateDocRef = decorateDocRef(docRefDecorator, docRef);
        LOGGER.debug(() -> LogUtil.message("decorateDocRef: {}, visitPath: {}",
                decorateDocRef.toShortString(), docRefsToStr(visitPath)));
        visitPath.push(decorateDocRef);

        // Prevent circular import dependencies.
        if (!visited.contains(decorateDocRef)) {
            visited.add(decorateDocRef);

            try {
                // If deDup is true then the lowest level dict will win, or
                // if duplicates appear in multiple sibling imports then the first
                // sibling encountered with it will win.
                // This is to be consistent with existing behaviour where imports come first
                // in the combined word list.
                // Precedence only impacts the source inside the Word object.
                final DictionaryDoc doc = readDocument(decorateDocRef);
                if (doc != null) {
                    // First add the words from each of the imports (and recursing into their imports too)
                    final List<DocRef> imports = doc.getImports();
                    if (NullSafe.hasItems(imports)) {
                        LOGGER.debug(() -> LogUtil.message("docRef: {} has imports: {}",
                                decorateDocRef.toShortString(), docRefsToStr(imports)));

                        for (final DocRef importDocRef : imports) {
                            // Recurse
                            doGetCombinedWordList(
                                    docRefDecorator,
                                    wordListBuilder,
                                    importDocRef,
                                    visited,
                                    visitPath);
                        }
                    }

                    // Add the words from this dict first as the higher level takes precedence
                    final String data = doc.getData();
                    if (NullSafe.isNonBlankString(data)) {
                        StringUtil.splitToLines(data, true)
                                .forEach(line ->
                                        wordListBuilder.addWord(line, decorateDocRef));
                    }
                }
            } catch (final PermissionException e) {
                // Silently ignore permission exceptions as not all users will be able to read all dicts
                LOGGER.debug(() -> LogUtil.message("Permission exception reading {}: {}",
                        decorateDocRef, e.getMessage()), e);
            }
        } else {
            LOGGER.debug(() -> LogUtil.message("Found circular import, ignoring {}, visitPath: {}",
                    decorateDocRef.toShortString(), docRefsToStr(visitPath)));
        }

        visitPath.pop();
    }

    private String docRefsToStr(final Collection<DocRef> docRefs) {
        if (NullSafe.hasItems(docRefs)) {
            return "[" + docRefs.stream()
                    .map(DocRef::toShortString)
                    .collect(Collectors.joining(", ")) + "]";

        } else {
            return "[]";
        }
    }
}
