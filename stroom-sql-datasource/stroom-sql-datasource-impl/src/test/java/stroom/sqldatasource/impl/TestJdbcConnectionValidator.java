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

package stroom.sqldatasource.impl;

import stroom.sqldatasource.shared.JdbcConnectionSettings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestJdbcConnectionValidator {

    private static final String MYSQL_URL = "jdbc:mysql://reporting-db:3306/hr";

    private static SqlDataSourceConfig config(final String... allowedUrlPatterns) {
        return new SqlDataSourceConfig(List.of(allowedUrlPatterns), null, null, null, null, null, null, null,
                null, null, null);
    }

    private static JdbcConnectionSettings settings(final String url) {
        return JdbcConnectionSettings.builder()
                .url(url)
                .build();
    }

    @Test
    void urlMatchingAnAllowedPatternIsAccepted() {
        final List<String> problems = JdbcConnectionValidator.findProblems(
                settings(MYSQL_URL),
                config("^jdbc:postgresql://.*$", "^jdbc:mysql://reporting-db:3306/.*$"));

        assertThat(problems).isEmpty();
    }

    @Test
    void noUrlIsAllowedWithoutPatterns() {
        final List<String> problems = JdbcConnectionValidator.findProblems(settings(MYSQL_URL), config());

        assertThat(problems)
                .singleElement()
                .asString()
                .contains("does not match")
                .contains("sqlDataSource.allowedUrlPatterns");
    }

    @Test
    void patternMustMatchTheWholeUrl() {
        // A pattern written without anchors still cannot be satisfied by a URL that merely contains it.
        final List<String> problems = JdbcConnectionValidator.findProblems(
                settings("jdbc:mysql://evil-host:3306/reporting-db"),
                config("jdbc:mysql://reporting-db.*"));

        assertThat(problems).hasSize(1);
    }

    @Test
    void missingUrlIsReported() {
        assertThat(JdbcConnectionValidator.findProblems(settings("  "), config(".*")))
                .containsExactly("No JDBC URL has been set.");
        assertThat(JdbcConnectionValidator.findProblems(null, config(".*")))
                .containsExactly("No JDBC URL has been set.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:h2:mem:test",
            "JDBC:H2:file:/etc/passwd",
            "jdbc:hsqldb:mem:test",
            "jdbc:derby:memory:test",
            "jdbc:sqlite:/tmp/test.db"})
    void embeddedDatabasesAreRefusedEvenWhenAllowed(final String url) {
        final List<String> problems = JdbcConnectionValidator.findProblems(settings(url), config(".*"));

        assertThat(problems)
                .singleElement()
                .asString()
                .contains("Embedded databases");
    }

    @Test
    void deniedConnectionPropertyIsRefusedWhateverItsCase() {
        final JdbcConnectionSettings settings = JdbcConnectionSettings.builder()
                .url(MYSQL_URL)
                .connectionProperties(Map.of("ALLOWLOADLOCALINFILE", "true", "useCursorFetch", "true"))
                .build();

        final List<String> problems = JdbcConnectionValidator.findProblems(settings, config(".*"));

        assertThat(problems)
                .singleElement()
                .asString()
                .contains("ALLOWLOADLOCALINFILE")
                .doesNotContain("useCursorFetch");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://db:3306/hr?autoDeserialize=true",
            "jdbc:mysql://db:3306/hr?useSSL=true&allowLoadLocalInfile=true",
            "jdbc:postgresql://db:5432/hr?socketFactory=org.example.Evil",
            "jdbc:postgresql://db:5432/hr?ssl=true&loggerFile=/tmp/x",
            "jdbc:sqlserver://db:1433;databaseName=hr;sslfactory=org.example.Evil"})
    void deniedPropertyInTheUrlIsRefused(final String url) {
        final List<String> problems = JdbcConnectionValidator.findProblems(settings(url), config(".*"));

        assertThat(problems)
                .singleElement()
                .asString()
                .contains("driver properties are not allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Percent-encoded names, which MySQL's driver decodes.
            "jdbc:mysql://db:3306/hr?allowLoadLocal%49nfile=true",
            "jdbc:mysql://db:3306/hr?x=1&%61utoDeserialize=true",
            // Properties set in the host part.
            "jdbc:mysql://(host=db,port=3306,allowLoadLocalInfile=true)/hr",
            "jdbc:mysql://address=(host=db)(port=3306)(autoDeserialize=true)/hr",
            // Other MySQL URL schemes.
            "jdbc:mysql:loadbalance://db1:3306,db2:3306/hr?queryInterceptors=org.example.Evil",
            "jdbc:mysql:replication://db1:3306,db2:3306/hr?socketFactory=org.example.Evil"})
    void deniedMySqlPropertyIsFoundInEveryFormTheDriverReads(final String url) {
        final List<String> problems = JdbcConnectionValidator.findProblems(settings(url), config(".*"));

        assertThat(problems)
                .anySatisfy(problem -> assertThat(problem).contains("driver properties are not allowed"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://db:5432/hr?readOnlyMode=never",
            "jdbc:postgresql://db:5432/hr?sslkey=/etc/stroom/key.pem",
            "jdbc:postgresql://db:5432/hr?options=-c%20default_transaction_read_only%3Doff",
            "jdbc:mysql://db:3306/hr?readOnlyPropagatesToServer=false",
            "jdbc:mysql://db:3306/hr?logger=org.example.Evil"})
    void propertiesThatWeakenReadOnlyOrLoadFilesAreDenied(final String url) {
        final List<String> problems = JdbcConnectionValidator.findProblems(settings(url), config(".*"));

        assertThat(problems)
                .singleElement()
                .asString()
                .contains("driver properties are not allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://db:3306/hr?password=s3cret",
            "jdbc:mysql://db:3306/hr?user=reader",
            "jdbc:mysql://reader:s3cret@db:3306/hr",
            "jdbc:mysql://(host=db,port=3306,password=s3cret)/hr",
            "jdbc:postgresql://db:5432/hr?user=reader&password=s3cret",
            "jdbc:postgresql://db:5432/hr?sslpassword=s3cret",
            "jdbc:sqlserver://db:1433;databaseName=hr;password=s3cret",
            "jdbc:oracle:thin:scott/tiger@db:1521:orcl"})
    void userNameOrPasswordInTheUrlIsRefused(final String url) {
        final List<String> problems = JdbcConnectionValidator.findProblems(settings(url), config(".*"));

        assertThat(problems)
                .anySatisfy(problem -> assertThat(problem)
                        .contains("user name or password")
                        .contains("Credential"));
    }

    @Test
    void passwordConnectionPropertyIsRefusedWhateverItsCase() {
        final JdbcConnectionSettings settings = JdbcConnectionSettings.builder()
                .url(MYSQL_URL)
                .connectionProperties(Map.of("Password", "s3cret"))
                .build();

        final List<String> problems = JdbcConnectionValidator.findProblems(settings, config(".*"));

        assertThat(problems)
                .singleElement()
                .asString()
                .contains("connection properties (Password)")
                .doesNotContain("s3cret");
    }

    @Test
    void oracleUrlWithoutCredentialsIsAccepted() {
        assertThat(JdbcConnectionValidator.findProblems(
                settings("jdbc:oracle:thin:@//db:1521/hr"), config(".*")))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:sqlserver://db:1433;databaseName=hr;%73slfactory=x",
            "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(HOST=db)(PORT=1521)))"})
    void urlStroomCannotReadMustNotHidePropertiesInEncodingOrBrackets(final String url) {
        final List<String> problems = JdbcConnectionValidator.findProblems(settings(url), config(".*"));

        assertThat(problems)
                .singleElement()
                .asString()
                .contains("cannot contain '%' or '('");
    }

    @Test
    void mySqlUrlTheDriverCannotReadIsRefused() {
        final List<String> problems = JdbcConnectionValidator.findProblems(
                settings("jdbc:mysql://db:notAPort/hr"), config(".*"));

        assertThat(problems)
                .anySatisfy(problem -> assertThat(problem).contains("MySQL driver cannot read"));
    }

    @Test
    void allowedPropertiesInTheUrlAreAccepted() {
        final List<String> problems = JdbcConnectionValidator.findProblems(
                settings("jdbc:mysql://db:3306/hr?useCursorFetch=true&connectTimeout=5000"),
                config(".*"));

        assertThat(problems).isEmpty();
    }

    @Test
    void invalidPatternIsReportedAndDoesNotAllow() {
        final List<String> problems = JdbcConnectionValidator.findProblems(settings(MYSQL_URL), config("[unclosed"));

        assertThat(problems).hasSize(2);
        assertThat(problems.get(0)).contains("not a valid regular expression");
        assertThat(problems.get(1)).contains("does not match");
    }

    @Test
    void validateThrowsWithEveryProblem() {
        final JdbcConnectionSettings settings = JdbcConnectionSettings.builder()
                .url(MYSQL_URL + "?autoDeserialize=true")
                .build();

        assertThatThrownBy(() -> JdbcConnectionValidator.validate(settings, config()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match")
                .hasMessageContaining("autoDeserialize");
    }
}
