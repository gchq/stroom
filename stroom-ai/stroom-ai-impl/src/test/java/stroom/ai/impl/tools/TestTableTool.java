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
import stroom.ai.impl.workspace.TableEngine.Order;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
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

    private String run(final String arguments) {
        return tool.execute(new ToolCall("c1", TableTool.NAME, arguments),
                ToolCalls.inChat(CHAT, files.workspace())).text();
    }
}
