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

package stroom.dashboard.client.component;

import stroom.query.api.Column;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestRenameColumnController {

    private final List<String> formattingRenames = new ArrayList<>();
    private final List<Column> renamed = new ArrayList<>();
    private RenameColumnController controller;

    @BeforeEach
    void setUp() {
        // The dialog's content is a Composite, which can only be mocked with GWT's bridge disarmed
        GWTMockUtilities.disarm();
        controller = new RenameColumnController(new SimpleEventBus(), Mockito.mock(RenameColumn.class));
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void aNameInUseRenamesNothing() {
        // Before, the conditional formatting rules were renamed before the name was checked, so a
        // refused rename had already changed them
        final boolean mayClose = controller.applyRename(column("Name"), "Count",
                (from, to) -> formattingRenames.add(from + " -> " + to),
                "Count"::equals,
                (column, newColumn) -> renamed.add(newColumn),
                () -> {
                });

        assertThat(mayClose).isFalse();
        assertThat(formattingRenames).isEmpty();
        assertThat(renamed).isEmpty();
    }

    @Test
    void aFreeNameRenamesTheColumnAndItsFormatting() {
        final boolean mayClose = controller.applyRename(column("Name"), "Label",
                (from, to) -> formattingRenames.add(from + " -> " + to),
                "Count"::equals,
                (column, newColumn) -> renamed.add(newColumn),
                () -> {
                });

        assertThat(mayClose).isTrue();
        assertThat(formattingRenames).containsExactly("Name -> Label");
        assertThat(renamed).extracting(Column::getName).containsExactly("Label");
    }

    @Test
    void aBlankOrUnchangedNameRenamesNothing() {
        assertThat(controller.applyRename(column("Name"), " ",
                (from, to) -> formattingRenames.add(from), name -> false,
                (column, newColumn) -> renamed.add(newColumn), () -> {
                })).isTrue();
        assertThat(controller.applyRename(column("Name"), "Name",
                (from, to) -> formattingRenames.add(from), name -> false,
                (column, newColumn) -> renamed.add(newColumn), () -> {
                })).isTrue();

        assertThat(formattingRenames).isEmpty();
        assertThat(renamed).isEmpty();
    }

    private static Column column(final String name) {
        return Column.builder().id("1").name(name).build();
    }
}
