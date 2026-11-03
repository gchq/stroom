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

package stroom.search.elastic;

import stroom.docref.DocRef;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.FieldType;
import stroom.search.elastic.shared.ElasticDenseVectorSettings;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class TestElasticVectorSettingsMigrator {

    private static final DocRef EMBEDDING_MODEL = new DocRef("OpenAIModel", "embed-uuid", "embed");
    private static final DocRef RERANK_MODEL = new DocRef("OpenAIModel", "rerank-uuid", "rerank");
    private static final List<ElasticIndexField> FIELDS = List.of(
            field("content.vector", FieldType.DENSE_VECTOR),
            field("title.vector", FieldType.DENSE_VECTOR),
            field("content.text", FieldType.TEXT));

    @Test
    void testMigrate_copiesLegacySettingsToEachVectorField() {
        final ElasticIndexDoc doc = legacyDoc()
                .rerankTextFieldSuffix(".body")
                .rerankScoreFieldSuffix(".relevance")
                .rerankScoreMinimum(0.6F)
                .searchScrollSize(500)
                .build();

        final ElasticIndexDoc migrated = ElasticVectorSettingsMigrator.migrate(doc, FIELDS);

        assertThat(migrated.hasLegacyVectorSettings()).isFalse();
        assertThat(migrated.getFieldSettings())
                .extracting(ElasticIndexFieldSettings::getFieldName)
                .containsExactly("content.vector", "title.vector");
        final ElasticDenseVectorSettings content = migrated.findFieldSettings("content.vector").getDenseVector();
        assertThat(content.getRerankTextField()).isEqualTo("content.body");
        assertThat(content.getRerankScoreField()).isEqualTo("content.relevance");
        final DenseVectorFieldConfig config = content.getConfig();
        assertThat(config.getEmbeddingModelRef()).isEqualTo(EMBEDDING_MODEL);
        assertThat(config.getRerankModelRef()).isEqualTo(RERANK_MODEL);
        assertThat(config.getRerankScoreMinimum()).isEqualTo(0.6F);
        // Behaviour is unchanged: `k` is left unset, and each scroll page is reranked in one call
        assertThat(config.getNearestNeighbourCount()).isZero();
        assertThat(config.getRerankBatchSize()).isEqualTo(500);
    }

    @Test
    void testMigrate_appliesLegacyDefaults() {
        final ElasticIndexDoc migrated = ElasticVectorSettingsMigrator.migrate(legacyDoc().build(), FIELDS);

        final ElasticDenseVectorSettings title = migrated.findFieldSettings("title.vector").getDenseVector();
        assertThat(title.getRerankTextField()).isEqualTo("title.text");
        assertThat(title.getRerankScoreField()).isEqualTo("title.score");
        assertThat(title.getConfig().getRerankScoreMinimum())
                .isEqualTo(DenseVectorFieldConfig.DEFAULT_RERANK_SCORE_MINIMUM);
    }

    @Test
    void testMigrate_keepsExistingVectorSettings() {
        final ElasticDenseVectorSettings own = new ElasticDenseVectorSettings(
                DenseVectorFieldConfig.builder().build(), "content.text", "content.score");
        final ElasticIndexDoc doc = legacyDoc()
                .fieldSettings(List.of(
                        ElasticIndexFieldSettings.builder("content.vector").denseVector(own).build(),
                        ElasticIndexFieldSettings.builder("title.vector").enabled(false).build()))
                .build();

        final ElasticIndexDoc migrated = ElasticVectorSettingsMigrator.migrate(doc, FIELDS);

        assertThat(migrated.findFieldSettings("content.vector").getDenseVector()).isEqualTo(own);
        // Existing settings without vector settings gain them, keeping their other settings
        final ElasticIndexFieldSettings title = migrated.findFieldSettings("title.vector");
        assertThat(title.isEnabled()).isFalse();
        assertThat(title.getDenseVector().getConfig().getEmbeddingModelRef()).isEqualTo(EMBEDDING_MODEL);
    }

    @Test
    void testMigrate_noModelsOnlyClearsLegacySettings() {
        // Older docs stored the default suffixes and minimum even without any models
        final ElasticIndexDoc doc = ElasticIndexDoc.builder()
                .uuid("uuid")
                .rerankTextFieldSuffix(".text")
                .rerankScoreFieldSuffix(".score")
                .rerankScoreMinimum(0.8F)
                .build();

        final ElasticIndexDoc migrated = ElasticVectorSettingsMigrator.migrate(doc, FIELDS);

        assertThat(migrated.hasLegacyVectorSettings()).isFalse();
        assertThat(migrated.getFieldSettings()).isEmpty();
    }

    @Test
    void testMigrate_noLegacySettings() {
        final ElasticIndexDoc doc = ElasticIndexDoc.builder().uuid("uuid").build();

        assertThat(ElasticVectorSettingsMigrator.migrate(doc, FIELDS)).isSameAs(doc);
    }

    @Test
    void testMigrate_noVectorFieldsKeepsLegacySettings() {
        // For example, when the index mapping could not be read
        final ElasticIndexDoc doc = legacyDoc().build();

        final ElasticIndexDoc migrated = ElasticVectorSettingsMigrator.migrate(doc, List.of());

        assertThat(migrated).isSameAs(doc);
        assertThat(migrated.hasLegacyVectorSettings()).isTrue();
    }

    @Test
    void testGetSuffixedFieldName() {
        assertThat(ElasticVectorSettingsMigrator.getSuffixedFieldName("content.vector", ".text"))
                .isEqualTo("content.text");
        assertThat(ElasticVectorSettingsMigrator.getSuffixedFieldName("a.b.vector", ".score"))
                .isEqualTo("a.b.score");
        assertThat(ElasticVectorSettingsMigrator.getSuffixedFieldName("vector", ".text"))
                .isNull();
        assertThat(ElasticVectorSettingsMigrator.getSuffixedFieldName("trailing.", ".text"))
                .isNull();
        assertThat(ElasticVectorSettingsMigrator.getSuffixedFieldName(".leading", ".text"))
                .isNull();
    }

    private static ElasticIndexDoc.Builder legacyDoc() {
        return ElasticIndexDoc.builder()
                .uuid("uuid")
                .name("test")
                .vectorGenerationModelRef(EMBEDDING_MODEL)
                .rerankModelRef(RERANK_MODEL);
    }

    private static ElasticIndexField field(final String name, final FieldType type) {
        return ElasticIndexField.builder().fldName(name).fldType(type).build();
    }
}
