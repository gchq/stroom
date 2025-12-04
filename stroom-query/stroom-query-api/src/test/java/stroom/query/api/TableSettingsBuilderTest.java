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

package stroom.query.api;

import stroom.query.api.Format.Type;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TableSettingsBuilderTest {
    @Test
    void doesBuild() {
        final Boolean extractValues = true;
        final Boolean showDetail = false;
        final String queryId = "someQueryId";

        // Extraction Pipeline
        final String extractPipelineName = "pipelineName";
        final String extractPipelineType = "pipelineType";
        final String extractPipelineUuid = UUID.randomUUID().toString();

        // Field 1
        final String field1Name = "field1";
        final Integer field1Group = 57;
        final Integer field1SortOrder = 2;
        final Sort.SortDirection field1SortDirection = Sort.SortDirection.DESCENDING;
        final String field1Expression = "someExpression";
        final String field1FilterExcludes = "stuff to exclude **";
        final String field1FilterIncludes = "stuff to include &&";
        final Integer field1NumberFormatDecimalPlaces = 5;
        final Boolean field1NumberFormatUseSeperator = true;

        // Field 2
        final String field2Name = "field2";
        final Integer field2Group = 57;
        final Integer field2SortOrder = 3;
        final Sort.SortDirection field2SortDirection = Sort.SortDirection.ASCENDING;
        final String field2Expression = "someExpression";
        final String field2FilterExcludes = "stuff to exclude ** field 1";
        final String field2FilterIncludes = "stuff to include field 2&&";
        final Integer field2NumberFormatDecimalPlaces = 6;
        final Boolean field2NumberFormatUseSeperator = false;

        final TableSettings tableSettings = TableSettings
                .builder()
                .extractValues(extractValues)
                .showDetail(showDetail)
                .queryId(queryId)
                .addColumns(Column
                        .builder()
                        .id(field1Name)
                        .name(field1Name)
                        .group(field1Group)
                        .sort(Sort
                                .builder()
                                .order(field1SortOrder)
                                .direction(field1SortDirection)
                                .build())
                        .expression(field1Expression)
                        .filter(IncludeExcludeFilter
                                .builder()
                                .includes(field1FilterIncludes)
                                .excludes(field1FilterExcludes)
                                .build())
                        .format(Format
                                .builder()
                                .type(Type.NUMBER)
                                .settings(NumberFormatSettings
                                        .builder()
                                        .decimalPlaces(field1NumberFormatDecimalPlaces)
                                        .useSeparator(field1NumberFormatUseSeperator)
                                        .build())
                                .build())
                        .build())
                .addColumns(Column
                        .builder()
                        .id(field2Name)
                        .name(field2Name)
                        .group(field2Group)
                        .sort(Sort
                                .builder()
                                .order(field2SortOrder)
                                .direction(field2SortDirection)
                                .build())
                        .expression(field2Expression)
                        .filter(IncludeExcludeFilter
                                .builder()
                                .includes(field2FilterIncludes)
                                .excludes(field2FilterExcludes)
                                .build())
                        .format(Format
                                .builder()
                                .type(Type.NUMBER)
                                .settings(NumberFormatSettings
                                        .builder()
                                        .decimalPlaces(field2NumberFormatDecimalPlaces)
                                        .useSeparator(field2NumberFormatUseSeperator)
                                        .build())
                                .build())
                        .build())
                .extractionPipeline(extractPipelineType, extractPipelineUuid, extractPipelineName)
                .build();

        assertThat(tableSettings.extractValues()).isEqualTo(extractValues);
        assertThat(tableSettings.getShowDetail()).isEqualTo(showDetail);
        assertThat(tableSettings.getQueryId()).isEqualTo(queryId);

        assertThat(tableSettings.getExtractionPipeline()).isNotNull();
        assertThat(tableSettings.getExtractionPipeline().getName()).isEqualTo(extractPipelineName);
        assertThat(tableSettings.getExtractionPipeline().getUuid()).isEqualTo(extractPipelineUuid);
        assertThat(tableSettings.getExtractionPipeline().getType()).isEqualTo(extractPipelineType);

        assertThat(tableSettings.getColumns()).hasSize(2);
        final Column column1 = tableSettings.getColumns().get(0);
        assertThat(column1.getName()).isEqualTo(field1Name);
        assertThat(column1.getExpression()).isEqualTo(field1Expression);
        assertThat(column1.getGroup()).isEqualTo(field1Group);

        assertThat(column1.getSort()).isNotNull();
        assertThat(column1.getSort().getOrder()).isEqualTo(field1SortOrder);
        assertThat(column1.getSort().getDirection()).isEqualTo(field1SortDirection);

        assertThat(column1.getFilter()).isNotNull();
        assertThat(column1.getFilter().getExcludes()).isEqualTo(field1FilterExcludes);
        assertThat(column1.getFilter().getIncludes()).isEqualTo(field1FilterIncludes);

        assertThat(column1.getFormat()).isNotNull();
        assertThat(column1.getFormat().getType()).isEqualTo(Format.Type.NUMBER);
        assertThat(column1.getFormat().getSettings()).isNotNull();
        assertThat(((NumberFormatSettings) column1.getFormat().getSettings()).getDecimalPlaces())
                .isEqualTo(field1NumberFormatDecimalPlaces);
        assertThat(((NumberFormatSettings) column1.getFormat().getSettings()).getUseSeparator())
                .isEqualTo(field1NumberFormatUseSeperator);

        final Column column2 = tableSettings.getColumns().get(1);
        assertThat(column2.getName()).isEqualTo(field2Name);
        assertThat(column2.getExpression()).isEqualTo(field2Expression);
        assertThat(column2.getGroup()).isEqualTo(field2Group);

        assertThat(column2.getSort()).isNotNull();
        assertThat(column2.getSort().getOrder()).isEqualTo(field2SortOrder);
        assertThat(column2.getSort().getDirection()).isEqualTo(field2SortDirection);

        assertThat(column2.getFilter()).isNotNull();
        assertThat(column2.getFilter().getExcludes()).isEqualTo(field2FilterExcludes);
        assertThat(column2.getFilter().getIncludes()).isEqualTo(field2FilterIncludes);

        assertThat(column2.getFormat()).isNotNull();
        assertThat(column2.getFormat().getType()).isEqualTo(Format.Type.NUMBER);
        assertThat(column2.getFormat().getSettings()).isNotNull();
        assertThat(((NumberFormatSettings) column2.getFormat().getSettings()).getDecimalPlaces())
                .isEqualTo(field2NumberFormatDecimalPlaces);
        assertThat(((NumberFormatSettings) column2.getFormat().getSettings()).getUseSeparator())
                .isEqualTo(field2NumberFormatUseSeperator);

    }
}
