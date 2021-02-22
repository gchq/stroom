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

import stroom.datasource.api.v2.DataSourceField;
import stroom.docstore.server.JsonSerialiser;
import stroom.docstore.server.Store;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Message;

import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.GetFieldMappingsRequest;
import org.elasticsearch.client.indices.GetFieldMappingsResponse;
import org.elasticsearch.client.indices.GetFieldMappingsResponse.FieldMappingMetadata;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Singleton
public class ElasticIndexStoreImpl implements ElasticIndexStore {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexStoreImpl.class);

    private final Store<ElasticIndex> store;
    private final ElasticClientCache elasticClientCache;

    @Inject
    public ElasticIndexStoreImpl(final Store<ElasticIndex> store,
                                 final ElasticClientCache elasticClientCache
    ) {
        this.store = store;
        this.elasticClientCache = elasticClientCache;

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
        document.setDataSourceFields(getDataSourceFields(document));
        document.setFields(getFields(document));

        return store.writeDocument(document);
    }

    public List<DataSourceField> getDataSourceFields(ElasticIndex index) {
        final Map<String, FieldMappingMetadata> fieldMappings = getFieldMappings(index);

        return fieldMappings.entrySet().stream()
            .map(field -> {
                final Object properties = field.getValue().sourceAsMap().get(field.getKey());

                if (properties instanceof Map) {
                    final Map<String, Object> propertiesMap = (Map<String, Object>) properties;
                    final String nativeType = (String) propertiesMap.get("type");

                    try {
                        final ElasticIndexFieldType elasticFieldType = ElasticIndexFieldType.fromNativeType(field.getValue().fullName(), nativeType);
                        return new DataSourceField.Builder()
                            .type(elasticFieldType.getDataSourceFieldType())
                            .name(field.getValue().fullName())
                            .queryable(true)
                            .addConditions(elasticFieldType.getSupportedConditions().toArray(new Condition[0]))
                            .build();
                    }
                    catch (IllegalArgumentException e) {
                        LOGGER.warn(e::getMessage);
                        return null;
                    }
                }
                else {
                    LOGGER.warn(() -> "Mapping properties for field '" + field.getKey() + "' were in an unrecognised format. Field ignored.");
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private List<ElasticIndexField> getFields(final ElasticIndex index) {
        return new ArrayList<>(getFieldsMap(index).values());
    }

    public Map<String, ElasticIndexField> getFieldsMap(final ElasticIndex index) {
        final Map<String, FieldMappingMetadata> fieldMappings = getFieldMappings(index);
        final Map<String, ElasticIndexField> fieldsMap = new HashMap<>();

        fieldMappings.entrySet().forEach(mapping -> {
            final Object properties = mapping.getValue().sourceAsMap().get(mapping.getKey());

            if (properties instanceof Map) {
                final Map<String, Object> propertiesMap = (Map<String, Object>) properties;
                final String fieldName = mapping.getValue().fullName();
                final String fieldType = (String) propertiesMap.get("type");
                final boolean stored = Boolean.parseBoolean((String) propertiesMap.get("stored"));

                try {
                    fieldsMap.put(fieldName, new ElasticIndexField(
                        ElasticIndexFieldType.fromNativeType(fieldName, fieldType),
                        fieldName,
                        fieldType,
                        stored,
                        true
                    ));
                }
                catch (IllegalArgumentException e) {
                    LOGGER.warn(e::getMessage, e);
                }
            }
            else {
                LOGGER.warn(() -> "Mapping properties for field '" + mapping.getKey() + "' were in an unrecognised format. Field ignored.");
            }
        });

        return fieldsMap;
    }

    private Map<String, FieldMappingMetadata> getFieldMappings(final ElasticIndex elasticIndex) {
        try {
            return elasticClientCache.contextResult(elasticIndex.getConnectionConfig(), elasticClient -> {
                final String indexName = elasticIndex.getIndexName();
                final GetFieldMappingsRequest request = new GetFieldMappingsRequest();
                request.indices(indexName);
                request.fields("*");

                try {
                    final GetFieldMappingsResponse response = elasticClient.indices().getFieldMapping(request, RequestOptions.DEFAULT);
                    final Map<String, Map<String, FieldMappingMetadata>> allMappings = response.mappings();

                    return allMappings.get(indexName);
                }
                catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                }

                return null;
            });
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            return new HashMap<>();
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
