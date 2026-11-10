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

package stroom.sqldatasource.shared;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSqlDataSourceDoc {

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

    private static SqlDataSourceDoc fullDoc() {
        return SqlDataSourceDoc.builder()
                .uuid("uuid-1")
                .name("HR Database")
                .version("version-1")
                .description("Staff records")
                .connectionType(SqlConnectionType.ATHENA)
                .jdbc(jdbc())
                .athena(athena())
                .timeField("created_at")
                .queryTimeoutSeconds(30)
                .maxRows(5_000)
                .build();
    }

    @Test
    void typeIsSqlDataSource() {
        assertThat(fullDoc().getType()).isEqualTo(SqlDataSourceDoc.TYPE);
        assertThat(SqlDataSourceDoc.DOCUMENT_TYPE.getType()).isEqualTo(SqlDataSourceDoc.TYPE);
    }

    @Test
    void newDocumentsHaveDefaults() {
        final SqlDataSourceDoc doc = SqlDataSourceDoc.builder()
                .uuid("uuid-1")
                .name("New")
                .build();

        assertThat(doc.getConnectionType()).isEqualTo(SqlConnectionType.JDBC);
        assertThat(doc.getQueryTimeoutSeconds()).isEqualTo(SqlDataSourceDoc.DEFAULT_QUERY_TIMEOUT_SECONDS);
        assertThat(doc.getMaxRows()).isEqualTo(SqlDataSourceDoc.DEFAULT_MAX_ROWS);
        assertThat(doc.getJdbc()).isNull();
        assertThat(doc.getAthena()).isNull();
        assertThat(doc.getTimeField()).isNull();
    }

    @Test
    void survivesJson() {
        final SqlDataSourceDoc doc = fullDoc();

        final String json = JsonUtil.writeValueAsString(doc);
        final SqlDataSourceDoc fromJson = JsonUtil.readValue(json, SqlDataSourceDoc.class);

        assertThat(fromJson).isEqualTo(doc);
        assertThat(fromJson.getJdbc()).isEqualTo(jdbc());
        assertThat(fromJson.getAthena()).isEqualTo(athena());
    }

    @Test
    void jsonOmitsUnsetValues() {
        final SqlDataSourceDoc doc = new SqlDataSourceDoc(
                "uuid-1", "Bare", null, null, null, null, null,
                null, null, null, null, null, null, null);

        final String json = JsonUtil.writeValueAsString(doc);

        assertThat(json)
                .doesNotContain("connectionType")
                .doesNotContain("jdbc")
                .doesNotContain("athena")
                .doesNotContain("maxRows");
    }

    @Test
    void jsonReadsConnectionTypeByName() {
        final String json = """
                {"type":"SqlDataSource","uuid":"uuid-1","name":"Athena","connectionType":"ATHENA",
                "athena":{"region":"eu-west-2"}}""";

        final SqlDataSourceDoc doc = JsonUtil.readValue(json, SqlDataSourceDoc.class);

        assertThat(doc.getConnectionType()).isEqualTo(SqlConnectionType.ATHENA);
        assertThat(doc.getAthena().getRegion()).isEqualTo("eu-west-2");
        assertThat(doc.getAthena().getWorkGroup())
                .as("defaults are applied where the settings are used, not stored")
                .isNull();
    }

    @Test
    void copyKeepsEveryValue() {
        final SqlDataSourceDoc doc = fullDoc();

        assertThat(doc.copy().build()).isEqualTo(doc);
    }

    @Test
    void eachSettingTakesPartInEquality() {
        final SqlDataSourceDoc doc = fullDoc();

        assertThat(doc.copy().description("Other").build()).isNotEqualTo(doc);
        assertThat(doc.copy().connectionType(SqlConnectionType.JDBC).build()).isNotEqualTo(doc);
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
    void connectionTypesHaveDisplayValues() {
        assertThat(SqlConnectionType.JDBC.getDisplayValue()).isEqualTo("JDBC");
        assertThat(SqlConnectionType.ATHENA.getDisplayValue()).isEqualTo("Amazon Athena");
    }
}
