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

package stroom.ai.impl.workspace;

import stroom.ai.impl.workspace.TableEngine.Format;
import stroom.ai.impl.workspace.TableJoin.Joined;
import stroom.ai.impl.workspace.TableJoin.Keep;
import stroom.ai.impl.workspace.TableJoin.Spec;
import stroom.ai.impl.workspace.TableJoin.Within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestTableJoin {

    // Mail items read, then moved to Deleted Items, by message id: the shape of the chat that needed a join.
    private static final String ACCESSES = """
            user,msg,time,folder
            li.wei,m1,2026-11-10T00:00:17,Inbox
            li.wei,m2,2026-11-10T00:00:17,Inbox
            anja,m3,2026-11-10T00:02:24Z,Drafts
            anja,m4,2026-11-10T01:00:00Z,Inbox
            kate,,2026-11-10T00:00:00Z,Inbox
            """;
    private static final String DELETES = """
            user,msg,time,folder
            li.wei,m1,2026-11-10T00:00:26,Deleted Items
            li.wei,m2,2026-11-10T00:09:00,Deleted Items
            anja,m3,2026-11-10T00:01:00Z,Deleted Items
            omar,m4,2026-11-10T01:00:30Z,Deleted Items
            kate,,2026-11-10T00:00:10Z,Deleted Items
            """;

    @TempDir
    Path tempDir;

    @Test
    void rowsAreJoinedOnTheirKeysAndARightColumnTheLeftHasIsPrefixed() {
        final Joined joined = join(new Spec(List.of("user", "msg"), List.of(), Keep.BOTH, null));

        assertThat(joined.columns()).containsExactly("user", "msg", "time", "folder", "right.time",
                "right.folder");
        assertThat(joined.rows()).extracting(row -> row.get(1)).containsExactly("m1", "m2", "m3");
        assertThat(joined.rows().getFirst()).containsExactly("li.wei", "m1", "2026-11-10T00:00:17", "Inbox",
                "2026-11-10T00:00:26", "Deleted Items");
        assertThat(joined.leftRows()).isEqualTo(5);
        assertThat(joined.rightRows()).isEqualTo(5);
        assertThat(joined.leftMatched()).isEqualTo(3);
    }

    @Test
    void withinAfterKeepsOnlyARightRowAtOrSoonAfterTheLeftOne() {
        final Joined joined = join(new Spec(List.of("user", "msg"), List.of(), Keep.BOTH,
                new Within("time", null, 300, true)));

        // m2 was deleted 8 minutes later; m3's delete came before the access.
        assertThat(joined.rows()).extracting(row -> row.get(1)).containsExactly("m1");
        // Either side: m3's delete, 84 s before, counts too.
        final Joined around = join(new Spec(List.of("user", "msg"), List.of(), Keep.BOTH,
                new Within("time", null, 300, false)));
        assertThat(around.rows()).extracting(row -> row.get(1)).containsExactly("m1", "m3");
    }

    @Test
    void unmatchedKeepsTheLeftRowsNothingMatchesAndAnEmptyKeyMatchesNothing() {
        final Joined joined = join(new Spec(List.of("msg"), List.of(), Keep.UNMATCHED, null));

        assertThat(joined.columns()).containsExactly("user", "msg", "time", "folder");
        // kate's empty msg isn't matched to the other empty msg.
        assertThat(joined.rows()).extracting(row -> row.getFirst()).containsExactly("kate");
        assertThat(joined.leftMatched()).isEqualTo(4);
    }

    @Test
    void columnsNamedDifferentlyAreJoinedWithRightOn() {
        final Path left = file("left.csv", "who,id\nli.wei,m1\n");
        final Path right = file("right.csv", "account,message,size\nli.wei,m1,10\n");

        final Joined joined = TableJoin.join(left, Format.CSV, right, Format.CSV,
                new Spec(List.of("who", "id"), List.of("account", "message"), Keep.BOTH, null));

        assertThat(joined.columns()).containsExactly("who", "id", "size");
        assertThat(joined.rows()).containsExactly(List.of("li.wei", "m1", "10"));
    }

    @Test
    void aMissingColumnOrMismatchedKeysAreRefusedSayingWhatThereIs() {
        assertThatThrownBy(() -> join(new Spec(List.of("message"), List.of(), Keep.BOTH, null)))
                .isInstanceOf(WorkspaceException.class)
                .hasMessage("There is no column 'message' in the table. Its columns are: user, msg, time, folder.");
        assertThatThrownBy(() -> join(new Spec(List.of("user", "msg"), List.of("user"), Keep.BOTH, null)))
                .hasMessageContaining("as many columns as join.on (2)");
        assertThatThrownBy(() -> join(new Spec(List.of(), List.of(), Keep.BOTH, null)))
                .hasMessageContaining("Give join.on");
        assertThatThrownBy(() -> join(new Spec(List.of("msg"), List.of(), Keep.BOTH,
                new Within("when", null, 60, false))))
                .hasMessageContaining("no column 'when'");
    }

    @Test
    void aColumnNameDifferingOnlyInCaseIsFound() {
        final Joined joined = join(new Spec(List.of("USER", "Msg"), List.of(), Keep.BOTH, null));

        assertThat(joined.rows()).hasSize(3);
    }

    @Test
    void timesAreReadAsIsoUtcOrEpochMillis() {
        assertThat(TableJoin.millis("2026-11-10T00:00:17Z")).isEqualTo(1794268817000L);
        assertThat(TableJoin.millis("2026-11-10T00:00:17")).isEqualTo(1794268817000L);
        assertThat(TableJoin.millis("2026-11-10 00:00:17")).isEqualTo(1794268817000L);
        assertThat(TableJoin.millis("2026-11-10T00:00:17.000+00:00")).isEqualTo(1794268817000L);
        assertThat(TableJoin.millis("1794268817000")).isEqualTo(1794268817000L);
        assertThat(TableJoin.millis("")).isNull();
        assertThat(TableJoin.millis("yesterday")).isNull();
    }

    @Test
    void aRowWithoutATimeMatchesNothingAndIsCounted() {
        final Path left = file("left.csv", "user,time\nli.wei,soon\nanja,2026-11-10T00:00:00Z\n");
        final Path right = file("right.csv", "user,time\nli.wei,2026-11-10T00:00:00Z\nanja,2026-11-10T00:00:01Z\n");

        final Joined joined = TableJoin.join(left, Format.CSV, right, Format.CSV,
                new Spec(List.of("user"), List.of(), Keep.BOTH, new Within("time", null, 60, false)));

        assertThat(joined.rows()).extracting(row -> row.getFirst()).containsExactly("anja");
        assertThat(joined.untimed()).isEqualTo(1);
    }

    @Test
    void withinIsInclusiveAtItsEnds() {
        final Within after = new Within("t", null, 10, true);
        assertThat(TableJoin.within(after, 1_000L, 1_000L)).isTrue();
        assertThat(TableJoin.within(after, 1_000L, 11_000L)).isTrue();
        assertThat(TableJoin.within(after, 1_000L, 11_001L)).isFalse();
        assertThat(TableJoin.within(after, 1_000L, 999L)).isFalse();
        assertThat(TableJoin.within(new Within("t", null, 10, false), 11_000L, 1_000L)).isTrue();
        assertThat(TableJoin.within(after, null, 1_000L)).isFalse();
    }

    private Joined join(final Spec spec) {
        return TableJoin.join(file("accesses.csv", ACCESSES), Format.CSV, file("deletes.csv", DELETES), Format.CSV,
                spec);
    }

    private Path file(final String name, final String content) {
        try {
            return Files.writeString(tempDir.resolve(name), content);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
