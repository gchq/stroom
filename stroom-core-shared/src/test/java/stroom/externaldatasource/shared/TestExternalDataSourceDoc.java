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

package stroom.externaldatasource.shared;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestExternalDataSourceDoc {

    private static JdbcConnectionSettings jdbc() {
        return JdbcConnectionSettings.builder()
                .url("jdbc:mysql://localhost:3307/stroom")
                .credentialName("stroom-db-read-only")
                .connectionProperties(Map.of("useCursorFetch", "true"))
                .maxPoolSize(3)
                .fetchSize(500)
                .build();
    }

    private static AthenaConnectionSettings athena() {
        return AthenaConnectionSettings.builder()
                .region("eu-west-2")
                .workGroup("analysts")
                .catalog("AwsDataCatalog")
                .database("events")
                .outputLocation("s3://results-bucket/stroom/")
                .credentialName("athena-keys")
                .resultReuseMinutes(15)
                .build();
    }

    private static ExternalDataSourceDoc fullDoc() {
        return ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .name("HR Database")
                .version("version-1")
                .description("Staff records")
                .sourceType(ExternalDataSourceType.ATHENA)
                .jdbc(jdbc())
                .athena(athena())
                .timeField("created_at")
                .queryTimeoutSeconds(30)
                .maxRows(5_000)
                .build();
    }

    @Test
    void typeIsExternalDataSource() {
        assertThat(fullDoc().getType()).isEqualTo(ExternalDataSourceDoc.TYPE);
        assertThat(ExternalDataSourceDoc.DOCUMENT_TYPE.getType()).isEqualTo(ExternalDataSourceDoc.TYPE);
    }

    @Test
    void newDocumentsHaveDefaults() {
        final ExternalDataSourceDoc doc = ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .name("New")
                .build();

        assertThat(doc.getSourceType()).isEqualTo(ExternalDataSourceType.JDBC);
        assertThat(doc.getQueryTimeoutSeconds()).isEqualTo(ExternalDataSourceDoc.DEFAULT_QUERY_TIMEOUT_SECONDS);
        assertThat(doc.getMaxRows()).isEqualTo(ExternalDataSourceDoc.DEFAULT_MAX_ROWS);
        assertThat(doc.getJdbc()).isNull();
        assertThat(doc.getAthena()).isNull();
        assertThat(doc.getTimeField()).isNull();
    }

    @Test
    void survivesJson() {
        final ExternalDataSourceDoc doc = fullDoc();

        final String json = JsonUtil.writeValueAsString(doc);
        final ExternalDataSourceDoc fromJson = JsonUtil.readValue(json, ExternalDataSourceDoc.class);

        assertThat(fromJson).isEqualTo(doc);
        assertThat(fromJson.getJdbc()).isEqualTo(jdbc());
        assertThat(fromJson.getAthena()).isEqualTo(athena());
    }

    @Test
    void jsonOmitsUnsetValues() {
        final ExternalDataSourceDoc doc = new ExternalDataSourceDoc(
                "uuid-1", "Bare", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        final String json = JsonUtil.writeValueAsString(doc);

        assertThat(json)
                .doesNotContain("sourceType")
                .doesNotContain("jdbc")
                .doesNotContain("athena")
                .doesNotContain("csv")
                .doesNotContain("maxRows");
    }

    @Test
    void jsonReadsSourceTypeByName() {
        final String json = """
                {"type":"ExternalDataSource","uuid":"uuid-1","name":"Athena","sourceType":"ATHENA",
                "athena":{"region":"eu-west-2"}}""";

        final ExternalDataSourceDoc doc = JsonUtil.readValue(json, ExternalDataSourceDoc.class);

        assertThat(doc.getSourceType()).isEqualTo(ExternalDataSourceType.ATHENA);
        assertThat(doc.getAthena().getRegion()).isEqualTo("eu-west-2");
        assertThat(doc.getAthena().getWorkGroup())
                .as("defaults are applied where the settings are used, not stored")
                .isNull();
    }

    @Test
    void copyKeepsEveryValue() {
        final ExternalDataSourceDoc doc = fullDoc();

        assertThat(doc.copy().build()).isEqualTo(doc);
    }

    @Test
    void eachSettingTakesPartInEquality() {
        final ExternalDataSourceDoc doc = fullDoc();

        assertThat(doc.copy().description("Other").build()).isNotEqualTo(doc);
        assertThat(doc.copy().sourceType(ExternalDataSourceType.JDBC).build()).isNotEqualTo(doc);
        assertThat(doc.copy().jdbc(jdbc().copy().url("jdbc:postgresql://db/hr").build()).build())
                .isNotEqualTo(doc);
        assertThat(doc.copy().athena(athena().copy().database("other").build()).build()).isNotEqualTo(doc);
        assertThat(doc.copy().timeField("updated_at").build()).isNotEqualTo(doc);
        assertThat(doc.copy().queryTimeoutSeconds(31).build()).isNotEqualTo(doc);
        assertThat(doc.copy().maxRows(5_001).build()).isNotEqualTo(doc);
    }

    @Test
    void jdbcToStringHidesPropertyValues() {
        // A driver property can hold a password, and toString output ends up in logs.
        final JdbcConnectionSettings settings = JdbcConnectionSettings.builder()
                .url("jdbc:mysql://localhost:3307/stroom")
                .connectionProperties(Map.of("password", "secret-value"))
                .build();

        assertThat(settings.toString())
                .contains("password")
                .doesNotContain("secret-value");
    }

    @Test
    void sourceTypesHaveDisplayValues() {
        assertThat(ExternalDataSourceType.JDBC.getDisplayValue()).isEqualTo("JDBC");
        assertThat(ExternalDataSourceType.ATHENA.getDisplayValue()).isEqualTo("Amazon Athena");
    }
}
