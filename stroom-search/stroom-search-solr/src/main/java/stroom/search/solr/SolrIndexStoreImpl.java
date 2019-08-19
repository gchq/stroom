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

package stroom.search.solr;

import com.google.common.base.Functions;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.AddField;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.DeleteField;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.Fields;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.ReplaceField;
import org.apache.solr.client.solrj.response.schema.SchemaResponse.FieldsResponse;
import org.springframework.stereotype.Component;
import stroom.docstore.server.JsonSerialiser;
import stroom.docstore.server.Persistence;
import stroom.docstore.server.Store;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.search.solr.shared.SolrIndex;
import stroom.search.solr.shared.SolrIndexField;
import stroom.search.solr.shared.SolrIndexFieldType;
import stroom.search.solr.shared.SolrSynchState;
import stroom.security.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Message;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Singleton
public class SolrIndexStoreImpl implements SolrIndexStore {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrIndexStoreImpl.class);

    private static final Pattern VALID_FIELD_NAME_PATTERN = Pattern.compile(SolrIndexField.VALID_FIELD_NAME_PATTERN);

    private final Store<SolrIndex> store;
    private final SecurityContext securityContext;
    private final Persistence persistence;
    private final SolrIndexClientCache solrIndexClientCache;

    @Inject
    public SolrIndexStoreImpl(final Store<SolrIndex> store,
                              final SecurityContext securityContext,
                              final Persistence persistence,
                              final SolrIndexClientCache solrIndexClientCache) {
        this.store = store;
        this.securityContext = securityContext;
        this.persistence = persistence;
        this.solrIndexClientCache = solrIndexClientCache;
        store.setType(SolrIndex.ENTITY_TYPE, SolrIndex.class);
        store.setSerialiser(new JsonSerialiser<>());
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
    public SolrIndex readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public SolrIndex writeDocument(final SolrIndex document) {
        final List<String> messages = new ArrayList<>();
        final AtomicInteger replaceCount = new AtomicInteger();
        final AtomicInteger addCount = new AtomicInteger();
        final AtomicInteger deleteCount = new AtomicInteger();

        // Test for invalid field names.
        if (document.getFields() != null) {
            document.getFields().forEach(solrIndexField -> {
                if (solrIndexField.getFieldName() == null) {
                    throw new RuntimeException("Null field name");
                } else if (!VALID_FIELD_NAME_PATTERN.matcher(solrIndexField.getFieldName()).matches()) {
                    throw new RuntimeException("Invalid field name " + solrIndexField.getFieldName());
                }
            });
        }

        try {
            solrIndexClientCache.context(document.getSolrConnectionConfig(), solrClient -> {
                try {
                    Map<String, SolrIndexField> existingFieldMap = Collections.emptyMap();
                    if (document.getFields() != null) {
                        existingFieldMap = document
                                .getFields()
                                .stream()
                                .collect(Collectors.toMap(SolrIndexField::getFieldName, Functions.identity()));
                    }

                    List<SolrIndexField> solrFields = fetchSolrFields(solrClient, document.getCollection(), existingFieldMap);

                    // Which fields are missing from Solr?
                    final Map<String, SolrIndexField> solrFieldMap = solrFields.stream().collect(Collectors.toMap(SolrIndexField::getFieldName, Functions.identity()));
                    existingFieldMap.forEach((k, v) -> {
                        if (solrFieldMap.containsKey(k)) {
                            final SolrIndexField solrField = solrFieldMap.get(k);
                            // If the field has changed then replace the field with the new definition
                            if (!solrField.equals(v)) {
                                // We need to replace this field.
                                try {
                                    final Map<String, Object> attributes = toAttributes(v);
                                    new ReplaceField(attributes).process(solrClient, document.getCollection());
                                    replaceCount.incrementAndGet();
                                } catch (final RuntimeException | SolrServerException | IOException e) {
                                    final String message = "Failed to replace field '" + k + "' - " + e.getMessage();
                                    messages.add(message);
                                    LOGGER.error(() -> message, e);
                                }
                            }
                        } else {
                            // We need to add this new field.
                            try {
                                final Map<String, Object> attributes = toAttributes(v);
                                new AddField(attributes).process(solrClient, document.getCollection());
                                addCount.incrementAndGet();
                            } catch (final RuntimeException | SolrServerException | IOException e) {
                                final String message = "Failed to add field '" + k + "' - " + e.getMessage();
                                messages.add(message);
                                LOGGER.error(() -> message, e);
                            }
                        }
                    });

                    // Delete fields.
                    if (document.getDeletedFields() != null) {
                        document.getDeletedFields().forEach(field -> {
                            if (solrFieldMap.containsKey(field.getFieldName())) {
                                try {
                                    new DeleteField(field.getFieldName()).process(solrClient, document.getCollection());
                                    deleteCount.incrementAndGet();
                                } catch (final RuntimeException | SolrServerException | IOException e) {
                                    final String message = "Failed to delete field '" + field.getFieldName() + "' - " + e.getMessage();
                                    messages.add(message);
                                    LOGGER.error(() -> message, e);
                                }
                            }
                        });
                        document.setDeletedFields(null);
                    }

                    // Now pull all fields back from Solr and refresh our doc.
                    solrFields = fetchSolrFields(solrClient, document.getCollection(), existingFieldMap);
                    solrFields.sort(Comparator.comparing(SolrIndexField::getFieldName, String.CASE_INSENSITIVE_ORDER));
                    document.setFields(solrFields);

                    messages.add("Replaced " + replaceCount.get() + " fields");
                    messages.add("Added " + addCount.get() + " fields");
                    messages.add("Deleted " + deleteCount.get() + " fields");

                } catch (final RuntimeException | SolrServerException | IOException e) {
                    messages.add(e.getMessage());
                    LOGGER.error(e::getMessage, e);
                }
            });

        } catch (final RuntimeException e) {
            messages.add(e.getMessage());
            LOGGER.error(e::getMessage, e);
        }

        document.setSolrSynchState(new SolrSynchState(System.currentTimeMillis(), messages));

        return store.writeDocument(document);
    }

    private List<SolrIndexField> fetchSolrFields(final SolrClient solrClient, final String collection, Map<String, SolrIndexField> existingFieldMap) throws SolrServerException, IOException {
        final FieldsResponse response = new Fields().process(solrClient, collection);
        final List<Map<String, Object>> fields = response.getFields();
        return fields
                .stream()
                .map(v -> {
                    final SolrIndexField field = fromAttributes(v);
                    field.setFieldUse(SolrIndexFieldType.FIELD);

                    final SolrIndexField existingField = existingFieldMap.get(field.getFieldName());
                    if (existingField != null) {
                        field.setFieldUse(existingField.getFieldUse());
                    }

                    return field;
                })
                .collect(Collectors.toList());
    }

    private SolrIndexField fromAttributes(final Map<String, Object> attributes) {
        final SolrIndexField field = new SolrIndexField();
        setString(attributes, "name", field::setFieldName);
        setString(attributes, "type", field::setFieldType);
        setString(attributes, "default", field::setDefaultValue);
        setBoolean(attributes, "stored", field::setStored);
        setBoolean(attributes, "indexed", field::setIndexed);
        setBoolean(attributes, "uninvertible", field::setUninvertible);
        setBoolean(attributes, "docValues", field::setDocValues);
        setBoolean(attributes, "multiValued", field::setMultiValued);
        setBoolean(attributes, "required", field::setRequired);
        setBoolean(attributes, "omitNorms", field::setOmitNorms);
        setBoolean(attributes, "omitTermFreqAndPositions", field::setOmitTermFreqAndPositions);
        setBoolean(attributes, "omitPositions", field::setOmitPositions);
        setBoolean(attributes, "termVectors", field::setTermVectors);
        setBoolean(attributes, "termPositions", field::setTermPositions);
        setBoolean(attributes, "termOffsets", field::setTermOffsets);
        setBoolean(attributes, "termPayloads", field::setTermPayloads);
        setBoolean(attributes, "sortMissingFirst", field::setSortMissingFirst);
        setBoolean(attributes, "sortMissingLast", field::setSortMissingLast);
        return field;
    }

    private Map<String, Object> toAttributes(final SolrIndexField field) {
        final Map<String, Object> attributes = new HashMap<>();
        putString(attributes, "name", field.getFieldName());
        putString(attributes, "type", field.getFieldType());
        putString(attributes, "default", field.getDefaultValue());
        putBoolean(attributes, "stored", field.isStored());
        putBoolean(attributes, "indexed", field.isIndexed());
        putBoolean(attributes, "uninvertible", field.isUninvertible());
        putBoolean(attributes, "docValues", field.isDocValues());
        putBoolean(attributes, "multiValued", field.isMultiValued());
        putBoolean(attributes, "required", field.isRequired());
        putBoolean(attributes, "omitNorms", field.isOmitNorms());
        putBoolean(attributes, "omitTermFreqAndPositions", field.isOmitTermFreqAndPositions());
        putBoolean(attributes, "omitPositions", field.isOmitPositions());
        putBoolean(attributes, "termVectors", field.isTermVectors());
        putBoolean(attributes, "termPositions", field.isTermPositions());
        putBoolean(attributes, "termOffsets", field.isTermOffsets());
        putBoolean(attributes, "termPayloads", field.isTermPayloads());
        putBoolean(attributes, "sortMissingFirst", field.isSortMissingFirst());
        putBoolean(attributes, "sortMissingLast", field.isSortMissingLast());
        return attributes;
    }


    private void setString(final Map<String, Object> map, final String key, final Consumer<String> consumer) {
        final Object v = map.get(key);
        if (v instanceof String) {
            consumer.accept((String) v);
        }
    }

    private void setBoolean(final Map<String, Object> map, final String key, final Consumer<Boolean> consumer) {
        final Object v = map.get(key);
        if (v instanceof Boolean) {
            consumer.accept((Boolean) v);
        }
    }

    private void putString(final Map<String, Object> map, final String key, final String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private void putBoolean(final Map<String, Object> map, final String key, final Boolean value) {
        if (value != null) {
            map.put(key, value);
        }
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
        final DocRef result = store.importDocument(docRef, dataMap, importState, importMode);

        // Make sure import doesn't change the sync state.
        if (importState.ok(importMode)) {
            final SolrIndex solrIndex = read(result.getUuid());
            if (solrIndex != null) {
                solrIndex.setSolrSynchState(null);
                writeDocument(solrIndex);
            }
        }

        return result;
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
        return SolrIndex.ENTITY_TYPE;
    }

    @Override
    public SolrIndex read(final String uuid) {
        return store.read(uuid);
    }

    @Override
    public SolrIndex update(final SolrIndex dataReceiptPolicy) {
        return store.update(dataReceiptPolicy);
    }

    @Override
    public List<DocRef> list() {
        return store.list();
    }
}
