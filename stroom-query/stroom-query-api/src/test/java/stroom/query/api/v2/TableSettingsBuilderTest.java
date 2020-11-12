package stroom.query.api.v2;

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

        final TableSettings tableSettings = new TableSettings.Builder()
                .extractValues(extractValues)
                .showDetail(showDetail)
                .queryId(queryId)
                .addFields(new Field.Builder()
                        .id(field1Name)
                        .name(field1Name)
                        .group(field1Group)
                        .sort(new Sort.Builder()
                                .order(field1SortOrder)
                                .direction(field1SortDirection)
                                .build())
                        .expression(field1Expression)
                        .filter(new Filter.Builder()
                                .includes(field1FilterIncludes)
                                .excludes(field1FilterExcludes)
                                .build())
                        .format(new Format.Builder().number(
                                new NumberFormat.Builder()
                                        .decimalPlaces(field1NumberFormatDecimalPlaces)
                                        .useSeparator(field1NumberFormatUseSeperator)
                                        .build())
                                .build())
                        .build())
                .addFields(new Field.Builder()
                        .id(field2Name)
                        .name(field2Name)
                        .group(field2Group)
                        .sort(new Sort.Builder()
                                .order(field2SortOrder)
                                .direction(field2SortDirection)
                                .build())
                        .expression(field2Expression)
                        .filter(new Filter.Builder()
                                .includes(field2FilterIncludes)
                                .excludes(field2FilterExcludes)
                                .build())
                        .format(new Format.Builder().number(
                                new NumberFormat.Builder()
                                        .decimalPlaces(field2NumberFormatDecimalPlaces)
                                        .useSeparator(field2NumberFormatUseSeperator)
                                        .build())
                                .build())
                        .build())
                .extractionPipeline(extractPipelineType, extractPipelineUuid, extractPipelineName)
                .build();

        assertThat(tableSettings.getExtractValues()).isEqualTo(extractValues);
        assertThat(tableSettings.getShowDetail()).isEqualTo(showDetail);
        assertThat(tableSettings.getQueryId()).isEqualTo(queryId);

        assertThat(tableSettings.getExtractionPipeline()).isNotNull();
        assertThat(tableSettings.getExtractionPipeline().getName()).isEqualTo(extractPipelineName);
        assertThat(tableSettings.getExtractionPipeline().getUuid()).isEqualTo(extractPipelineUuid);
        assertThat(tableSettings.getExtractionPipeline().getType()).isEqualTo(extractPipelineType);

        assertThat(tableSettings.getFields()).hasSize(2);
        final Field field1 = tableSettings.getFields().get(0);
        assertThat(field1.getName()).isEqualTo(field1Name);
        assertThat(field1.getExpression()).isEqualTo(field1Expression);
        assertThat(field1.getGroup()).isEqualTo(field1Group);

        assertThat(field1.getSort()).isNotNull();
        assertThat(field1.getSort().getOrder()).isEqualTo(field1SortOrder);
        assertThat(field1.getSort().getDirection()).isEqualTo(field1SortDirection);

        assertThat(field1.getFilter()).isNotNull();
        assertThat(field1.getFilter().getExcludes()).isEqualTo(field1FilterExcludes);
        assertThat(field1.getFilter().getIncludes()).isEqualTo(field1FilterIncludes);

        assertThat(field1.getFormat()).isNotNull();
        assertThat(field1.getFormat().getType()).isEqualTo(Format.Type.NUMBER);
        assertThat(field1.getFormat().getNumberFormat()).isNotNull();
        assertThat(field1.getFormat().getDateTimeFormat()).isNull();
        assertThat(field1.getFormat().getNumberFormat().getDecimalPlaces()).isEqualTo(field1NumberFormatDecimalPlaces);
        assertThat(field1.getFormat().getNumberFormat().getUseSeparator()).isEqualTo(field1NumberFormatUseSeperator);

        final Field field2 = tableSettings.getFields().get(1);
        assertThat(field2.getName()).isEqualTo(field2Name);
        assertThat(field2.getExpression()).isEqualTo(field2Expression);
        assertThat(field2.getGroup()).isEqualTo(field2Group);

        assertThat(field2.getSort()).isNotNull();
        assertThat(field2.getSort().getOrder()).isEqualTo(field2SortOrder);
        assertThat(field2.getSort().getDirection()).isEqualTo(field2SortDirection);

        assertThat(field2.getFilter()).isNotNull();
        assertThat(field2.getFilter().getExcludes()).isEqualTo(field2FilterExcludes);
        assertThat(field2.getFilter().getIncludes()).isEqualTo(field2FilterIncludes);

        assertThat(field2.getFormat()).isNotNull();
        assertThat(field2.getFormat().getType()).isEqualTo(Format.Type.NUMBER);
        assertThat(field2.getFormat().getNumberFormat()).isNotNull();
        assertThat(field2.getFormat().getDateTimeFormat()).isNull();
        assertThat(field2.getFormat().getNumberFormat().getDecimalPlaces()).isEqualTo(field2NumberFormatDecimalPlaces);
        assertThat(field2.getFormat().getNumberFormat().getUseSeparator()).isEqualTo(field2NumberFormatUseSeperator);

    }
}
