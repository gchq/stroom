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
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.shared.PlanBDocument;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
class TracesDocLoader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TracesDocLoader.class);

    private final PlanBDocCache planBDocCache;
    private final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider;

    @Inject
    TracesDocLoader(final PlanBDocCache planBDocCache,
                    final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider) {
        this.planBDocCache = planBDocCache;
        this.documentActionHandlersProvider = documentActionHandlersProvider;
    }

    PlanBDocument getPlanBDoc(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        if (TracesDoc.TYPE.equals(docRef.getType())) {
            try {
                final DocumentActionHandler<?> handler = documentActionHandlersProvider.get()
                        .get(new DocumentTypeName(TracesDoc.TYPE));
                if (handler == null) {
                    throw new IllegalStateException("No handler found for type: " + TracesDoc.TYPE);
                }
                return (PlanBDocument) handler.readDocument(docRef);
            } catch (final Exception e) {
                LOGGER.error("Failed to read TracesDoc " + docRef, e);
                throw new RuntimeException("Failed to read TracesDoc '" + docRef.getName() + "'", e);
            }
        } else {
            return planBDocCache.get(docRef.getName());
        }
    }
}
