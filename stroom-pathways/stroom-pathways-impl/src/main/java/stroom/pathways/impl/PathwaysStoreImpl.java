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
 */

package stroom.pathways.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.pathways.shared.PathwaysDoc;
import stroom.security.api.SecurityContext;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class PathwaysStoreImpl
        extends AbstractDocumentStore<PathwaysDoc>
        implements PathwaysStore {

    private final Provider<ClusterLockService> clusterLockServiceProvider;

    @Inject
    public PathwaysStoreImpl(final StoreFactory storeFactory,
                             final SecurityContext securityContext,
                             final PathwaysSerialiser serialiser,
                             final Provider<ClusterLockService> clusterLockServiceProvider) {
        super(storeFactory,
                securityContext,
                serialiser,
                PathwaysDoc.TYPE,
                PathwaysDoc::builder,
                PathwaysDoc::copy);
        this.clusterLockServiceProvider = clusterLockServiceProvider;
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        super.deleteDocument(docRef);

        // Clean up cluster write locks created by PathwaysProcessor for this document.
        // Lock rows accumulate in cluster_lock as shards are written to and are never
        // removed automatically — they must be explicitly deleted on document removal.
        if (docRef != null && docRef.getUuid() != null) {
            try {
                clusterLockServiceProvider.get().deleteLocks("pathways-write-" + docRef.getUuid());
            } catch (final Exception e) {
                // Ignore lock deletion failures to avoid failing the document delete itself.
            }
        }
    }
}
