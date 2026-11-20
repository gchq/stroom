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

package stroom.mcp.impl.e2e;

import stroom.dataconnection.impl.DataConnectionStore;
import stroom.dataconnection.shared.CsvSettings;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.docref.DocRef;
import stroom.mcp.impl.McpTestClient;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// StroomQL joins through the REAL validate_stroomql and run_stroomql, over `/mcp`, on two CSV Data Connections
/// (events, and the HR users they are joined to), as upstream's `TestJoinSearchRunnerIntegration` makes them: a left
/// join that enriches events and groups the joined rows, and a `where X in (from …)` subquery with no `limit`, which
/// run_stroomql caps at the top level, not inside the subquery (found reviewing the tools against the joins: the
/// limit went inside the subquery, and Stroom refused it).
class TestE2eStroomQlJoins extends AbstractE2eTest {

    private static final String EVENTS_CSV = """
            UserId,Host,Bytes
            jbloggs,h1,100
            ssmith,h2,200
            nobody,h3,300
            jbloggs,h4,400
            leaver,h5,500
            """;
    private static final String HR_CSV = """
            Username,Department,Active
            jbloggs,Sales,true
            ssmith,IT,true
            leaver,Sales,false
            """;
    private static final Pattern RESULTS_PATH = Pattern.compile("The results are in (\\S+\\.md)\\.");

    @Inject
    private DataConnectionStore store;

    private String events;
    private String hr;

    @BeforeEach
    void createDataConnections() {
        events = createCsv("Events", EVENTS_CSV, List.of(
                new DataColumn("UserId", DataColumnType.TEXT),
                new DataColumn("Host", DataColumnType.TEXT),
                new DataColumn("Bytes", DataColumnType.LONG)));
        hr = createCsv("HR", HR_CSV, List.of(
                new DataColumn("Username", DataColumnType.TEXT),
                new DataColumn("Department", DataColumnType.TEXT),
                new DataColumn("Active", DataColumnType.BOOLEAN)));
    }

    @Test
    void aLeftJoinIsValidatedAndRunWithItsJoinedFields() {
        final String query = """
                from "%s"
                left join "%s" as hr on UserId = hr.Username
                group by hr.Department
                select hr.Department, sum(Bytes) as Total""".formatted(events, hr);

        assertThat(replyText("validate_stroomql", args("query", query)))
                .startsWith("OK. The query is valid and reads DataConnection '" + events + "'")
                .contains(", joined with DataConnection '" + hr + "'")
                .contains("It groups rows.");

        // Grouped after the join: Sales holds jbloggs' and leaver's bytes, and nobody has no department.
        assertThat(rows(replyText("run_stroomql", args("query", query)), "hr.Department", "Total"))
                .containsExactlyInAnyOrder(
                        List.of("Sales", "1000"),
                        List.of("IT", "200"),
                        List.of("", "300"));
    }

    @Test
    void aWhereSubqueryWithNoLimitRunsCappedAtTheTopLevel() {
        final String query = """
                from "%s"
                where UserId in (from "%s" where Active = true select Username)
                select UserId, Host""".formatted(events, hr);

        final String result = replyText("run_stroomql", args("query", query));

        assertThat(result).contains("limit");
        assertThat(rows(result, "UserId", "Host")).containsExactlyInAnyOrder(
                List.of("jbloggs", "h1"),
                List.of("ssmith", "h2"),
                List.of("jbloggs", "h4"));
    }

    @Test
    void anInvalidQueryIsRefusedWithTheParsersErrorForTheQueryAsWritten() {
        // Group by after select, as Haiku wrote it in eval case 71: the limit run_stroomql adds before select must
        // not be the token the error names, which the agent never wrote.
        final String query = "from \"" + events + "\" select UserId, count() as N group by UserId";

        assertThat(replyText("run_stroomql", args("query", query)))
                .isEqualTo("Error: The query failed: Unexpected token GROUP after SELECT. In StroomQL, every clause "
                           + "but show goes before select: from, join lines, where, eval, window, filter, sort by, "
                           + "group by, having, limit, select, show.");
        assertThat(replyText("validate_stroomql", args("query", query)))
                .startsWith("The query is not valid: Unexpected token GROUP after SELECT. In StroomQL, every clause "
                            + "but show goes before select: from, join lines, where, eval, window, filter, sort by, "
                            + "group by, having, limit, select, show.");
    }

