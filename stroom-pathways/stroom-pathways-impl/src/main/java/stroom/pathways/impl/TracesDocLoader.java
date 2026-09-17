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

package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.pathways.shared.TracesDoc;
import stroom.planb.shared.PlanBDocument;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
class TracesDocLoader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TracesDocLoader.class);

    private final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider;
    private final SecurityContext securityContext;

    @Inject
    TracesDocLoader(final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider,
                    final SecurityContext securityContext) {
        this.documentActionHandlersProvider = documentActionHandlersProvider;
        this.securityContext = securityContext;
    }

    /**
     * Resolves the trace store a query names, once the caller is allowed to use it.
     *
     * <p>The type is supplied by the caller and is checked rather than trusted. Only a
     * {@link TracesDoc} is resolvable here: the type decides nothing beyond whether the request is
     * accepted, so it cannot be used to steer the lookup somewhere else.
     */
    PlanBDocument getPlanBDoc(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        if (!TracesDoc.TYPE.equals(docRef.getType())) {
            throw new IllegalArgumentException(
                    "A trace query needs a " + TracesDoc.TYPE + ", not a '" + docRef.getType() + "'");
        }

        final PlanBDocument doc;
        try {
            final DocumentActionHandler<?> handler = documentActionHandlersProvider.get()
                    .get(new DocumentTypeName(TracesDoc.TYPE));
            if (handler == null) {
                throw new IllegalStateException("No handler found for type: " + TracesDoc.TYPE);
            }
            doc = (PlanBDocument) handler.readDocument(docRef);
        } catch (final Exception e) {
            LOGGER.error("Failed to read TracesDoc " + docRef, e);
            throw new RuntimeException("Failed to read TracesDoc '" + docRef.getName() + "'", e);
        }
        // Outside the try: the catch above would report a refusal as a failed read.
        return checkUsePermission(doc);
    }

    // The read above is unauthorised, so this is the only point at which a caller's right to the
    // trace store is decided.
    private PlanBDocument checkUsePermission(final PlanBDocument doc) {
        if (doc == null) {
            return null;
        }
        final DocRef docRef = doc.asDocRef();
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    LogUtil.message("You are not authorised to read {}", docRef));
        }
        return doc;
    }
}
