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

package stroom.query.client.result.vis;

import stroom.query.api.ColumnRef;
import stroom.visualisation.shared.VisSelectionKey;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestVisSelectionModel {

    private static final List<ColumnRef> NAME_AND_VALUE = List.of(
            new ColumnRef("name", "name"),
            new ColumnRef("value", "value"));

    @Test
    void testDeclaredKeys() {
        // Regression test (#6059): the keys a visualisation declares, not just name and value
        final List<ColumnRef> columnRefs = VisSelectionModel.columnRefsOf(List.of(
                new VisSelectionKey("series", "Series", "TEXT"),
                new VisSelectionKey("x", "X", "DATE_TIME"),
                new VisSelectionKey("y", "Y", "NUMBER")));

        assertThat(columnRefs).containsExactly(
                new ColumnRef("series", "series"),
                new ColumnRef("x", "x"),
                new ColumnRef("y", "y"));
    }

    @Test
    void testUndeclaredKeys() {
        // A visualisation that doesn't declare its keys keeps name and value, as before keys were
        // declared
        assertThat(VisSelectionModel.columnRefsOf(null)).isEqualTo(NAME_AND_VALUE);
    }

    @Test
    void testNoKeys() {
        // A visualisation in which nothing can be selected declares that it has no keys
        assertThat(VisSelectionModel.columnRefsOf(List.of())).isEmpty();
    }

    @Test
    void testOnlyUnusableKeys() {
        // Keys that are blank or missing are left out, which leaves none
        assertThat(VisSelectionModel.columnRefsOf(Arrays.asList(
                new VisSelectionKey(null, "Series", "TEXT"),
                new VisSelectionKey("  ", null, null),
                null)))
                .isEmpty();
    }

    @Test
    void testKeysWithSpacesLeftOut() {
        // A trimmed key would not match the one the visualisation sends its selections under
        assertThat(VisSelectionModel.columnRefsOf(List.of(
                new VisSelectionKey(" lat ", null, null),
                new VisSelectionKey("lon", null, null))))
                .containsExactly(new ColumnRef("lon", "lon"));
    }

    @Test
    void testKeysOnce() {
        assertThat(VisSelectionModel.columnRefsOf(List.of(
                new VisSelectionKey("lat", null, null),
                new VisSelectionKey("lon", null, null),
                new VisSelectionKey("lat", "Latitude", "NUMBER"))))
                .containsExactly(
                        new ColumnRef("lat", "lat"),
                        new ColumnRef("lon", "lon"));
    }
}
