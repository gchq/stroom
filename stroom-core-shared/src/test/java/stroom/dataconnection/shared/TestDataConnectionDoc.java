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

package stroom.dataconnection.shared;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataConnectionDoc {

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

    private static LdapSettings ldap() {
        return LdapSettings.builder()
                .url("ldaps://directory.example.org:636")
                .startTls(false)
                .credentialName("directory-reader")
                .baseDn("ou=People,dc=example,dc=org")
                .scope(LdapSearchScope.ONE_LEVEL)
                .attributes(List.of(
                        new DataColumn("cn", DataColumnType.TEXT),
                        new DataColumn("whenCreated", DataColumnType.DATE)))
                .pageSize(200)
                .minRefreshMinutes(2)
                .build();
    }

    private static DataConnectionDoc fullDoc() {
        return DataConnectionDoc.builder()
                .uuid("uuid-1")
                .name("HR Database")
                .version("version-1")
                .description("Staff records")
                .sourceType(DataConnectionType.ATHENA)
                .jdbc(jdbc())
                .athena(athena())
                .ldap(ldap())
                .timeField("created_at")
                .queryTimeoutSeconds(30)
                .maxRows(5_000)
                .build();
    }

    @Test
    void typeIsDataConnection() {
        assertThat(fullDoc().getType()).isEqualTo(DataConnectionDoc.TYPE);
        assertThat(DataConnectionDoc.DOCUMENT_TYPE.getType()).isEqualTo(DataConnectionDoc.TYPE);
    }

    @Test
    void newDocumentsHaveDefaults() {
        final DataConnectionDoc doc = DataConnectionDoc.builder()
                .uuid("uuid-1")
                .name("New")
                .build();

        assertThat(doc.getSourceType()).isEqualTo(DataConnectionType.JDBC);
        assertThat(doc.getQueryTimeoutSeconds()).isEqualTo(DataConnectionDoc.DEFAULT_QUERY_TIMEOUT_SECONDS);
        assertThat(doc.getMaxRows()).isEqualTo(DataConnectionDoc.DEFAULT_MAX_ROWS);
        assertThat(doc.getJdbc()).isNull();
        assertThat(doc.getAthena()).isNull();
        assertThat(doc.getTimeField()).isNull();
    }

    @Test
    void survivesJson() {
        final DataConnectionDoc doc = fullDoc();

        final String json = JsonUtil.writeValueAsString(doc);
        final DataConnectionDoc fromJson = JsonUtil.readValue(json, DataConnectionDoc.class);

        assertThat(fromJson).isEqualTo(doc);
        assertThat(fromJson.getJdbc()).isEqualTo(jdbc());
        assertThat(fromJson.getAthena()).isEqualTo(athena());
    }

    @Test
    void jsonOmitsUnsetValues() {
        final DataConnectionDoc doc = new DataConnectionDoc(
                "uuid-1", "Bare", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);

        final String json = JsonUtil.writeValueAsString(doc);

        assertThat(json)
                .doesNotContain("sourceType")
                .doesNotContain("jdbc")
                .doesNotContain("athena")
                .doesNotContain("csv")
                .doesNotContain("ldap")
                .doesNotContain("maxRows");
    }

    @Test
    void jsonReadsSourceTypeByName() {
        final String json = """
                {"type":"DataConnection","uuid":"uuid-1","name":"Athena","sourceType":"ATHENA",
                "athena":{"region":"eu-west-2"}}""";

        final DataConnectionDoc doc = JsonUtil.readValue(json, DataConnectionDoc.class);

        assertThat(doc.getSourceType()).isEqualTo(DataConnectionType.ATHENA);
        assertThat(doc.getAthena().getRegion()).isEqualTo("eu-west-2");
        assertThat(doc.getAthena().getWorkGroup())
                .as("defaults are applied where the settings are used, not stored")
                .isNull();
    }

    @Test
    void copyKeepsEveryValue() {
        final DataConnectionDoc doc = fullDoc();

        assertThat(doc.copy().build()).isEqualTo(doc);
    }

    @Test
    void eachSettingTakesPartInEquality() {
        final DataConnectionDoc doc = fullDoc();

        assertThat(doc.copy().description("Other").build()).isNotEqualTo(doc);
        assertThat(doc.copy().sourceType(DataConnectionType.JDBC).build()).isNotEqualTo(doc);
        assertThat(doc.copy().jdbc(jdbc().copy().url("jdbc:postgresql://db/hr").build()).build())
                .isNotEqualTo(doc);
        assertThat(doc.copy().athena(athena().copy().database("other").build()).build()).isNotEqualTo(doc);
        assertThat(doc.copy().ldap(ldap().copy().baseDn("ou=Groups,dc=example,dc=org").build()).build())
                .isNotEqualTo(doc);
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
    void ldapSettingsTakePartInEquality() {
        final LdapSettings settings = ldap();

        assertThat(settings.copy().build()).isEqualTo(settings);
        assertThat(settings.copy().url("ldaps://other:636").build()).isNotEqualTo(settings);
        assertThat(settings.copy().startTls(true).build()).isNotEqualTo(settings);
        assertThat(settings.copy().credentialName("other").build()).isNotEqualTo(settings);
        assertThat(settings.copy().scope(LdapSearchScope.SUBTREE).build()).isNotEqualTo(settings);
        assertThat(settings.copy().attributes(List.of()).build()).isNotEqualTo(settings);
        assertThat(settings.copy().pageSize(201).build()).isNotEqualTo(settings);
        assertThat(settings.copy().minRefreshMinutes(3).build()).isNotEqualTo(settings);
    }

    @Test
    void jsonReadsLdapScopeByName() {
        final String json = """
                {"type":"DataConnection","uuid":"uuid-1","name":"Staff","sourceType":"LDAP",
                "ldap":{"url":"ldaps://dir:636","scope":"ONE_LEVEL"}}""";

        final DataConnectionDoc doc = JsonUtil.readValue(json, DataConnectionDoc.class);

        assertThat(doc.getSourceType()).isEqualTo(DataConnectionType.LDAP);
        assertThat(doc.getLdap().getScope()).isEqualTo(LdapSearchScope.ONE_LEVEL);
        assertThat(doc.getLdap().getPageSize()).as("defaults are applied where used").isNull();
    }

    @Test
    void sourceTypesHaveDisplayValues() {
        assertThat(DataConnectionType.JDBC.getDisplayValue()).isEqualTo("JDBC");
        assertThat(DataConnectionType.ATHENA.getDisplayValue()).isEqualTo("Amazon Athena");
        assertThat(DataConnectionType.LDAP.getDisplayValue()).isEqualTo("LDAP");
    }
}
