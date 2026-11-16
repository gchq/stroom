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
import stroom.dataconnection.shared.LdapSearchScope;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.EntrySourceException;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPEntrySource;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultReferenceEntrySourceException;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/// Runs an LDAP search and feeds each entry to a row consumer as a row: its DN first, then the
/// attributes of the document's attributes table, in order.
///
/// The search asks for pages of entries with the simple paged results control (RFC 2696), not marked
/// critical, so a directory without it still answers, within its own size limit. Stroom stops reading
/// at the row limit. The directory's own size or time limit, and any referrals, which are not followed,
/// end up as warnings rather than errors, keeping the entries read.
@Singleton
class LdapQueryRunner {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LdapQueryRunner.class);

    /// The field holding each entry's DN, always the first.
    static final String DN_FIELD = "dn";
    // The longest value shown in the warning about values not of their column's type.
    private static final int MAX_EXAMPLE_LENGTH = 60;

    private final LdapConnectionFactory connectionFactory;

    /// Creates the runner. Guice supplies every argument.
    @Inject
    LdapQueryRunner(final LdapConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /// Searches the directory.
    ///
    /// @param connection         The resolved connection.
    /// @param filter             The search filter, with its placeholders' values in place.
    /// @param attributes         The document's attributes, in field order after the DN.
    /// @param columns            The columns of each row: the DN, then the attributes.
    /// @param limits             The timeout and the row limit.
    /// @param rowConsumerFactory Makes the row consumer from the columns.
    /// @param cancellation       Stops the search when the query is cancelled.
    /// @param progress           Takes messages about the search's progress.
    /// @return What the search read.
    /// @throws Exception If the directory cannot be reached, refuses the bind or fails the search.
    QueryRunSummary run(final ResolvedLdapConnection connection,
                        final Filter filter,
                        final List<DataColumn> attributes,
                        final List<SqlColumn> columns,
                        final SqlQueryLimits limits,
                        final Function<List<SqlColumn>, SqlRowConsumer> rowConsumerFactory,
                        final QueryCancellation cancellation,
                        final Consumer<String> progress) throws Exception {
        final SqlRowConsumer rowConsumer = rowConsumerFactory.apply(columns);
        final Search search = new Search(attributes, limits, rowConsumer, cancellation);
        try (final LDAPConnection ldapConnection = connectionFactory.connect(connection, limits.timeoutSeconds())) {
            // Closing the connection ends the search in the directory too. It is closed in the background,
            // as closing waits for the connection's reader, which may be waiting for this thread to read.
            cancellation.register(() -> closeInBackground(ldapConnection));
            try {
                ASN1OctetString cookie = null;
                do {
                    final SearchRequest request = searchRequest(connection, filter, attributes, limits, cookie);
                    final SearchResult result = search.readPage(ldapConnection, request);
                    progress.accept(LogUtil.message("LDAP search of {} has read {} entries",
                            connection.url(), search.rowsRead));
                    cookie = nextCookie(result);
                } while (cookie != null && !search.stopped());
            } catch (final LDAPException | EntrySourceException e) {
                if (!cancellation.isCancelled()) {
                    throw e;
                }
                LOGGER.debug(() -> LogUtil.message("LDAP search of {} cancelled: {}", connection.url(),
                        e.getMessage()));
            } finally {
                cancellation.unregister();
            }
        }
        return new QueryRunSummary(
                columns,
                rowConsumer.getUnmatchedFields(),
                search.rowsRead,
                search.rowsPassed,
                search.limitReached,
                limits.maxRows(),
                null,
                search.warnings());
    }

    // Closing a connection waits for its reader, which waits while the entry source's queue is full.
    private static void closeInBackground(final LDAPConnection connection) {
        CompletableFuture.runAsync(connection::close);
    }

    /// @param attributes The document's attributes, in field order.
    /// @return The columns of each row: the DN, then the attributes.
    static List<SqlColumn> columns(final List<DataColumn> attributes) {
        final List<SqlColumn> columns = new ArrayList<>(attributes.size() + 1);
        columns.add(new SqlColumn(DN_FIELD, DataValues.toFieldType(null), Types.OTHER));
        for (final DataColumn attribute : attributes) {
            columns.add(new SqlColumn(attribute.getName(), DataValues.toFieldType(attribute.getType()),
                    Types.OTHER));
        }
        return columns;
    }

    /// @param scope A search scope.
    /// @return The SDK's scope.
    static SearchScope toSearchScope(final LdapSearchScope scope) {
        return switch (scope) {
            case BASE -> SearchScope.BASE;
            case ONE_LEVEL -> SearchScope.ONE;
            case SUBTREE -> SearchScope.SUB;
        };
    }

    private static SearchRequest searchRequest(final ResolvedLdapConnection connection,
                                               final Filter filter,
                                               final List<DataColumn> attributes,
                                               final SqlQueryLimits limits,
                                               final ASN1OctetString cookie) {
        // With no attributes, ask for none rather than all of them: the DN comes with every entry.
        final String[] attributeNames = attributes.isEmpty()
                ? new String[]{SearchRequest.NO_ATTRIBUTES}
                : attributes.stream().map(DataColumn::getName).toArray(String[]::new);
        final SearchRequest request = new SearchRequest(
                connection.baseDn(),
                toSearchScope(connection.scope()),
                filter,
                attributeNames);
        request.setTimeLimitSeconds(limits.timeoutSeconds());
        request.setControls(new SimplePagedResultsControl(connection.pageSize(), cookie, false));
        return request;
    }

    // The cookie for the next page, or null if this was the last page or the directory does not page.
    private static ASN1OctetString nextCookie(final SearchResult result) throws LDAPException {
        if (result == null) {
            return null;
        }
        final SimplePagedResultsControl control = SimplePagedResultsControl.get(result);
        return control != null && control.moreResultsToReturn()
                ? control.getCookie()
                : null;
    }

    // --------------------------------------------------------------------------------


    // The state of one search across its pages.
    private static final class Search {

        private final List<DataColumn> attributes;
        private final SqlQueryLimits limits;
        private final SqlRowConsumer rowConsumer;
        private final QueryCancellation cancellation;
        private final BadValues badValues = new BadValues();
        private long rowsRead;
        private long rowsPassed;
        private boolean limitReached;
        private boolean endedByDirectory;
        private int referrals;
        private String directoryLimitWarning;

        private Search(final List<DataColumn> attributes,
                       final SqlQueryLimits limits,
                       final SqlRowConsumer rowConsumer,
                       final QueryCancellation cancellation) {
            this.attributes = attributes;
            this.limits = limits;
            this.rowConsumer = rowConsumer;
            this.cancellation = cancellation;
        }

        // Reads one page, returning its result, or null if reading stopped before the page ended.
        private SearchResult readPage(final LDAPConnection connection,
                                      final SearchRequest request) throws LDAPException, EntrySourceException {
            final LDAPEntrySource source = new LDAPEntrySource(connection, request, false);
            boolean finished = false;
            try {
                while (true) {
                    final Entry entry;
                    try {
                        entry = source.nextEntry();
                    } catch (final SearchResultReferenceEntrySourceException e) {
                        referrals++;
                        continue;
                    } catch (final EntrySourceException e) {
                        finished = true;
                        if (endedByDirectoryLimit(e)) {
                            return null;
                        }
                        throw e;
                    }
                    if (entry == null) {
                        finished = true;
                        return source.getSearchResult();
                    }
                    if (cancellation.isCancelled()) {
                        return null;
                    }
                    if (rowsRead == limits.maxRows()) {
                        limitReached = true;
                        return null;
                    }
                    rowsRead++;
                    if (accept(entry)) {
                        rowsPassed++;
                    }
                }
            } finally {
                if (!finished) {
                    stopEarly(connection, source);
                }
                source.close();
            }
        }

        // The entry source holds entries in a queue of 100 that the connection's reader fills. Closing the
        // source, or the connection, while the queue is full waits for room that never comes, so a search
        // stopped part way through a page would hang. So the connection is closed in the background, which
        // ends the search in the directory, while this thread reads what is left, letting the reader and
        // the source finish. A source is only ever stopped this way when the whole search is.
        private static void stopEarly(final LDAPConnection connection, final LDAPEntrySource source) {
            closeInBackground(connection);
            while (true) {
                try {
                    if (source.nextEntry() == null) {
                        return;
                    }
                } catch (final EntrySourceException e) {
                    if (!e.mayContinueReading()) {
                        return;
                    }
                }
            }
        }

        private boolean accept(final Entry entry) throws LDAPException {
            try {
                return rowConsumer.accept(columnIndex -> read(entry, columnIndex));
            } catch (final SQLException e) {
                throw new LDAPException(ResultCode.LOCAL_ERROR, e.getMessage(), e);
            }
        }

        private Val read(final Entry entry, final int columnIndex) {
            if (columnIndex == 0) {
                return ValString.create(entry.getDN());
            }
            final DataColumn column = attributes.get(columnIndex - 1);
            final Attribute attribute = entry.getAttribute(column.getName());
            try {
                return LdapValues.read(attribute, column.getType());
            } catch (final IllegalArgumentException e) {
                badValues.add(example(attribute), column);
                return ValNull.INSTANCE;
            }
        }

        // A value for a warning, cut short, as binary values in base64 can be long.
        private static String example(final Attribute attribute) {
            final String text = String.join(LdapValues.VALUE_SEPARATOR, LdapValues.texts(attribute));
            return text.length() > MAX_EXAMPLE_LENGTH
                    ? text.substring(0, MAX_EXAMPLE_LENGTH) + "..."
                    : text;
        }

        // The directory's own size or time limit ends the search with the entries read so far.
        private boolean endedByDirectoryLimit(final EntrySourceException e) {
            if (e.getCause() instanceof final LDAPSearchException searchException) {
                final ResultCode resultCode = searchException.getResultCode();
                if (ResultCode.SIZE_LIMIT_EXCEEDED.equals(resultCode)) {
                    endedByDirectory = true;
                    directoryLimitWarning = "The directory's own size limit ended the search after " + rowsRead
                                            + " entries. Narrow the filter, or ask the directory's "
                                            + "administrator to raise the limit for the bind account.";
                    return true;
                }
                // The directory's own time limit, or Stroom's wait for a response, which is set a little
                // longer but may still end first.
                if (ResultCode.TIME_LIMIT_EXCEEDED.equals(resultCode) || ResultCode.TIMEOUT.equals(resultCode)) {
                    endedByDirectory = true;
                    directoryLimitWarning = "The search reached its time limit after " + rowsRead
                                            + " entries. Narrow the filter, or raise the query timeout.";
                    return true;
                }
            }
            return false;
        }

        private boolean stopped() {
            return limitReached || endedByDirectory || cancellation.isCancelled();
        }

        private List<String> warnings() {
            final List<String> warnings = new ArrayList<>();
            if (directoryLimitWarning != null) {
                warnings.add(directoryLimitWarning);
            }
            if (referrals > 0) {
                warnings.add(referrals + (referrals == 1 ? " referral was" : " referrals were")
                             + " not followed. Referrals name other servers, which the allowed LDAP URLs may "
                             + "not include; search those servers with their own Data Connections.");
            }
            badValues.warning().ifPresent(warnings::add);
            return warnings;
        }
    }
}
