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

import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.sqldatasource.shared.SqlConnectionType;
import stroom.sqldatasource.shared.SqlDataSourceDoc;
import stroom.sqldatasource.shared.SqlDataSourceTestResponse;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.Database;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.WorkGroup;
import software.amazon.awssdk.services.athena.model.WorkGroupConfiguration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Objects;

/// Tests the connection described by a [SqlDataSourceDoc], which need not have been saved.
@Singleton
public class SqlDataSourceConnectionTester {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqlDataSourceConnectionTester.class);

    static final String READ_ONLY_ADVICE = "Use a database account that can only read. Stroom asks for "
                                           + "read-only connections, but only the database can enforce it.";
    static final String ATHENA_ADVICE = "Use an AWS identity that can only read the tables Stroom should query, "
                                        + "and set a per-query data scan limit on the workgroup.";

    private final Provider<SecurityContext> securityContextProvider;
    private final JdbcConnectionResolver connectionResolver;
    private final JdbcDriverLoader driverLoader;
    private final AthenaConnectionResolver athenaConnectionResolver;
    private final AthenaClientFactory athenaClientFactory;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    SqlDataSourceConnectionTester(final Provider<SecurityContext> securityContextProvider,
                                  final JdbcConnectionResolver connectionResolver,
                                  final JdbcDriverLoader driverLoader,
                                  final AthenaConnectionResolver athenaConnectionResolver,
                                  final AthenaClientFactory athenaClientFactory) {
        this.securityContextProvider = securityContextProvider;
        this.connectionResolver = connectionResolver;
        this.driverLoader = driverLoader;
        this.athenaConnectionResolver = athenaConnectionResolver;
        this.athenaClientFactory = athenaClientFactory;
    }

    /// Opens a connection with the document's settings and reports what it connected to.
    ///
    /// For Amazon Athena no query is run, as every query is billed. Instead the workgroup and
    /// database are looked up, which checks the credentials, region, workgroup and database.
    ///
    /// The test uses the settings in the request, which may not be saved, with the credential they
    /// name. So only a user who could save those settings may run it; otherwise a user could send a
    /// credential they can use to a server of their choosing.
    ///
    /// @param doc The document whose connection is tested.
    /// @return Whether the connection worked, with the database and driver versions or the error.
    /// @throws EntityServiceException If no document is supplied.
    /// @throws PermissionException    If the current user may not edit the document.
    public SqlDataSourceTestResponse test(final SqlDataSourceDoc doc) {
        checkEditPermission(doc);

        final SqlConnectionType connectionType = Objects.requireNonNullElse(
                doc.getConnectionType(),
                SqlConnectionType.JDBC);
        return switch (connectionType) {
            case JDBC -> testJdbc(doc);
            case ATHENA -> testAthena(doc);
        };
    }

    private SqlDataSourceTestResponse testJdbc(final SqlDataSourceDoc doc) {
        try {
            final ResolvedJdbcConnection resolvedConnection = connectionResolver.resolve(doc.getJdbc());
            driverLoader.ensureLoaded();
            // A pool of one, closed straight after, so a test never leaves a pool behind.
            try (final HikariDataSource dataSource = JdbcDataSourceFactory.create(resolvedConnection, 1);
                    final Connection connection = dataSource.getConnection()) {
                return new SqlDataSourceTestResponse(true, describe(connection.getMetaData()));
            }
        } catch (final SQLException | RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Connection test failed for {}", doc.getUuid()), e);
            return new SqlDataSourceTestResponse(false, e.getMessage());
        }
    }

    private SqlDataSourceTestResponse testAthena(final SqlDataSourceDoc doc) {
        try {
            final ResolvedAthenaConnection connection = athenaConnectionResolver.resolve(doc.getAthena());
            try (final AthenaClient client = athenaClientFactory.create(connection)) {
                final WorkGroup workGroup = client.getWorkGroup(builder -> builder
                        .workGroup(connection.workGroup())).workGroup();
                final Database database = client.getDatabase(builder -> builder
                        .catalogName(connection.catalog())
                        .databaseName(connection.database())).database();
                return new SqlDataSourceTestResponse(true, describe(connection, workGroup, database));
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Connection test failed for {}", doc.getUuid()), e);
            return new SqlDataSourceTestResponse(false, e.getMessage());
        }
    }

    private static String describe(final ResolvedAthenaConnection connection,
                                   final WorkGroup workGroup,
                                   final Database database) {
        final WorkGroupConfiguration configuration = workGroup.configuration();
        final Long scanLimit = NullSafe.get(configuration, WorkGroupConfiguration::bytesScannedCutoffPerQuery);
        final String outputLocation = NullSafe.get(
                configuration,
                WorkGroupConfiguration::resultConfiguration,
                ResultConfiguration::outputLocation);
        return "Connected to Amazon Athena in " + connection.region()
               + "\nWorkgroup: " + workGroup.name() + " (" + workGroup.stateAsString() + ")"
               + "\nData scan limit per query: " + (scanLimit == null
                       ? "none set"
                       : ModelStringUtil.formatMetricByteSizeString(scanLimit))
               + "\nWorkgroup result location: " + Objects.requireNonNullElse(outputLocation, "none set")
               + "\nWorkgroup enforces its settings: " + (NullSafe.isTrue(NullSafe.get(
                       configuration,
                       WorkGroupConfiguration::enforceWorkGroupConfiguration))
                       ? "yes"
                       : "no, so the document's output location is used if it sets one")
               + "\nDatabase: " + connection.catalog() + "." + database.name()
               + "\nCredentials: " + (connection.awsKey() == null
                       ? "Stroom's own AWS identity"
                       : "access key " + connection.awsKey().getAccessKeyId())
               + "\n\n" + ATHENA_ADVICE;
    }

    private static String describe(final DatabaseMetaData metaData) throws SQLException {
        return "Connected to " + metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion()
               + "\nDriver: " + metaData.getDriverName() + " " + metaData.getDriverVersion()
               + "\nUser: " + metaData.getUserName()
               + "\n\n" + READ_ONLY_ADVICE;
    }

    private void checkEditPermission(final SqlDataSourceDoc doc) {
        if (doc == null || doc.getUuid() == null) {
            throw new EntityServiceException("No SQL data source supplied");
        }
        final SecurityContext securityContext = securityContextProvider.get();
        final DocRef docRef = SqlDataSourceDoc.buildDocRef()
                .uuid(doc.getUuid())
                .build();
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to test this SQL data source");
        }
    }
}
