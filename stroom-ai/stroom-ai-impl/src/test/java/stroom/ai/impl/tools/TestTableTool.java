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

package stroom.ai.impl.tools;

import stroom.ai.api.TableSummariser;
import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.workspace.TableEngine;
import stroom.ai.impl.workspace.TableEngine.Order;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTableTool {

    private static final int CHAT = 1;
    private static final String TABLE = "/outputs/logons.csv";

    @TempDir
    Path tempDir;

    private FakeTabWorkspace files;
    private TableTool tool;

    @BeforeEach
    void setUp() {
        files = new FakeTabWorkspace(tempDir);
        files.workspace().writeTabFile(CHAT, WorkspacePath.parse(TABLE),
                "user,n\nalice,3\nbob,1\nalice,5\ncarol,2\n", null);
        tool = new TableTool(files.workspace(), new ColumnSummariser(Mockito.mock(TableSummariser.class),
                () -> null));
    }

    @Test
    void aQueryWithExpressionsSaysTheZoneItsDatesAreIn() {
        final TableEngine.Query plain = TableTool.query(Map.of(), List::of);
        final TableEngine.Query computed = TableTool.query(Map.of("eval", List.of(
                Map.of("as", "h", "expression", "formatDate(${t}, 'HH')"))), List::of);
        final ToolTimeZone canberra = new ToolTimeZone(ZoneId.of("Australia/Canberra"),
                ToolTimeZone.Source.BROWSER, null);

        assertThat(TableTool.zoneNote(plain, canberra)).isEmpty();
        assertThat(TableTool.zoneNote(computed, canberra))
                .startsWith(" Its expressions' date rounding (floorDay, ceilHour...) and day() are in "
                            + "Australia/Canberra (+1")
                .endsWith(" formatDate and parseDate work in UTC unless given a zone, e.g. formatDate(EventTime, "
                          + "'HH:mm', 'Australia/Canberra').");
        assertThat(TableTool.zoneNote(computed, new ToolTimeZone(ZoneOffset.UTC,
                ToolTimeZone.Source.FALLBACK, UserTimeZones.DEVICE_NOT_KNOWN)))
                .isEqualTo(" Its expressions' date rounding (floorDay, ceilHour...) and day() are in UTC. "
                           + UserTimeZones.DEVICE_NOT_KNOWN);
    }

    @Test
    void aFileOfAKindThatIsNeverATableIsNotReadAsOne() {
        // Seen: a survey's JSON given to table, read as CSV, and refused for a stray quote on its first line.
        assertThatThrownBy(() -> TableTool.refuseUnlessATable(WorkspacePath.parse("/outputs/delinea_shapes.json")))
                .isInstanceOf(WorkspaceException.class)
                .hasMessage("/outputs/delinea_shapes.json is JSON, not a table: query it with jq. table reads .csv, "
                            + ".tsv and .md tables.");
        assertThatThrownBy(() -> TableTool.refuseUnlessATable(WorkspacePath.parse("/outputs/raw.xml")))
                .hasMessageContaining("read it with read_file or grep");
        assertThatCode(() -> TableTool.refuseUnlessATable(WorkspacePath.parse("/outputs/rows.csv")))
                .doesNotThrowAnyException();
        assertThatCode(() -> TableTool.refuseUnlessATable(WorkspacePath.parse("/attachments/12")))
                .doesNotThrowAnyException();
        assertThatCode(() -> TableTool.refuseUnlessATable(WorkspacePath.parse("/outputs/v1.2/table")))
                .doesNotThrowAnyException();
    }

    /// Seen in chat 140: aggregates given as a JSON string, with a stray trailing comma.
    @Test
    void aListGivenAsJsonTextWithATrailingCommaIsRead() {
        final String result = run("{\"path\":\"" + TABLE + "\",\"groupBy\":\"[\\\"user\\\"]\","
                                  + "\"aggregates\":\"[{\\\"as\\\": \\\"n\\\", \\\"function\\\": \\\"count\\\"}],\","
                                  + "\"orderBy\":\" [{\\\"column\\\": \\\"user\\\"}] \"}");

        assertThat(result)
                .startsWith("4 of 4 rows matched; the result has 3 row(s).")
                .contains("| user | n |")
                .contains("| alice | 2 |");
    }

    @Test
    void aSingleObjectWhereAListIsExpectedIsAListOfIt() {
        final String result = run("{\"path\":\"" + TABLE + "\",\"where\":{\"column\":\"user\",\"op\":\"=\","
                                  + "\"value\":\"alice\"},\"aggregates\":{\"function\":\"sum\",\"column\":\"n\","
                                  + "\"as\":\"total\"}}");

        assertThat(result)
                .startsWith("2 of 4 rows matched")
                .contains("| total |")
                .contains("| 8 |");
    }

    @Test
    void orderByMayBeGivenAsColumnNames() {
        final String result = run("{\"path\":\"" + TABLE + "\",\"orderBy\":[\"-n\"],\"limit\":2}");

        assertThat(result).contains("| alice | 5 |\n| alice | 3 |");
        assertThat(run("{\"path\":\"" + TABLE + "\",\"orderBy\":\"n\",\"limit\":1}")).contains("| bob | 1 |");
    }

    @Test
    void aColumnNameStartingWithAMinusIsSortedByAsItIs() {
        assertThat(TableTool.order("-n", () -> Set.of("n"))).isEqualTo(new Order("n", true));
        // A column called "-n" is sorted by, smallest first.
        assertThat(TableTool.order("-n", () -> Set.of("-n", "n"))).isEqualTo(new Order("-n", false));
        assertThat(TableTool.order("n", () -> Set.of())).isEqualTo(new Order("n", false));
        assertThat(TableTool.order("-", () -> Set.of())).isEqualTo(new Order("-", false));
    }

    @Test
    void textThatIsntAListOfObjectsIsRefusedSayingWhatWasGiven() {
        assertThat(run("{\"path\":\"" + TABLE + "\",\"aggregates\":\"count of users\"}"))
                .isEqualTo("Error: aggregates was text that isn't a JSON list of objects: 'count of users'.");
        assertThat(run("{\"path\":\"" + TABLE + "\",\"aggregates\":\"[{\\\"as\\\": \\\"n\\\"\"}"))
                .isEqualTo("Error: aggregates was text that isn't a JSON list of objects: '[{\"as\": \"n\"'.");
        assertThat(run("{\"path\":\"" + TABLE + "\",\"where\":[\"user = alice\"]}"))
                .isEqualTo("Error: where must be a list of objects, but one item was 'user = alice'.");
        assertThat(run("{\"path\":\"" + TABLE + "\",\"select\":{\"column\":\"user\"}}"))
                .isEqualTo("Error: select must be a list of column names, but was '{\"column\":\"user\"}'.");
        // Long values are shortened.
        assertThat(run("{\"path\":\"" + TABLE + "\",\"eval\":\"" + "x".repeat(200) + "\"}"))
                .contains("'" + "x".repeat(80) + "…'");
    }

    /// Arguments in the wrong shape are the model's to fix, so don't count towards summarising a column instead.
    @Test
    void argumentShapeErrorsDontFallBackToSummarising() {
        final ToolRunContext context = new ToolRunContext(Mockito.mock(ChatModel.class), null, () -> false);
        final String badShape = "{\"path\":\"" + TABLE + "\",\"groupBy\":[\"user\"],\"aggregates\":\"count\"}";

        for (int i = 0; i < 3; i++) {
            assertThat(context.run(() -> run(badShape)))
                    .startsWith("Error: aggregates was text")
                    .doesNotContain("summary")
                    .doesNotContain("failed");
        }
    }

    @Test
    void failuresOfAReadableQueryStillFallBackToSummarising() {
        final ToolRunContext context = new ToolRunContext(Mockito.mock(ChatModel.class), null, () -> false);
        final String badQuery = "{\"path\":\"" + TABLE + "\",\"where\":[{\"column\":\"user\",\"op\":\"like\","
                                + "\"value\":\"a\"}]}";
        final String badShape = "{\"path\":\"" + TABLE + "\",\"where\":\"user like a\"}";

        assertThat(context.run(() -> run(badQuery))).startsWith("Error:").doesNotContain("summary");
        // A shape error in between doesn't count.
        assertThat(context.run(() -> run(badShape))).startsWith("Error:").doesNotContain("summary");
        assertThat(context.run(() -> run(badQuery)))
                .startsWith("Error:")
                .contains("failed 2 times, so here is a summary of column 'user' instead");
    }

    @Test
    void inTakesItsValuesAsAListOrSeparatedByCommas() {
        assertThat(run("{\"path\":\"" + TABLE + "\",\"where\":[{\"column\":\"user\",\"op\":\"in\","
                       + "\"value\":[\"bob\",\"carol\"]}]}"))
                .startsWith("2 of 4 rows matched");
        assertThat(run("{\"path\":\"" + TABLE + "\",\"where\":[{\"column\":\"user\",\"op\":\"not_in\","
                       + "\"value\":\"bob,carol\"}]}"))
                .startsWith("2 of 4 rows matched");
    }

    /// Seen in chat 141: a result written to a .md file as CSV, which fetch_events then read as a table of one
    /// column, saying it had no StreamId and EventId.
    @Test
    void aResultIsWrittenInTheFormatItsFileIsNamedFor() {
        assertThat(run("{\"path\":\"" + TABLE + "\",\"where\":[{\"column\":\"user\",\"op\":\"=\","
                       + "\"value\":\"alice\"}],\"out\":\"/outputs/alice.md\"}"))
                .startsWith("Wrote the result to /outputs/alice.md.");
        assertThat(files.content("/outputs/alice.md")).hasValue("| user | n |\n| --- | --- |\n| alice | 3 |\n"
                                                                + "| alice | 5 |\n");
        assertThat(run("{\"path\":\"/outputs/alice.md\",\"groupBy\":[\"user\"]}"))
                .contains("| alice | 2 |");

        run("{\"path\":\"" + TABLE + "\",\"limit\":1,\"out\":\"/outputs/first.tsv\"}");
        assertThat(files.content("/outputs/first.tsv")).hasValue("user\tn\r\nalice\t3\r\n");
        run("{\"path\":\"" + TABLE + "\",\"limit\":1,\"out\":\"/outputs/first.csv\"}");
        assertThat(files.content("/outputs/first.csv")).hasValue("user,n\r\nalice,3\r\n");
    }

    @Test
    void anotherTableIsJoinedFirstAndTheQueryIsOfTheJoinedRows() {
        // Seen: a chat matched mail items read then deleted, by user and message id within 5 minutes, across two
        // files: some 15 calls of grep, jq and table, the jq join failing.
        files.workspace().writeTabFile(CHAT, WorkspacePath.parse("/outputs/accesses.csv"), """
                user,msg,time
                li.wei,m1,2026-11-10T00:00:17
                li.wei,m2,2026-11-10T00:00:17
                anja,m3,2026-11-10T00:02:24
                """, null);
        files.workspace().writeTabFile(CHAT, WorkspacePath.parse("/outputs/deletes.csv"), """
                user,msg,time
                li.wei,m1,2026-11-10T00:00:26
                li.wei,m2,2026-11-10T00:09:00
                """, null);

        final String result = run("""
                {"path":"/outputs/accesses.csv","join":{"path":"/outputs/deletes.csv","on":["user","msg"],
                "within":{"column":"time","seconds":300,"after":true}},
                "select":["user","msg","right.time"]}""");

        assertThat(result)
                .startsWith("Joined 3 rows of /outputs/accesses.csv with 2 of /outputs/deletes.csv on user, msg, "
                            + "within 300 s after: 1 of the 3 rows matched, making 1 joined rows. 1 of 1 joined "
                            + "rows matched; the result has 1 row(s).")
                .contains("| user | msg | right.time |")
                .contains("| li.wei | m1 | 2026-11-10T00:00:26 |");
        // The rows nothing matches, grouped.
        assertThat(run("""
                {"path":"/outputs/accesses.csv","join":{"path":"/outputs/deletes.csv","on":"msg",
                "keep":"unmatched"},"groupBy":["user"]}"""))
                .contains("2 of the 3 rows matched, so 1 are unmatched.")
                .contains("| anja | 1 |");
    }

    @Test
    void aJoinInAShapeThatCantBeReadIsRefusedSayingWhatWasGiven() {
        assertThat(run("{\"path\":\"" + TABLE + "\",\"join\":\"the other file\"}"))
                .startsWith("Error: join must be an object");
        assertThat(run("{\"path\":\"" + TABLE + "\",\"join\":{\"on\":[\"user\"]}}"))
                .isEqualTo("Error: Give join.path: the table to join to this one.");
        assertThat(run("{\"path\":\"" + TABLE + "\",\"join\":{\"path\":\"/outputs/x.json\",\"on\":"
                       + "[\"user\"]}}"))
                .startsWith("Error: /outputs/x.json is JSON, not a table");
        assertThat(run("{\"path\":\"" + TABLE + "\",\"join\":{\"path\":\"" + TABLE + "\",\"on\":"
                       + "[\"user\"],\"keep\":\"left\"}}"))
                .isEqualTo("Error: join.keep must be both or unmatched, but was 'left'.");
        assertThat(run("{\"path\":\"" + TABLE + "\",\"join\":{\"path\":\"" + TABLE + "\",\"on\":"
                       + "[\"user\"],\"within\":{\"column\":\"n\"}}}"))
                .isEqualTo("Error: join.within needs column (this table's time) and seconds.");
    }

    @Test
    void aTableJoinedToItselfPrefixesTheOtherCopysColumns() {
        final String result = run("{\"path\":\"" + TABLE + "\",\"join\":{\"path\":\"" + TABLE + "\","
                                  + "\"on\":[\"user\"]},\"where\":[{\"column\":\"user\",\"op\":\"=\","
                                  + "\"value\":\"alice\"}]}");

        // alice has two rows, each matching both.
        assertThat(result)
                .contains("making 6 joined rows")
                .contains("4 of 6 joined rows matched")
                .contains("| user | n | right.n |");
    }

    private String run(final String arguments) {
        return tool.execute(new ToolCall("c1", TableTool.NAME, arguments),
                ToolCalls.inChat(CHAT, files.workspace())).text();
    }
}
