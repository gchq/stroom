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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.AthenaConnectionSettings;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionTestResponse;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.Query;
import stroom.query.api.datasource.QueryField;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.Database;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.WorkGroup;
import software.amazon.awssdk.services.athena.model.WorkGroupConfiguration;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// Queries Amazon Athena through the AWS SDK, with the SQL given in each query.
///
/// ## The time range
///
/// Athena bills by the data a query scans, so filtering in Stroom after Athena has read everything
/// would cost money for nothing. A query with a time range must therefore take it through `:from` or
/// `:to` in its SQL, and is refused otherwise.
///
/// ## Fields
///
/// Athena cannot describe a query without running it, and every run is billed. So the fields of a
/// query are only known once it has run; until then there are none.
@Singleton
class AthenaConnector implements Connector {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AthenaConnector.class);

    static final String TIME_RANGE_REFUSED = "The query has a time range, but its SQL has no :from or :to "
                                             + "placeholder. Amazon Athena bills by the data a query scans, "
                                             + "so the time range must go into the SQL, for example "
                                             + "'where event_time >= :from and event_time < :to'.";
    static final String ADVICE = "Use an AWS identity that can only read the tables Stroom should query, "
                                 + "and set a per-query data scan limit on the workgroup.";

    private final AthenaQueryRunner queryRunner;
    private final AthenaConnectionResolver connectionResolver;
    private final AthenaClientFactory clientFactory;
    private final SqlFieldCache fieldCache;

    /// Creates the connector. Guice supplies every argument.
    @Inject
    AthenaConnector(final AthenaQueryRunner queryRunner,
                    final AthenaConnectionResolver connectionResolver,
                    final AthenaClientFactory clientFactory,
                    final SqlFieldCache fieldCache) {
        this.queryRunner = queryRunner;
        this.connectionResolver = connectionResolver;
        this.clientFactory = clientFactory;
        this.fieldCache = fieldCache;
    }

    @Override
    public DataConnectionType getType() {
        return DataConnectionType.ATHENA;
    }

    /// The SQL of an Athena Data Connection is given in each query.
    @Override
    public boolean takesNativeQuery() {
        return true;
    }

    /// The fields are the columns of the SQL's last run, if it has run; otherwise none.
    @Override
    public List<QueryField> getFields(final DataConnectionDoc doc, final String sql) {
        requireSettings(doc);
        SqlStatementParser.parse(sql);
        // The cache answers without connecting, so check here that the user may use the credential,
        // as running the query would.
        connectionResolver.resolve(doc.getAthena());
        return fieldCache.getIfPresent(doc, sql)
                .map(SqlColumn::toQueryFields)
                .orElse(List.of());
    }

    /// Athena sources default to [AthenaConnectionSettings#DEFAULT_MIN_REFRESH_MINUTES], as every run
    /// is billed.
    @Override
    public int getMinRefreshMinutes(final DataConnectionDoc doc) {
        final Integer minutes = Objects.requireNonNullElse(
                NullSafe.get(doc.getAthena(), AthenaConnectionSettings::getMinRefreshMinutes),
                AthenaConnectionSettings.DEFAULT_MIN_REFRESH_MINUTES);
        return Math.max(0, minutes);
    }

    @Override
    public PreparedQuery prepare(final DataConnectionDoc doc,
                                 final Query query,
                                 final DateTimeSettings dateTimeSettings) {
        requireSettings(doc);
        final String sql = query.getNativeQuery();
        final ParsedSql parsedSql = SqlStatementParser.parse(sql);
        final List<Object> values = SqlParameterBinder.resolveValues(
                parsedSql,
                query.getTimeRange(),
                query.getParams(),
                dateTimeSettings);
        AthenaQueryRunner.requireReadOnly(parsedSql);
        if (TimeRanges.isSet(query.getTimeRange()) && !parsedSql.usesTimeRange()) {
            throw new IllegalArgumentException(TIME_RANGE_REFUSED);
        }

        return new PreparedQuery(query.getExpression(), List.of(), (rowConsumerFactory, cancellation, progress) -> {
            final Function<List<SqlColumn>, SqlRowConsumer> cachingFactory = columns -> {
                fieldCache.put(doc, sql, columns);
                return rowConsumerFactory.apply(columns);
            };
            return queryRunner.run(doc, parsedSql, values, cachingFactory, cancellation, progress);
        });
    }

    /// Runs no query, as every query is billed. Instead the workgroup and database are looked up,
    /// which checks the credentials, region, workgroup and database.
    @Override
    public DataConnectionTestResponse test(final DataConnectionDoc doc) {
        try {
            final ResolvedAthenaConnection connection = connectionResolver.resolve(doc.getAthena());
            try (final AthenaClient client = clientFactory.create(connection)) {
                final WorkGroup workGroup = client.getWorkGroup(builder -> builder
                        .workGroup(connection.workGroup())).workGroup();
                final Database database = client.getDatabase(builder -> builder
                        .catalogName(connection.catalog())
                        .databaseName(connection.database())).database();
                return new DataConnectionTestResponse(true, describe(connection, workGroup, database,
                        AthenaQueryRunner.maxDataScannedMb(doc.getAthena())));
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Connection test failed for {}", doc.getUuid()), e);
            return new DataConnectionTestResponse(false, e.getMessage());
        }
    }

    private static String describe(final ResolvedAthenaConnection connection,
                                   final WorkGroup workGroup,
                                   final Database database,
                                   final int maxDataScannedMb) {
        final WorkGroupConfiguration configuration = workGroup.configuration();
        final Long scanLimit = NullSafe.get(configuration, WorkGroupConfiguration::bytesScannedCutoffPerQuery);
        final String outputLocation = NullSafe.get(
                configuration,
                WorkGroupConfiguration::resultConfiguration,
                ResultConfiguration::outputLocation);
        return "Connected to Amazon Athena in " + connection.region()
               + "\nWorkgroup: " + workGroup.name() + " (" + workGroup.stateAsString() + ")"
               + "\nWorkgroup data scan limit per query: " + (scanLimit == null
                       ? "none set"
                       : ModelStringUtil.formatMetricByteSizeString(scanLimit))
               + "\nDocument data scan limit: " + (maxDataScannedMb == 0
                       ? "none set"
                       : maxDataScannedMb + " MB, checked while a query runs")
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
               + "\n\n" + ADVICE;
    }

    private static void requireSettings(final DataConnectionDoc doc) {
        if (doc.getAthena() == null) {
            throw new IllegalArgumentException("Data Connection '" + doc.getName()
                                               + "' has no Amazon Athena settings.");
        }
    }
}
