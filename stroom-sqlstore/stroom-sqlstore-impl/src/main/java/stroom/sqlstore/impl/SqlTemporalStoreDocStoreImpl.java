/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqlstore.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.security.api.SecurityContext;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;


@Singleton
public class SqlTemporalStoreDocStoreImpl
        extends AbstractDocumentStore<SqlTemporalStoreDoc>
        implements SqlTemporalStoreDocStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqlTemporalStoreDocStoreImpl.class);

    private final Provider<UpdatableTemporalStoreDao> daoProvider;

    @Inject
    public SqlTemporalStoreDocStoreImpl(
            final StoreFactory storeFactory,
            final SecurityContext securityContext,
            final SqlTemporalStoreSerialiser serialiser,
            final Provider<UpdatableTemporalStoreDao> daoProvider) {
        super(storeFactory,
                securityContext,
                serialiser,
                SqlTemporalStoreDoc.TYPE,
                SqlTemporalStoreDoc::builder,
                SqlTemporalStoreDoc::copy);
        this.daoProvider = daoProvider;
    }

    /// Deletes the document and every entry it holds.
    ///
    /// The entries live in their own table keyed on the document's UUID, which nothing in
    /// [AbstractDocumentStore] knows about. Without this they outlived the document, unreachable,
    /// and re-importing a document with the same UUID silently brought them all back.
    ///
    /// The document goes first, so a user without DELETE permission is refused before any data is
    /// touched.
    @Override
    public void deleteDocument(final DocRef docRef) {
        super.deleteDocument(docRef);
        LOGGER.debug("Clearing the entries of deleted temporal store {}", docRef);
        daoProvider.get().clear(docRef.getUuid());
    }

    /*
     * Name handling is left to AbstractDocumentStore, which takes the explorer's
     * permission-filtered, folder-scoped candidate names and applies the same
     * UniqueNameUtil.getCopyName convention as every other document type. Where a name must
     * still be resolved to a single store - a StroomQL "from" clause, an XSLT lookup -
     * UpdatableSqlTemporalStore does that against only the documents the caller can see, and
     * refuses an ambiguous match.
     */
}
