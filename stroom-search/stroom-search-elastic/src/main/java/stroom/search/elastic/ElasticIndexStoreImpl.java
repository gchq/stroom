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

package stroom.search.elastic;

import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.StoreFactory;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.search.elastic.shared.ElasticDenseVectorSettings;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.security.api.SecurityContext;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class ElasticIndexStoreImpl
        extends AbstractDocumentStore<ElasticIndexDoc>
        implements ElasticIndexStore {

    private final ElasticIndexService elasticIndexService;

    @Inject
    public ElasticIndexStoreImpl(
            final StoreFactory storeFactory,
            final SecurityContext securityContext,
            final ElasticIndexService elasticIndexService,
            final ElasticIndexSerialiser serialiser) {
        super(storeFactory,
                securityContext,
                serialiser,
                ElasticIndexDoc.TYPE,
                ElasticIndexDoc::builder,
                ElasticIndexDoc::copy);
        this.elasticIndexService = elasticIndexService;
    }

    // super, not getStore(), in both: the base applies this type's VIEW and EDIT checks, and
    // getStore() is the deliberately unchecked handle.

    @Override
    public ElasticIndexDoc readDocument(final DocRef docRef) {
        final ElasticIndexDoc doc = super.readDocument(docRef);
        if (doc == null) {
            return null;
        }
        final List<ElasticIndexField> fields = elasticIndexService.getFields(doc);
        return ElasticVectorSettingsMigrator.migrate(doc.copy().fields(fields).build(), fields);
    }

    @Override
    public ElasticIndexDoc writeDocument(final ElasticIndexDoc document) {
        final List<ElasticIndexField> fields = elasticIndexService.getFields(document);
        final ElasticIndexDoc migrated = ElasticVectorSettingsMigrator.migrate(
                document.copy().fields(fields).build(), fields);
        ElasticFieldSettingsValidator.validate(migrated, fields);
        // The mapping details of each field are read afresh with the doc, so aren't stored with it, which would
        // make the doc large and change it whenever an index is added
        final ElasticIndexDoc written = super.writeDocument(migrated.copy()
                .fields(fields.stream()
                        .map(ElasticIndexStoreImpl::withoutMappingDetails)
                        .toList())
                .fieldSettings(dropDefaultSettings(migrated.getFieldSettings()))
                .build());
        return written == null
                ? null
                : written.copy().fields(fields).build();
    }

    private static ElasticIndexField withoutMappingDetails(final ElasticIndexField field) {
        return field.copy()
                .sourceIndices(null)
                .conflictingTypes(null)
                .mappingParameters(null)
                .queryOptionSet(null)
                .supportsTopValues(null)
                .build();
    }

    /// Drops settings entries that hold nothing but defaults, so removing a field's last setting reverts it to
    /// having no entry at all.
    private static List<ElasticIndexFieldSettings> dropDefaultSettings(
            final List<ElasticIndexFieldSettings> fieldSettings) {
        return fieldSettings.stream()
                .filter(settings -> !settings.isDefault())
                .toList();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected DependencyRemapFunction<ElasticIndexDoc> getDependencyRemapFunction() {
        return (doc, dependencyRemapper) -> doc
                .copy()
                .clusterRef(dependencyRemapper.remap(doc.getClusterRef()))
                .vectorGenerationModelRef(dependencyRemapper.remap(doc.getVectorGenerationModelRef()))
                .rerankModelRef(dependencyRemapper.remap(doc.getRerankModelRef()))
                .defaultExtractionPipeline(dependencyRemapper.remap(doc.getDefaultExtractionPipeline()))
                .fieldSettings(doc.getFieldSettings()
                        .stream()
                        .map(settings -> remapFieldSettings(settings, dependencyRemapper))
                        .toList())
                .build();
    }

    /// Remaps the model docs referenced by a field's vector settings, and the docs (such as dictionaries)
    /// referenced by a virtual field's group expressions.
    static ElasticIndexFieldSettings remapFieldSettings(final ElasticIndexFieldSettings settings,
                                                        final DependencyRemapper dependencyRemapper) {
        final ElasticIndexFieldSettings.Builder builder = settings.copy();
        final ElasticDenseVectorSettings denseVector = settings.getDenseVector();
        if (denseVector != null && denseVector.getConfig() != null) {
            final DenseVectorFieldConfig config = denseVector.getConfig();
            builder.denseVector(new ElasticDenseVectorSettings(
                    config.copy()
                            .embeddingModelRef(dependencyRemapper.remap(config.getEmbeddingModelRef()))
                            .rerankModelRef(dependencyRemapper.remap(config.getRerankModelRef()))
                            .build(),
                    denseVector.getRerankTextField(),
                    denseVector.getRerankScoreField()));
        }
        if (settings.getVirtualGroups() != null) {
            builder.virtualGroups(settings.getVirtualGroups()
                    .stream()
                    .map(group -> new ElasticVirtualFieldGroup(
                            group.getName(),
                            group.getExpression() == null
                                    ? null
                                    : dependencyRemapper.remapExpression(group.getExpression())))
                    .toList());
        }
        return builder.build();
    }
}
