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

package stroom.search.elastic;

import stroom.docstore.server.JsonSerialiser;
import stroom.docstore.server.Store;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Message;

import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Singleton
public class ElasticIndexStoreImpl implements ElasticIndexStore {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexStoreImpl.class);

    private final Store<ElasticIndex> store;
    private final ElasticIndexService elasticIndexService;

    @Inject
    public ElasticIndexStoreImpl(final Store<ElasticIndex> store,
                                 final ElasticIndexService elasticIndexService
    ) {
        this.store = store;
        this.elasticIndexService = elasticIndexService;

        store.setType(ElasticIndex.ENTITY_TYPE, ElasticIndex.class);
        store.setSerialiser(new JsonSerialiser<ElasticIndex>() {
            @Override
            public void write(final OutputStream outputStream, final ElasticIndex document, final boolean export) throws IOException {
                super.write(outputStream, document, export);
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name, final String parentFolderUUID) {
        return store.createDocument(name, parentFolderUUID);
    }

    @Override
    public DocRef copyDocument(final String originalUuid,
                               final String copyUuid,
                               final Map<String, String> otherCopiesByOriginalUuid,
                               final String parentFolderUUID) {
        return store.copyDocument(originalUuid, copyUuid, otherCopiesByOriginalUuid, parentFolderUUID);
    }

    @Override
    public DocRef moveDocument(final String uuid, final String parentFolderUUID) {
        return store.moveDocument(uuid, parentFolderUUID);
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        return store.renameDocument(uuid, name);
    }

    @Override
    public void deleteDocument(final String uuid) {
        store.deleteDocument(uuid);
    }

    @Override
    public DocRefInfo info(String uuid) {
        return store.info(uuid);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public ElasticIndex readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public ElasticIndex writeDocument(final ElasticIndex document) {
        document.setDataSourceFields(elasticIndexService.getDataSourceFields(document));
        document.setFields(elasticIndexService.getFields(document));

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
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return Collections.emptyMap();
    }

    @Override
    public DocRef importDocument(final DocRef docRef, final Map<String, String> dataMap, final ImportState importState, final ImportMode importMode) {
        return store.importDocument(docRef, dataMap, importState, importMode);
    }

    @Override
    public Map<String, String> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        return store.exportDocument(docRef, omitAuditFields, messageList);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public String getDocType() {
        return ElasticIndex.ENTITY_TYPE;
    }

    @Override
    public ElasticIndex read(final String uuid) {
        return store.read(uuid);
    }

    @Override
    public ElasticIndex update(final ElasticIndex dataReceiptPolicy) {
        return store.update(dataReceiptPolicy);
    }

    @Override
    public List<DocRef> list() {
        return store.list();
    }
}
