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

package stroom.search.solr;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.query.api.datasource.FieldType;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexField;
import stroom.search.solr.shared.SolrSynchState;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Message;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.AddField;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.DeleteField;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.Fields;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.ReplaceField;
import org.apache.solr.client.solrj.response.schema.SchemaResponse.FieldsResponse;

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
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class SolrIndexStoreImpl implements SolrIndexStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrIndexStoreImpl.class);

    private static final Pattern VALID_FIELD_NAME_PATTERN = Pattern.compile(SolrIndexField.VALID_FIELD_NAME_PATTERN);

    private final Store<SolrIndexDoc> store;
    private final SolrIndexClientCache solrIndexClientCache;

    @Inject
    SolrIndexStoreImpl(final StoreFactory storeFactory,
                       final SolrIndexClientCache solrIndexClientCache,
                       final SolrIndexSerialiser serialiser) {
        this.store = storeFactory.createStore(
                serialiser,
                SolrIndexDoc.TYPE,
                SolrIndexDoc::builder,
                SolrIndexDoc::copy);
        this.solrIndexClientCache = solrIndexClientCache;
    }

    // ---------------------------------------------------------------------
    // START OF ExplorerActionHandler
    // ---------------------------------------------------------------------

    @Override
    public DocRef createDocument(final String name) {
        return store.createDocument(name);
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        return store.copyDocument(docRef.getUuid(), newName);
    }

    @Override
    public DocRef moveDocument(final DocRef docRef) {
        return store.moveDocument(docRef);
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        return store.renameDocument(docRef, name);
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        store.deleteDocument(docRef);
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        return store.info(docRef);
    }

    // ---------------------------------------------------------------------
    // END OF ExplorerActionHandler
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF HasDependencies
    // ---------------------------------------------------------------------

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return store.getDependencies(null);
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return store.getDependencies(docRef, null);
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        store.remapDependencies(docRef, remappings, null);
    }

    // ---------------------------------------------------------------------
    // END OF HasDependencies
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF DocumentActionHandler
    // ---------------------------------------------------------------------

    @Override
    public SolrIndexDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public SolrIndexDoc writeDocument(final SolrIndexDoc document) {
        final SolrIndexDoc.Builder builder = document.copy();

        final List<String> messages = new ArrayList<>();
        final AtomicInteger replaceCount = new AtomicInteger();
        final AtomicInteger addCount = new AtomicInteger();
        final AtomicInteger deleteCount = new AtomicInteger();

        // Test for invalid field names.
        if (document.getFields() != null) {
            document.getFields().forEach(solrIndexField -> {
                if (solrIndexField.getFldName() == null) {
                    throw new RuntimeException("Null field name");
                } else if (!VALID_FIELD_NAME_PATTERN.matcher(solrIndexField.getFldName()).matches()) {
                    throw new RuntimeException("Invalid field name " + solrIndexField.getFldName());
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
                                .collect(Collectors.toMap(SolrIndexField::getFldName, Function.identity()));
                    }

                    List<SolrIndexField> solrFields = fetchSolrFields(solrClient,
                            document.getCollection(),
                            existingFieldMap);

                    // Which fields are missing from Solr?
                    final Map<String, SolrIndexField> solrFieldMap = solrFields.stream().collect(Collectors.toMap(
                            SolrIndexField::getFldName,
                            Function.identity()));
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
                            if (solrFieldMap.containsKey(field.getFldName())) {
                                try {
                                    new DeleteField(field.getFldName()).process(solrClient, document.getCollection());
                                    deleteCount.incrementAndGet();
                                } catch (final RuntimeException | SolrServerException | IOException e) {
                                    final String message = "Failed to delete field '" + field.getFldName() +
                                                           "' - " + e.getMessage();
                                    messages.add(message);
                                    LOGGER.error(() -> message, e);
                                }
                            }
                        });
                        builder.deletedFields(null);
                    }

                    // Now pull all fields back from Solr and refresh our doc.
                    solrFields = fetchSolrFields(solrClient, document.getCollection(), existingFieldMap);
                    solrFields.sort(Comparator.comparing(SolrIndexField::getFldName, String.CASE_INSENSITIVE_ORDER));
                    builder.fields(solrFields);

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

        builder.solrSynchState(new SolrSynchState(System.currentTimeMillis(), messages));

        return store.writeDocument(builder.build());
    }

    private List<SolrIndexField> fetchSolrFields(final SolrClient solrClient,
                                                 final String collection,
                                                 final Map<String, SolrIndexField> existingFieldMap)
            throws SolrServerException, IOException {
        final FieldsResponse response = new Fields().process(solrClient, collection);
        final List<Map<String, Object>> fields = response.getFields();
        return fields
                .stream()
                .map(v -> {
                    final SolrIndexField field = fromAttributes(v);
                    final SolrIndexField.Builder builder = field.copy();
                    builder.fldType(FieldType.TEXT);

                    final SolrIndexField existingField = existingFieldMap.get(field.getFldName());
                    if (existingField != null) {
                        builder.fldType(existingField.getFldType());
                    }

                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    private SolrIndexField fromAttributes(final Map<String, Object> attributes) {
        final SolrIndexField.Builder builder = SolrIndexField.builder();
        setString(attributes, "name", builder::fldName);
        setString(attributes, "type", builder::nativeType);
        setString(attributes, "default", builder::defaultValue);
        setBoolean(attributes, "stored", builder::stored);
        setBoolean(attributes, "indexed", builder::indexed);
        setBoolean(attributes, "uninvertible", builder::uninvertible);
        setBoolean(attributes, "docValues", builder::docValues);
        setBoolean(attributes, "multiValued", builder::multiValued);
        setBoolean(attributes, "required", builder::required);
        setBoolean(attributes, "omitNorms", builder::omitNorms);
        setBoolean(attributes, "omitTermFreqAndPositions", builder::omitTermFreqAndPositions);
        setBoolean(attributes, "omitPositions", builder::omitPositions);
        setBoolean(attributes, "termVectors", builder::termVectors);
        setBoolean(attributes, "termPositions", builder::termPositions);
        setBoolean(attributes, "termOffsets", builder::termOffsets);
        setBoolean(attributes, "termPayloads", builder::termPayloads);
        setBoolean(attributes, "sortMissingFirst", builder::sortMissingFirst);
        setBoolean(attributes, "sortMissingLast", builder::sortMissingLast);
        return builder.build();
    }

    private Map<String, Object> toAttributes(final SolrIndexField field) {
        final Map<String, Object> attributes = new HashMap<>();
        putString(attributes, "name", field.getFldName());
        putString(attributes, "type", field.getNativeType());
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

    // ---------------------------------------------------------------------
    // END OF DocumentActionHandler
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // START OF ImportExportActionHandler
    // ---------------------------------------------------------------------

    @Override
    public Set<DocRef> listDocuments() {
        return store.listDocuments();
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final Map<String, byte[]> dataMap,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        return store.importDocument(docRef, dataMap, importState, importSettings);
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef,
                                              final boolean omitAuditFields,
                                              final List<Message> messageList) {
        return store.exportDocument(docRef, omitAuditFields, messageList, doc ->
                doc.copy().solrSynchState(null).build());
    }

    @Override
    public String getType() {
        return store.getType();
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        return null;
    }

    // ---------------------------------------------------------------------
    // END OF ImportExportActionHandler
    // ---------------------------------------------------------------------

    @Override
    public List<DocRef> list() {
        return store.list();
    }

    @Override
    public List<DocRef> findByNames(final List<String> name, final boolean allowWildCards) {
        return store.findByNames(name, allowWildCards);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }
}
