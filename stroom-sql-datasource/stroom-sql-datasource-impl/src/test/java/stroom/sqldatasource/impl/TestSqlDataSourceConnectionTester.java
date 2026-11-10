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

import stroom.credentials.api.StoredSecrets;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.sqldatasource.shared.JdbcConnectionSettings;
import stroom.sqldatasource.shared.SqlConnectionType;
import stroom.sqldatasource.shared.SqlDataSourceDoc;
import stroom.sqldatasource.shared.SqlDataSourceTestResponse;
import stroom.util.io.PathCreator;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSqlDataSourceConnectionTester {

    private FakeJdbcDriver driver;

    @BeforeEach
    void setUp() throws SQLException {
        driver = FakeJdbcDriver.register();
    }

    @AfterEach
    void tearDown() throws SQLException {
        driver.deregister();
    }

    private static SqlDataSourceConnectionTester tester(final SecurityContext securityContext) {
        final SqlDataSourceConfig config = new SqlDataSourceConfig(
                List.of(FakeJdbcDriver.ALLOWED_URL_PATTERN), null, null, null, null, null, null, null);
        final JdbcConnectionResolver resolver = new JdbcConnectionResolver(
                () -> config,
                () -> Mockito.mock(StoredSecrets.class));
        final JdbcDriverLoader driverLoader = new JdbcDriverLoader(() -> config, Mockito.mock(PathCreator.class));
        return new SqlDataSourceConnectionTester(() -> securityContext, resolver, driverLoader);
    }

    private static SqlDataSourceDoc doc(final String url) {
        return SqlDataSourceDoc.builder()
                .uuid("uuid-1")
                .name("HR")
                .jdbc(JdbcConnectionSettings.builder()
                        .url(url)
                        .build())
                .build();
    }

    @Test
    void successfulTestReportsTheDatabaseAndAdvisesReadOnly() {
        final SqlDataSourceTestResponse response = tester(new MockSecurityContext()).test(doc(FakeJdbcDriver.URL));

        assertThat(response.isOk()).isTrue();
        assertThat(response.getMessage())
                .contains("FakeDB 1.2.3")
                .contains("Fake Driver 4.5")
                .contains(SqlDataSourceConnectionTester.READ_ONLY_ADVICE);
    }

    @Test
    void testConnectionsAreReadOnlyAndClosed() throws SQLException {
        tester(new MockSecurityContext()).test(doc(FakeJdbcDriver.URL));

        assertThat(driver.getConnections()).isNotEmpty();
        for (final Connection connection : driver.getConnections()) {
            Mockito.verify(connection).setReadOnly(true);
            Mockito.verify(connection).close();
        }
    }

    @Test
    void urlNotAllowedFailsWithoutConnecting() {
        final SqlDataSourceTestResponse response = tester(new MockSecurityContext())
                .test(doc("jdbc:mysql://elsewhere:3306/hr"));

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("allowedUrlPatterns");
        assertThat(driver.getConnections()).isEmpty();
    }

    @Test
    void missingUrlFails() {
        final SqlDataSourceTestResponse response = tester(new MockSecurityContext())
                .test(SqlDataSourceDoc.builder()
                        .uuid("uuid-1")
                        .build());

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("No JDBC URL");
    }

    @Test
    void athenaIsNotAvailableYet() {
        final SqlDataSourceTestResponse response = tester(new MockSecurityContext())
                .test(doc(FakeJdbcDriver.URL).copy()
                        .connectionType(SqlConnectionType.ATHENA)
                        .build());

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).isEqualTo(SqlDataSourceConnectionTester.ATHENA_NOT_AVAILABLE);
        assertThat(driver.getConnections()).isEmpty();
    }

    @Test
    void userWhoCannotEditIsRefused() {
        // Otherwise a user could send a credential they can use to a server of their choosing.
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.any())).thenReturn(false);

        assertThatThrownBy(() -> tester(securityContext).test(doc(FakeJdbcDriver.URL)))
                .isInstanceOf(PermissionException.class);
        assertThat(driver.getConnections()).isEmpty();
    }

    @Test
    void noDocumentIsRefused() {
        assertThatThrownBy(() -> tester(new MockSecurityContext()).test(null))
                .isInstanceOf(EntityServiceException.class);
    }
}
