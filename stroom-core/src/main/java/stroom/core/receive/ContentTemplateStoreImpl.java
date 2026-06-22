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
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.receive.content.shared.ContentTemplate;
import stroom.receive.content.shared.ContentTemplates;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Objects;

/**
 * A bit of a special store that only ever holds one doc with a hard coded name.
 */
public class ContentTemplateStoreImpl
        extends AbstractDocumentStore<ContentTemplates>
        implements ContentTemplateStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentTemplateStoreImpl.class);
    private static final String DOC_NAME = "Content Templates";

    private final SecurityContext securityContext;

    @Inject
    public ContentTemplateStoreImpl(final StoreFactory storeFactory,
                                    final Serialiser2Factory serialiser2Factory,
                                    final SecurityContext securityContext) {
        super(storeFactory,
                serialiser2Factory.createSerialiser(ContentTemplates.class),
                ContentTemplates.TYPE,
                ContentTemplates::builder,
                ContentTemplates::copy);
        this.securityContext = securityContext;
    }

    @Override
    public ContentTemplates getOrCreate() {
        // The user will never have any doc perms on the DRR as it is not an explorer doc, thus
        // access it via the proc user.
        return securityContext.asProcessingUserResult(() -> {
            // Should return 0-1 docs of our store's type, unless we have a problem
            final List<DocRef> docRefs = getStore().list();
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

    @Override
    public ContentTemplates readDocument(final DocRef docRef) {
        return securityContext.secureResult(() ->
                getStore().readDocument(docRef));
    }

    @Override
    public ContentTemplates writeDocument(final ContentTemplates document) {
        // The user will never have any doc perms on the DRR as it is not an explorer doc, thus
        // access it via the proc user (so long as use has MANAGE_POLICIES_PERMISSION)
        return securityContext.secureResult(AppPermission.MANAGE_CONTENT_TEMPLATES_PERMISSION,
                () -> securityContext.asProcessingUserResult(() -> getStore().writeDocument(document)));
    }

    @Override
    protected DependencyRemapFunction<ContentTemplates> getDependencyRemapFunction() {
        return (doc, dependencyRemapper) -> {
            final List<ContentTemplate> templates = doc.getContentTemplates();
            if (NullSafe.hasItems(templates)) {
                templates.forEach(receiveDataRule -> {
                    if (receiveDataRule.getExpression() != null) {
                        dependencyRemapper.remapExpression(receiveDataRule.getExpression());
                    }
                });
            }
            return doc;
        };
    }
}
