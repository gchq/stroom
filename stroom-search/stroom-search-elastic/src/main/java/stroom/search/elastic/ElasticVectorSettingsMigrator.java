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

import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.FieldType;
import stroom.search.elastic.shared.ElasticDenseVectorSettings;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Moves the legacy, doc-level vector settings of an [ElasticIndexDoc] onto the settings of each of its
/// `dense_vector` fields.
///
/// The migration happens in memory when a doc is read. The migrated doc is only persisted when it is next saved.
public final class ElasticVectorSettingsMigrator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticVectorSettingsMigrator.class);
    private static final char FIELD_PATH_SEPARATOR = '.';

    private ElasticVectorSettingsMigrator() {
        // Static utility
    }

    /// Copies the doc's legacy vector settings onto every `dense_vector` field that has no vector settings of its
    /// own, then removes the legacy settings from the doc.
    ///
    /// If the doc has no legacy settings, or none of the given fields is a `dense_vector` field (for example because
    /// the index mapping could not be read), the doc is returned unchanged, so the legacy settings are not lost.
    ///
    /// @param doc    the doc to migrate
    /// @param fields the doc's fields, as read from the index mapping
    /// @return the migrated doc, or the same doc if there is nothing to migrate
    @SuppressWarnings("deprecation")
    public static ElasticIndexDoc migrate(final ElasticIndexDoc doc, final List<ElasticIndexField> fields) {
        if (doc == null || !doc.hasLegacyVectorSettings()) {
            return doc;
        }
        final List<ElasticIndexField> vectorFields = NullSafe.list(fields)
                .stream()
                .filter(field -> FieldType.DENSE_VECTOR.equals(field.getFldType()))
                .toList();
        if (vectorFields.isEmpty()) {
            LOGGER.debug("Not migrating legacy vector settings of '{}' as it has no dense_vector fields",
                    doc.getName());
            return doc;
        }

        final List<ElasticIndexFieldSettings> fieldSettings = new ArrayList<>(doc.getFieldSettings());
        for (final ElasticIndexField vectorField : vectorFields) {
            final String fieldName = vectorField.getFldName();
            final ElasticIndexFieldSettings existing = doc.findFieldSettings(fieldName);
            if (existing == null) {
                fieldSettings.add(ElasticIndexFieldSettings.builder(fieldName)
                        .denseVector(createDenseVectorSettings(doc, fieldName))
                        .build());
            } else if (existing.getDenseVector() == null) {
                fieldSettings.set(fieldSettings.indexOf(existing), existing.copy()
                        .denseVector(createDenseVectorSettings(doc, fieldName))
                        .build());
            }
        }

        LOGGER.debug("Migrated legacy vector settings of '{}' onto {} dense_vector fields",
                doc.getName(), vectorFields.size());
        return doc.copy()
                .fieldSettings(fieldSettings)
                .clearLegacyVectorSettings()
                .build();
    }

    @SuppressWarnings("deprecation")
    private static ElasticDenseVectorSettings createDenseVectorSettings(final ElasticIndexDoc doc,
                                                                        final String vectorFieldName) {
        // A nearest neighbour count of 0 leaves `k` unset, and a batch the size of a scroll page reranks each page in
        // one call, both as before the migration
        final DenseVectorFieldConfig config = DenseVectorFieldConfig.builder()
                .embeddingModelRef(doc.getVectorGenerationModelRef())
                .nearestNeighbourCount(0)
                .rerankModelRef(doc.getRerankModelRef())
                .rerankBatchSize(doc.getSearchScrollSize())
                .rerankScoreMinimum(Objects.requireNonNullElse(
                        doc.getRerankScoreMinimum(), DenseVectorFieldConfig.DEFAULT_RERANK_SCORE_MINIMUM))
                .build();
        final String textSuffix = Objects.requireNonNullElse(
                doc.getRerankTextFieldSuffix(), ElasticIndexDoc.LEGACY_DEFAULT_RERANK_TEXT_FIELD_SUFFIX);
        final String scoreSuffix = Objects.requireNonNullElse(
                doc.getRerankScoreFieldSuffix(), ElasticIndexDoc.LEGACY_DEFAULT_RERANK_SCORE_FIELD_SUFFIX);
        return new ElasticDenseVectorSettings(
                config,
                getSuffixedFieldName(vectorFieldName, textSuffix),
                getSuffixedFieldName(vectorFieldName, scoreSuffix));
    }

    /// Replaces the last path segment of a field name with a suffix, the naming rule legacy docs used to find a
    /// vector field's rerank text and score fields. For example `content.vector` with suffix `.text` gives
    /// `content.text`.
    ///
    /// @param fieldName the full name of the vector field
    /// @param suffix    the suffix, including its leading `.`
    /// @return the suffixed name, or `null` if the field name has no `.` to replace after
    static String getSuffixedFieldName(final String fieldName, final String suffix) {
        final int idx = fieldName.lastIndexOf(FIELD_PATH_SEPARATOR);
        if (idx <= 0 || idx == fieldName.length() - 1) {
            return null;
        }
        return fieldName.substring(0, idx) + suffix;
    }
}
