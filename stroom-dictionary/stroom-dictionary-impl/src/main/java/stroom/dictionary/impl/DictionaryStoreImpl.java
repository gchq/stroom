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
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.StoreFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.string.StringUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

@Singleton
public class DictionaryStoreImpl
        extends AbstractDocumentStore<DictionaryDoc>
        implements DictionaryStore, WordListProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DictionaryStoreImpl.class);

    public static final boolean IS_DE_DUP_DEFAULT = false;

    private final DocFinder docFinder;

    @Inject
    DictionaryStoreImpl(final StoreFactory storeFactory,
                        final DictionarySerialiser serialiser,
                        final DocFinder docFinder) {
        super(storeFactory,
                serialiser,
                DictionaryDoc.TYPE,
                DictionaryDoc::builder,
                DictionaryDoc::copy);
        this.docFinder = docFinder;
    }

    @Override
    protected DependencyRemapFunction<DictionaryDoc> getDependencyRemapFunction() {
        return (doc, dependencyRemapper) -> {
            if (doc.getImports() != null) {
                final List<DocRef> replacedDocRefImports = doc
                        .getImports()
                        .stream()
                        .map(dependencyRemapper::remap)
                        .toList();
                return doc.copy().imports(replacedDocRefImports).build();
            }
            return doc;
        };
    }

    private DocRef decorateDocRef(final DocRef docRef) {
        if (docRef == null) {
            return null;
        } else if (docFinder == null) {
            return docRef;
        } else {
            return docFinder.decorate(docRef);
        }
    }

    @Override
    public Optional<DocRef> findByUuid(final String uuid) {
        try {
            return docFinder.decorateIfExists(new DocRef(DictionaryDoc.TYPE, uuid));
        } catch (final RuntimeException e) {
            // Expected permission exception for some users.
            LOGGER.debug(e::getMessage, e);
        }
        return Optional.empty();
    }

    @Override
    public List<DocRef> findByName(final String name) {
        return docFinder.findByName(getType(), name, false);
    }

    @Override
    public String getCombinedData(final DocRef docRef) {
        return getCombinedWordList(docRef, IS_DE_DUP_DEFAULT).asString();
    }

    @Override
    public String[] getWords(final DocRef dictionaryRef) {
        return getCombinedWordList(dictionaryRef, IS_DE_DUP_DEFAULT).asWordArray();
    }

    @Override
    public WordList getCombinedWordList(final DocRef dictionaryRef) {
        return getCombinedWordList(dictionaryRef, IS_DE_DUP_DEFAULT);
    }

    public WordList getCombinedWordList(final DocRef dictionaryRef,
                                        final boolean deDup) {
        final Builder builder = WordList.builder(deDup);
        final Set<DocRef> visited = new HashSet<>();
        final Stack<DocRef> visitPath = new Stack<>();

        doGetCombinedWordList(builder, dictionaryRef, visited, visitPath);

        final WordList wordList = builder.build();

        LOGGER.debug(() -> LogUtil.message("Returning wordList with {} 'words' and {} sources.",
                wordList.size(), wordList.sourceCount()));

        return wordList;
    }

    private void doGetCombinedWordList(final WordList.Builder wordListBuilder,
                                       final DocRef docRef,
                                       final Set<DocRef> visited,
                                       final Stack<DocRef> visitPath) {

        // As we are adding the docRef to the WordList, we want to ensure it
        // has a name and the correct name
        final DocRef decorateDocRef = decorateDocRef(docRef);
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
