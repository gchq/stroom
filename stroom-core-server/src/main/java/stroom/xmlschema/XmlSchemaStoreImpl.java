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

package stroom.xmlschema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.docstore.Persistence;
import stroom.docstore.Serialiser2;
import stroom.docstore.Store;
import stroom.entity.shared.BaseResultList;
import stroom.explorer.shared.DocumentType;
import stroom.importexport.LegacyXMLSerialiser;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.query.api.v2.DocRefInfo;
import stroom.security.SecurityContext;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;
import stroom.xmlschema.shared.XmlSchemaDoc;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Singleton
public class XmlSchemaStoreImpl implements XmlSchemaStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(XmlSchemaStoreImpl.class);

    private final Store<XmlSchemaDoc> store;
    private final SecurityContext securityContext;
    private final Persistence persistence;
    private final Serialiser2<XmlSchemaDoc> serialiser;

    @Inject
    public XmlSchemaStoreImpl(final Store<XmlSchemaDoc> store, final SecurityContext securityContext, final Persistence persistence) {
        this.store = store;
        this.securityContext = securityContext;
        this.persistence = persistence;

        serialiser = new XmlSchemaSerialiser();

        store.setType(XmlSchemaDoc.DOCUMENT_TYPE, XmlSchemaDoc.class);
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
        return new DocumentType(13, XmlSchemaDoc.DOCUMENT_TYPE, XmlSchemaDoc.DOCUMENT_TYPE);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public XmlSchemaDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public XmlSchemaDoc writeDocument(final XmlSchemaDoc document) {
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

    private Map<String, byte[]> convert(final DocRef docRef, final Map<String, byte[]> dataMap, final ImportState importState, final ImportMode importMode) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 1 && !dataMap.containsKey("meta") && dataMap.containsKey("xml")) {
            final String uuid = docRef.getUuid();
            try {
                final boolean exists = persistence.exists(docRef);
                XmlSchemaDoc document;
                if (exists) {
                    document = readDocument(docRef);

                } else {
                    final OldXMLSchema oldXmlSchema = new OldXMLSchema();
                    final LegacyXMLSerialiser legacySerialiser = new LegacyXMLSerialiser();
                    legacySerialiser.performImport(oldXmlSchema, dataMap);

                    final long now = System.currentTimeMillis();
                    final String userId = securityContext.getUserId();

                    document = new XmlSchemaDoc();
                    document.setType(docRef.getType());
                    document.setUuid(uuid);
                    document.setName(docRef.getName());
                    document.setVersion(UUID.randomUUID().toString());
                    document.setCreateTime(now);
                    document.setUpdateTime(now);
                    document.setCreateUser(userId);
                    document.setUpdateUser(userId);
                    document.setDescription(oldXmlSchema.getDescription());
                    document.setNamespaceURI(oldXmlSchema.getNamespaceURI());
                    document.setSystemId(oldXmlSchema.getSystemId());
                    document.setData(oldXmlSchema.getData());
                    document.setDeprecated(oldXmlSchema.isDeprecated());
                    document.setSchemaGroup(oldXmlSchema.getSchemaGroup());
                }

                result = serialiser.write(document);
                if (dataMap.containsKey("data.xsd")) {
                    result.put("xsd", dataMap.remove("data.xsd"));
                }

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }

    @Override
    public String getType() {
        return XmlSchemaDoc.DOCUMENT_TYPE;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public BaseResultList<XmlSchemaDoc> find(final FindXMLSchemaCriteria criteria) {
        final List<XmlSchemaDoc> result = new ArrayList<>();

        final List<DocRef> docRefs = list();
        docRefs.forEach(docRef -> {
            try {
                final XmlSchemaDoc doc = readDocument(docRef);
                if (criteria.matches(doc)) {
                    result.add(doc);
                }

            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
            }
        });
        return BaseResultList.createCriterialBasedList(result, criteria);
    }

    @Override
    public List<DocRef> list() {
        return store.list();
    }
}
