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

package stroom.dictionary.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import io.swagger.annotations.ApiParam;
import stroom.dictionary.shared.DictionaryDTO;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.DictionaryResource;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.api.DocumentEventLog;
import stroom.importexport.api.DocumentData;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.shared.Base64EncodedDocumentData;
import stroom.importexport.shared.ImportState;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.util.HasHealthCheck;
import stroom.util.io.StreamUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import javax.ws.rs.PathParam;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

class DictionaryResourceImpl implements DictionaryResource, HasHealthCheck {
    private final DictionaryStore dictionaryStore;
    private final DocumentResourceHelper documentResourceHelper;
    private final ResourceStore resourceStore;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    DictionaryResourceImpl(final DictionaryStore dictionaryStore,
                           final DocumentResourceHelper documentResourceHelper,
                           final ResourceStore resourceStore,
                           final DocumentEventLog documentEventLog,
                           final SecurityContext securityContext) {
        this.dictionaryStore = dictionaryStore;
        this.documentResourceHelper = documentResourceHelper;
        this.resourceStore = resourceStore;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    ///////////////////////
    // GWT UI end points //
    ///////////////////////

    @Override
    public DictionaryDoc read(final DocRef docRef) {
        return documentResourceHelper.read(dictionaryStore, docRef);
    }

    @Override
    public DictionaryDoc update(final DictionaryDoc doc) {
        return documentResourceHelper.update(dictionaryStore, doc);
    }

    @Override
    public ResourceGeneration download(final DocRef dictionaryRef) {
        return securityContext.secureResult(() -> {
            // Get dictionary.
            final DictionaryDoc dictionary = dictionaryStore.readDocument(dictionaryRef);
            if (dictionary == null) {
                throw new EntityServiceException("Unable to find dictionary");
            }

            try {
                final ResourceKey resourceKey = resourceStore.createTempFile("dictionary.txt");
                final Path file = resourceStore.getTempFile(resourceKey);
                Files.writeString(file, dictionary.getData(), StreamUtil.DEFAULT_CHARSET);
                documentEventLog.download(dictionary, null);
                return new ResourceGeneration(resourceKey, new ArrayList<>());

            } catch (final IOException e) {
                documentEventLog.download(dictionary, null);
                throw new UncheckedIOException(e);
            }
        });
    }


    ////////////////////////
    // React UI endpoints //
    ////////////////////////

    public Set<DocRef> listDocuments() {
        return dictionaryStore.listDocuments();
    }

    public DocRef importDocument(@ApiParam("DocumentData") final Base64EncodedDocumentData encodedDocumentData) {
        final DocumentData documentData = DocumentData.fromBase64EncodedDocumentData(encodedDocumentData);
        final ImportState importState = new ImportState(documentData.getDocRef(), documentData.getDocRef().getName());

        final ImportExportActionHandler.ImpexDetails result =  dictionaryStore.importDocument(documentData.getDocRef(), documentData.getDataMap(), importState, ImportState.ImportMode.IGNORE_CONFIRMATION);
        if (result != null)
            return result.getDocRef();
        else
            return null;
    }

    public Base64EncodedDocumentData exportDocument(@ApiParam("DocRef") final DocRef docRef) {
        final Map<String, byte[]> map = dictionaryStore.exportDocument(docRef, true, new ArrayList<>());
        return DocumentData.toBase64EncodedDocumentData(new DocumentData(docRef, map));
    }

    private DictionaryDTO fetchInScope(final String dictionaryUuid) {
        final DictionaryDoc doc = dictionaryStore.readDocument(getDocRef(dictionaryUuid));
        final DictionaryDTO dto = new DictionaryDTO(doc);
        return dto;
    }

    public DictionaryDTO fetch(@PathParam("dictionaryUuid") final String dictionaryUuid) {
        // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
        return securityContext.useAsReadResult(() -> fetchInScope(dictionaryUuid));
    }

    private DocRef getDocRef(final String pipelineId) {
        return new DocRef.Builder()
                .uuid(pipelineId)
                .type(DictionaryDoc.ENTITY_TYPE)
                .build();
    }

    public void save(@PathParam("dictionaryUuid") final String dictionaryUuid,
                     final DictionaryDTO updates) {
        System.out.println("DEBUG in save");
        // A user should be allowed to read pipelines that they are inheriting from as long as they have 'use' permission on them.
        securityContext.useAsRead(() -> {
            final DictionaryDoc doc = dictionaryStore.readDocument(getDocRef(dictionaryUuid));

            if (doc != null) {
                doc.setDescription(updates.getDescription());
                doc.setData(updates.getData());
                doc.setImports(updates.getImports());
                dictionaryStore.writeDocument(doc);
            }
        });
    }


    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}