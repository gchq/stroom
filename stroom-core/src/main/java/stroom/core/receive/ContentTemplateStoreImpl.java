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

package stroom.core.receive;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.receive.content.shared.ContentTemplate;
import stroom.receive.content.shared.ContentTemplates;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A bit of a special store that only ever holds one doc with a hard coded name.
 */
public class ContentTemplateStoreImpl implements ContentTemplateStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentTemplateStoreImpl.class);
    private static final String DOC_NAME = "Content Templates";

    private final Store<ContentTemplates> store;
    private final SecurityContext securityContext;

    @Inject
    public ContentTemplateStoreImpl(final StoreFactory storeFactory,
                                    final Serialiser2Factory serialiser2Factory,
                                    final SecurityContext securityContext) {
        this.securityContext = securityContext;
        final DocumentSerialiser2<ContentTemplates> serialiser = serialiser2Factory.createSerialiser(
                ContentTemplates.class);
        this.store = storeFactory.createStore(serialiser, ContentTemplates.TYPE, ContentTemplates::builder);
    }

    @Override
    public ContentTemplates getOrCreate() {
        // The user will never have any doc perms on the DRR as it is not an explorer doc, thus
        // access it via the proc user.
        return securityContext.asProcessingUserResult(() -> {
            // Should return 0-1 docs of our store's type, unless we have a problem
            final List<DocRef> docRefs = store.list();
            final DocRef docRef;
            if (NullSafe.isEmptyCollection(docRefs)) {
                // Not there so create it
                docRef = createDocument(DOC_NAME);
                LOGGER.info("Created document {}", docRef);
            } else {
                if (docRefs.size() > 1) {
                    throw new RuntimeException("Found multiple documents, expecting one. " + docRefs);
                } else {
                    docRef = Objects.requireNonNull(docRefs.getFirst());
                    if (!Objects.equals(DOC_NAME, docRef.getName())) {
                        throw new RuntimeException("Unexpected document " + docRef);
                    }
                }
            }
            return readDocument(docRef);
        });
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

    private BiConsumer<ContentTemplates, DependencyRemapper> createMapper() {
        return (doc, dependencyRemapper) -> {
            final List<ContentTemplate> templates = doc.getContentTemplates();
            if (NullSafe.hasItems(templates)) {
                templates.forEach(receiveDataRule -> {
                    if (receiveDataRule.getExpression() != null) {
                        dependencyRemapper.remapExpression(receiveDataRule.getExpression());
                    }
                });
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
    public ContentTemplates readDocument(final DocRef docRef) {
        return securityContext.secureResult(() ->
                store.readDocument(docRef));
    }

    @Override
    public ContentTemplates writeDocument(final ContentTemplates document) {
        // The user will never have any doc perms on the DRR as it is not an explorer doc, thus
        // access it via the proc user (so long as use has MANAGE_POLICIES_PERMISSION)
        return securityContext.secureResult(AppPermission.MANAGE_CONTENT_TEMPLATES_PERMISSION,
                () -> securityContext.asProcessingUserResult(() -> store.writeDocument(document)));

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
    public List<DocRef> findByNames(final List<String> name, final boolean allowWildCards) {
        return store.findByNames(name, allowWildCards);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }

}
