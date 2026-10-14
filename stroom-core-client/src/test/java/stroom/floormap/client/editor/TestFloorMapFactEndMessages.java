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

package stroom.floormap.client.editor;

import stroom.floormap.client.editor.FloorMapEditorModel.FactEndCheck;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapFactEndMessages {

    private static final Map<String, FactEndCheck> NONE_SKIPPED = Map.of();

    /// One fact with no later versions is deleted "onwards", in the singular, with the undo
    /// instruction and nothing about later versions or skipped facts.
    @Test
    void testConfirmation_singleWithNoLaterVersions() {
        final String message = FloorMapFactEndMessages.confirmation(
                List.of("desk-1"), NONE_SKIPPED, Map.of());

        assertThat(message)
                .startsWith("Delete 'desk-1' from the current timeline time onwards? ")
                .contains("so it still shows before this time.")
                .endsWith(" To undo, delete the 'Deleted' version from the Time List.")
                .doesNotContain("However")
                .doesNotContain("could not be checked")
                .doesNotContain("left as they are");
    }

    /// Several facts are counted, not named, and described in the plural.
    @Test
    void testConfirmation_pluralWithNoLaterVersions() {
        final String message = FloorMapFactEndMessages.confirmation(
                List.of("desk-1", "desk-2"), NONE_SKIPPED, Map.of());

        assertThat(message)
                .startsWith("Delete 2 objects from the current timeline time onwards? ")
                .contains("so they still show before this time.");
    }

    /// A single fact's later versions are counted, singular and plural, with how to keep it
    /// deleted; "onwards" is dropped, since it will come back.
    @Test
    void testConfirmation_singleWithLaterVersions() {
        assertThat(FloorMapFactEndMessages.confirmation(
                List.of("desk-1"), NONE_SKIPPED, Map.of("desk-1", 1)))
                .startsWith("Delete 'desk-1' from the current timeline time? ")
                .contains(" However, 'desk-1' has 1 later version, which will bring it back")
                .contains("To keep it deleted");
        assertThat(FloorMapFactEndMessages.confirmation(
                List.of("desk-1"), NONE_SKIPPED, Map.of("desk-1", 3)))
                .contains("'desk-1' has 3 later versions");
    }

    /// Across several facts, only those with later versions are named.
    @Test
    void testConfirmation_pluralWithLaterVersions() {
        final String message = FloorMapFactEndMessages.confirmation(
                List.of("desk-1", "desk-2", "desk-3"), NONE_SKIPPED, Map.of("desk-2", 2));

        assertThat(message)
                .startsWith("Delete 3 objects from the current timeline time? ")
                .contains(" However, 1 of them have later versions, which will bring them back: "
                          + "'desk-2' has 2 later versions.")
                .contains("To keep them deleted")
                .doesNotContain("'desk-1'");
    }

    /// When later versions could not be fetched the message says so rather than claiming
    /// there are none.
    @Test
    void testConfirmation_laterVersionsUnknown() {
        assertThat(FloorMapFactEndMessages.confirmation(List.of("desk-1"), NONE_SKIPPED, null))
                .startsWith("Delete 'desk-1' from the current timeline time? ")
                .contains("Later versions could not be checked: if it has any, it will come back");
        assertThat(FloorMapFactEndMessages.confirmation(List.of("desk-1", "desk-2"), NONE_SKIPPED, null))
                .contains("any object that has them will come back");
    }

    /// Facts that cannot be ended are counted and each given its reason, after the undo
    /// instruction.
    @Test
    void testConfirmation_listsSkipped() {
        final Map<String, FactEndCheck> skipped = new LinkedHashMap<>();
        skipped.put("desk-2", FactEndCheck.ALREADY_DELETED);
        skipped.put("desk-3", FactEndCheck.NOT_PRESENT);

        final String message = FloorMapFactEndMessages.confirmation(List.of("desk-1"), skipped, Map.of());

        assertThat(message).endsWith(" To undo, delete the 'Deleted' version from the Time List. "
                                     + "2 will be left as they are. "
                                     + "'desk-2' is already deleted at this time; "
                                     + "'desk-3' does not exist at this time.");
    }

    /// The warning when nothing can be ended gives each fact's reason.
    @Test
    void testNothingToEnd() {
        assertThat(FloorMapFactEndMessages.nothingToEnd(Map.of("desk-1", FactEndCheck.UNREADABLE)))
                .isEqualTo("Nothing can be deleted from this time. "
                           + "'desk-1' has a value that cannot be read.");
    }

    /// A long list names only the first few, and says how many more there are, so a large
    /// selection does not produce an unreadable dialog.
    @Test
    void testNothingToEnd_namesAtMostAFew() {
        final Map<String, FactEndCheck> skipped = new LinkedHashMap<>();
        for (int i = 1; i <= FloorMapFactEndMessages.MAX_NAMED + 2; i++) {
            skipped.put("desk-" + i, FactEndCheck.NOT_PRESENT);
        }

        final String message = FloorMapFactEndMessages.nothingToEnd(skipped);

        assertThat(message)
                .contains("'desk-" + FloorMapFactEndMessages.MAX_NAMED + "'")
                .doesNotContain("'desk-" + (FloorMapFactEndMessages.MAX_NAMED + 1) + "'")
                .endsWith(" (and 2 more).");
    }
}
