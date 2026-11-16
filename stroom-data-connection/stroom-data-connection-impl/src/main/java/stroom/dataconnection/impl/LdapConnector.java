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

import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionTestResponse;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.LdapSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Query;
import stroom.query.api.datasource.QueryField;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.RootDSE;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// Searches an LDAP directory, with the search filter given in each query.
///
/// ## Fields
///
/// Entries in a directory need not share attributes, so the fields are not taken from what a search
/// returns but from the document's attributes table: `dn`, then each attribute in the table. An
/// attribute an entry does not have is empty. Detect Attributes fills the table from some entries.
///
/// ## The time range
///
/// A filter can take the time range with `:from` and `:to`, as in `(modifyTimestamp>=:from)`, which
/// the directory then applies. Otherwise, if the document names a time column, which must be a Date
/// attribute, entries are filtered on it in Stroom; failing both, the time range is ignored with a
/// warning. This is how JDBC sources work too.
@Singleton
class LdapConnector implements Connector {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LdapConnector.class);

    static final String TIME_RANGE_IGNORED = "The time range was ignored, as the LDAP filter has no :from or "
                                             + ":to placeholder and the Data Connection has no time "
                                             + "column.";
    static final String ADVICE = "Use a bind account that can only read the entries and attributes Stroom "
                                 + "needs, as directories hold personal data.";
    /// The most entries Detect Attributes reads.
    static final int DETECT_SAMPLE_ENTRIES = 100;
    // Every user attribute, and the operational timestamps most directories keep, which are only
    // returned when asked for by name and suit the time column.
    private static final String[] DETECT_ATTRIBUTES = {
            SearchRequest.ALL_USER_ATTRIBUTES,
            "createTimestamp",
            "modifyTimestamp"};

    private final LdapConnectionResolver connectionResolver;
    private final LdapConnectionFactory connectionFactory;
    private final LdapQueryRunner queryRunner;
    private final Provider<DataConnectionConfig> configProvider;

    /// Creates the connector. Guice supplies every argument.
    @Inject
    LdapConnector(final LdapConnectionResolver connectionResolver,
                  final LdapConnectionFactory connectionFactory,
                  final LdapQueryRunner queryRunner,
                  final Provider<DataConnectionConfig> configProvider) {
        this.connectionResolver = connectionResolver;
        this.connectionFactory = connectionFactory;
        this.queryRunner = queryRunner;
        this.configProvider = configProvider;
    }

    @Override
    public DataConnectionType getType() {
        return DataConnectionType.LDAP;
    }

    /// The LDAP search filter of each query is given in its `sql` clause.
    @Override
    public boolean takesNativeQuery() {
        return true;
    }

    /// The fields are `dn` and the document's attributes, so the directory is not searched. The filter
    /// is checked, and so is the credential, as running the query would.
    @Override
    public List<QueryField> getFields(final DataConnectionDoc doc, final String filter) {
        final LdapSettings settings = requireSettings(doc);
        LdapFilterTemplate.parse(filter).check();
        connectionResolver.resolve(settings);
        return SqlColumn.toQueryFields(LdapQueryRunner.columns(attributes(settings)));
    }

    /// LDAP sources have no minimum unless the document sets one.
    @Override
    public int getMinRefreshMinutes(final DataConnectionDoc doc) {
        final Integer minutes = NullSafe.get(doc.getLdap(), LdapSettings::getMinRefreshMinutes);
        return minutes == null
                ? 0
                : Math.max(0, minutes);
    }

    @Override
    public PreparedQuery prepare(final DataConnectionDoc doc,
                                 final Query query,
                                 final DateTimeSettings dateTimeSettings) {
        final LdapSettings settings = requireSettings(doc);
        final List<DataColumn> attributes = attributes(settings);
        requireNamedAttributes(doc, attributes);
        final LdapFilterTemplate template = LdapFilterTemplate.parse(query.getNativeQuery());
        final Filter filter = template.resolve(query.getTimeRange(), query.getParams(), dateTimeSettings);
        // Resolved now, in the user's security context, so a credential they may not use is reported
        // before the search starts.
        final ResolvedLdapConnection connection = connectionResolver.resolve(settings);

        final boolean timeRangeSet = TimeRanges.isSet(query.getTimeRange());
        final String timeField = doc.getTimeField();
        final ExpressionOperator expression;
        if (template.usesTimeRange()) {
            expression = query.getExpression();
        } else {
            if (timeRangeSet && !NullSafe.isBlankString(timeField)) {
                requireDateAttribute(doc, attributes, timeField);
            }
            expression = TimeRanges.addTimeRange(query.getExpression(), query.getTimeRange(), timeField);
        }
        final List<String> warnings = timeRangeSet && !template.usesTimeRange() && NullSafe.isBlankString(timeField)
                ? List.of(TIME_RANGE_IGNORED)
                : List.of();
        final SqlQueryLimits limits = SqlQueryLimits.of(doc, configProvider.get());
        final List<SqlColumn> columns = LdapQueryRunner.columns(attributes);
        return new PreparedQuery(expression, warnings, (rowConsumerFactory, cancellation, progress) ->
                queryRunner.run(connection, filter, attributes, columns, limits, rowConsumerFactory, cancellation,
                        progress));
    }

    /// Connects and binds, then reads the base DN's entry and the directory's root DSE, reporting the
    /// directory's vendor and whether it supports paged results.
    @Override
    public DataConnectionTestResponse test(final DataConnectionDoc doc) {
        try {
            final ResolvedLdapConnection connection = connectionResolver.resolve(requireSettings(doc));
            try (final LDAPConnection ldapConnection = connectionFactory.connect(connection, timeoutSeconds(doc))) {
                final RootDSE rootDse = ldapConnection.getRootDSE();
                final SearchResultEntry baseEntry = ldapConnection.getEntry(connection.baseDn());
                if (baseEntry == null) {
                    return new DataConnectionTestResponse(false, "Connected to " + connection.url()
                                                                     + " as " + connection.bindDn()
                                                                     + ", but the base DN '" + connection.baseDn()
                                                                     + "' was not found, or the bind account "
                                                                     + "may not read it.");
                }
                return new DataConnectionTestResponse(true, describe(connection, rootDse));
            }
        } catch (final LDAPException | GeneralSecurityException | RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Connection test failed for {}", doc.getUuid()), e);
            return new DataConnectionTestResponse(false, message(e));
        }
    }

    /// Adds the attributes of the first [#DETECT_SAMPLE_ENTRIES] entries below the base DN to those
    /// already in the table: their user attributes, and `createTimestamp` and `modifyTimestamp` where
    /// the directory keeps them.
    ///
    /// Every attribute already in the table stays, in its place and with its type, even if none of the
    /// sample entries has it, as the user may have added it by name or it may only be on later entries.
    /// New attributes follow, in the order they are first seen, with the type their values suggest.
    /// Binary attributes are not added. Names are matched without regard to case, as LDAP's are.
    ///
    /// @param doc The document, with its LDAP settings, which need not have been saved.
    /// @return The attributes.
    /// @throws IllegalArgumentException If the settings are incomplete or the search fails.
    List<DataColumn> detectAttributes(final DataConnectionDoc doc) {
        final LdapSettings settings = requireSettings(doc);
        final ResolvedLdapConnection connection = connectionResolver.resolve(settings);
        final Map<String, List<Attribute>> found = new LinkedHashMap<>();
        try (final LDAPConnection ldapConnection = connectionFactory.connect(connection, timeoutSeconds(doc))) {
            final SearchRequest request = new SearchRequest(
                    connection.baseDn(),
                    LdapQueryRunner.toSearchScope(connection.scope()),
                    Filter.createPresenceFilter("objectClass"),
                    DETECT_ATTRIBUTES);
            request.setSizeLimit(DETECT_SAMPLE_ENTRIES);
            request.setTimeLimitSeconds(timeoutSeconds(doc));
            request.setControls(new SimplePagedResultsControl(DETECT_SAMPLE_ENTRIES, false));
            final SearchResult result = search(ldapConnection, request);
            for (final SearchResultEntry entry : result.getSearchEntries()) {
                for (final Attribute attribute : entry.getAttributes()) {
                    // Keyed without regard to case, with the name as first seen kept by the first value.
                    found.computeIfAbsent(attribute.getName().toLowerCase(Locale.ROOT), key -> new ArrayList<>())
                            .add(attribute);
                }
            }
        } catch (final LDAPException | GeneralSecurityException e) {
            throw new IllegalArgumentException(message(e), e);
        }
        return detected(found, attributes(settings));
    }

    // The existing attributes, then those found that are not among them. Found is keyed by lower case.
    private static List<DataColumn> detected(final Map<String, List<Attribute>> found,
                                             final List<DataColumn> existing) {
        final List<DataColumn> columns = new ArrayList<>(existing);
        final Set<String> listed = new HashSet<>();
        for (final DataColumn column : existing) {
            listed.add(column.getName().trim().toLowerCase(Locale.ROOT));
        }
        found.forEach((key, attributes) -> {
            if (!listed.contains(key) && attributes.stream().noneMatch(LdapValues::isBinary)) {
                columns.add(new DataColumn(attributes.getFirst().getName(), LdapValues.guessType(attributes)));
            }
        });
        return columns;
    }

    // A directory's own size limit, or the sample size, ends the search early; the entries read are kept.
    private static SearchResult search(final LDAPConnection connection,
                                       final SearchRequest request) throws LDAPException {
        try {
            return connection.search(request);
        } catch (final LDAPSearchException e) {
            if (ResultCode.SIZE_LIMIT_EXCEEDED.equals(e.getResultCode())
                || ResultCode.TIME_LIMIT_EXCEEDED.equals(e.getResultCode())) {
                return e.getSearchResult();
            }
            throw e;
        }
    }

    private int timeoutSeconds(final DataConnectionDoc doc) {
        return SqlQueryLimits.of(doc, configProvider.get()).timeoutSeconds();
    }

    private static String describe(final ResolvedLdapConnection connection, final RootDSE rootDse) {
        String vendor = "not reported";
        if (rootDse != null && !NullSafe.isBlankString(rootDse.getVendorName())) {
            vendor = rootDse.getVendorName();
            if (!NullSafe.isBlankString(rootDse.getVendorVersion())) {
                vendor = vendor + " " + rootDse.getVendorVersion();
            }
        }
        final boolean pages = rootDse != null && rootDse.supportsControl(SimplePagedResultsControl.PAGED_RESULTS_OID);
        return "Connected to " + connection.url() + (connection.startTls()
                ? " with StartTLS"
                : "")
               + "\nBound as: " + connection.bindDn()
               + "\nDirectory: " + vendor
               + "\nBase DN: " + connection.baseDn() + ", found"
               + "\nScope: " + connection.scope().getDisplayValue()
               + "\nPaged results: " + (pages
                       ? "supported, " + connection.pageSize() + " entries a page"
                       : "not supported, so searches are limited by the directory's own size limit")
               + "\n\n" + ADVICE;
    }

    // LDAP result codes are clearer to users with their name, e.g. 'invalid credentials'.
    private static String message(final Exception e) {
        if (e instanceof final LDAPException ldapException) {
            final String diagnostic = ldapException.getDiagnosticMessage();
            return "LDAP " + ldapException.getResultCode().getName()
                   + (NullSafe.isBlankString(diagnostic)
                           ? ": " + ldapException.getMessage()
                           : ": " + diagnostic);
        }
        return e.getMessage();
    }

    private static LdapSettings requireSettings(final DataConnectionDoc doc) {
        if (doc.getLdap() == null) {
            throw new IllegalArgumentException("Data Connection '" + doc.getName() + "' has no LDAP settings.");
        }
        return doc.getLdap();
    }

    // The attributes with a name, trimmed, as an imported document may not be; the DN is always the first
    // field, so an attribute named dn is left out.
    private static List<DataColumn> attributes(final LdapSettings settings) {
        return NullSafe.list(settings.getAttributes())
                .stream()
                .filter(column -> !NullSafe.isBlankString(column.getName()))
                .map(column -> new DataColumn(column.getName().trim(), column.getType()))
                .filter(column -> !LdapQueryRunner.DN_FIELD.equalsIgnoreCase(column.getName()))
                .toList();
    }

    // Each attribute becomes a field, so each needs a name of its own. LDAP names ignore case.
    private static void requireNamedAttributes(final DataConnectionDoc doc, final List<DataColumn> attributes) {
        final Set<String> names = new HashSet<>();
        for (final DataColumn attribute : attributes) {
            if (!names.add(attribute.getName().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Data Connection '" + doc.getName() + "' lists the "
                                                   + "attribute '" + attribute.getName() + "' more than once.");
            }
        }
    }

    private static void requireDateAttribute(final DataConnectionDoc doc,
                                             final List<DataColumn> attributes,
                                             final String timeField) {
        final DataColumn attribute = attributes.stream()
                .filter(column -> timeField.equalsIgnoreCase(column.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The time range cannot be applied, as the time "
                                                                + "column '" + timeField + "' is not an attribute "
                                                                + "of Data Connection '" + doc.getName()
                                                                + "'. Add it to the attributes, or use :from "
                                                                + "and :to in the filter."));
        if (attribute.getType() != DataColumnType.DATE) {
            throw new IllegalArgumentException("The time range cannot be applied, as the time column '"
                                               + timeField + "' of Data Connection '" + doc.getName()
                                               + "' is not a Date attribute.");
        }
    }
}
