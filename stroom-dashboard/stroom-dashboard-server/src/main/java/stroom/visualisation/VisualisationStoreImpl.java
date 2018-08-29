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

package stroom.visualisation;

import stroom.docref.DocRef;
import stroom.docstore.Persistence;
import stroom.docstore.Store;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.LegacyXMLSerialiser;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.query.api.v2.DocRefInfo;
import stroom.security.SecurityContext;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;
import stroom.visualisation.shared.VisualisationDoc;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Singleton
class VisualisationStoreImpl implements VisualisationStore {
    private final Store<VisualisationDoc> store;
    private final SecurityContext securityContext;
    private final Persistence persistence;
    private final VisualisationSerialiser serialiser;

    @Inject
    VisualisationStoreImpl(final Store<VisualisationDoc> store, final SecurityContext securityContext, final Persistence persistence) {
        this.store = store;
        this.securityContext = securityContext;
        this.persistence = persistence;

        serialiser = new VisualisationSerialiser();

        store.setType(VisualisationDoc.DOCUMENT_TYPE, VisualisationDoc.class);
        store.setSerialiser(serialiser);
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name) {
        return store.createDocument(name);
    }

    @Override
    public DocRef copyDocument(final String originalUuid,
                               final String copyUuid,
                               final Map<String, String> otherCopiesByOriginalUuid) {
        return store.copyDocument(originalUuid, copyUuid, otherCopiesByOriginalUuid);
    }

    @Override
    public DocRef moveDocument(final String uuid) {
        return store.moveDocument(uuid);
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

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(9, VisualisationDoc.DOCUMENT_TYPE, VisualisationDoc.DOCUMENT_TYPE);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public VisualisationDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public VisualisationDoc writeDocument(final VisualisationDoc document) {
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
    public DocRef importDocument(final DocRef docRef, final Map<String, byte[]> dataMap, final ImportState importState, final ImportMode importMode) {
        // Convert legacy import format to the new format.
        final Map<String, byte[]> map = convert(docRef, dataMap, importState, importMode);
        if (map != null) {
            return store.importDocument(docRef, map, importState, importMode);
        }

        return docRef;
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef, final boolean omitAuditFields, final List<Message> messageList) {
        return store.exportDocument(docRef, omitAuditFields, messageList);
    }

    @Override
    public String getType() {
        return VisualisationDoc.DOCUMENT_TYPE;
    }

    private Map<String, byte[]> convert(final DocRef docRef, final Map<String, byte[]> dataMap, final ImportState importState, final ImportMode importMode) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 0 && !dataMap.containsKey("meta")) {
            final String uuid = docRef.getUuid();
            try {
                final boolean exists = persistence.exists(docRef);
                VisualisationDoc document;
                if (exists) {
                    document = readDocument(docRef);

                } else {
                    final OldVisualisation oldVisualisation = new OldVisualisation();
                    final LegacyXMLSerialiser legacySerialiser = new LegacyXMLSerialiser();
                    legacySerialiser.performImport(oldVisualisation, dataMap);

                    final long now = System.currentTimeMillis();
                    final String userId = securityContext.getUserId();

                    document = new VisualisationDoc();
                    document.setType(docRef.getType());
                    document.setUuid(uuid);
                    document.setName(docRef.getName());
                    document.setVersion(UUID.randomUUID().toString());
                    document.setCreateTime(now);
                    document.setUpdateTime(now);
                    document.setCreateUser(userId);
                    document.setUpdateUser(userId);
                    document.setDescription(oldVisualisation.getDescription());
                    document.setFunctionName(oldVisualisation.getFunctionName());
                    document.setSettings(oldVisualisation.getSettings());

                    final DocRef scriptRef = serialiser.getDocRefFromLegacyXML(oldVisualisation.getScriptRefXML());
                    if (scriptRef != null) {
                        document.setScriptRef(scriptRef);
                    }
                }

                result = serialiser.write(document);
                if (dataMap.containsKey("settings.json")) {
                    result.put("json", dataMap.remove("settings.json"));
                }

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public List<DocRef> list() {
        return store.list();
    }
}