    @Test
    void aQueryWithOrderByIsToldStroomQlUsesSortBy() {
        // Seen with Haiku in eval cases 72 and 73: Stroom's own error names neither order nor sort.
        final String query = "from \"" + events + "\" select UserId, Bytes order by Bytes";

        assertThat(replyText("run_stroomql", args("query", query)))
                .startsWith("Error: The query failed: ")
                .endsWith(" StroomQL uses sort by, not order by.");
        assertThat(replyText("validate_stroomql", args("query", query)))
                .startsWith("The query is not valid: ")
                .contains("StroomQL uses sort by, not order by.");
    }

    @Test
    void aJoinLineAfterWhereIsToldJoinLinesGoStraightAfterFrom() {
        // Seen with Sonnet in eval case 71: Stroom's error names only the token it stopped at.
        final String query = "from \"" + events + "\" where Bytes > 100 join \"" + hr + "\" as hr on UserId = "
                             + "hr.Username select UserId, hr.Department";

        assertThat(replyText("run_stroomql", args("query", query)))
                .startsWith("Error: The query failed: Unexpected token JOIN after WHERE")
                .endsWith(" Join lines go straight after from.");
        assertThat(replyText("validate_stroomql", args("query", query)))
                .startsWith("The query is not valid: Unexpected token JOIN after WHERE")
                .contains(" Join lines go straight after from.");
    }

    @Test
    void aJoinedDataSourceThatDoesNotExistIsTheParsersError() {
        final String query = "from \"" + events + "\" join \"No such HR\" as hr on UserId = hr.Username "
                             + "select UserId";

        assertThat(replyText("validate_stroomql", args("query", query)))
                .startsWith("The query is not valid: ")
                .contains("No such HR");
    }

    private String createCsv(final String name, final String csv, final List<DataColumn> columns) {
        return asUser(admin, () -> {
            final DocRef docRef = store.createDocument(name + " " + UUID.randomUUID());
            final DataConnectionDoc doc = store.readDocument(docRef);
            store.writeDocument(doc.copy()
                    .sourceType(DataConnectionType.CSV)
                    .csv(CsvSettings.builder()
                            .columns(columns)
                            .data(csv)
                            .build())
                    .build());
            return docRef.getName();
        });
    }

    // The named columns of the table a run_stroomql result wrote, a row each.
    private List<List<String>> rows(final String result, final String... columns) {
        final Matcher matcher = RESULTS_PATH.matcher(result);
        assertThat(matcher.find()).as("run_stroomql wrote its results: %s", result).isTrue();
        final String file = McpTestClient.text(client.call("read_file", args("path", matcher.group(1),
                "count", 1_000)));
        final List<List<String>> rows = new ArrayList<>();
        List<String> header = null;
        for (final String line : file.split("\n")) {
            final int tab = line.indexOf('\t');
            final String row = tab < 0
                    ? line
                    : line.substring(tab + 1);
            if (!row.startsWith("|") || row.startsWith("| ---") || row.startsWith("|---")) {
                continue;
            }
            final List<String> cells = new ArrayList<>();
            for (final String cell : row.substring(1, row.lastIndexOf('|')).split("\\|", -1)) {
                cells.add(cell.strip());
            }
            if (header == null) {
                header = cells;
            } else {
                final List<String> picked = new ArrayList<>();
                for (final String column : columns) {
                    picked.add(cells.get(header.indexOf(column)));
                }
                rows.add(picked);
            }
        }
        assertThat(header).as("run_stroomql's table: %s", file).isNotNull().contains(columns);
        return rows;
    }
}
