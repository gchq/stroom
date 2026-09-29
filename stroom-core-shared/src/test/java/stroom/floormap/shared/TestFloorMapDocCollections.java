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

package stroom.floormap.shared;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests that [FloorMapDoc]'s collections are owned by the document rather than
/// shared with whoever supplied or read them.
///
/// Before this, the lists were stored and handed out by reference, so the document
/// was immutable only by convention — upheld by every presenter remembering to copy
/// before mutating, and silently broken by anything that forgot. These tests move that
/// guarantee from convention to enforcement.
class TestFloorMapDocCollections {

    private static FloorMapDoc.Builder builder() {
        return FloorMapDoc.builder().uuid("uuid-1").name("map-1");
    }

    /// Mutating the caller's list after building must not change the document.
    @Test
    void testBuilderCopiesIncomingLists() {
        final List<TypeStyle> styles = new ArrayList<>();
        styles.add(new TypeStyle("gate", null, "#111111"));

        final FloorMapDoc doc = builder().typeStyles(styles).build();

        styles.add(new TypeStyle("camera", null, "#222222"));

        assertThat(doc.getTypeStyles())
                .as("the document must not see a list the caller edited afterwards")
                .hasSize(1);
    }

    /// The document's own list cannot be edited through its getter.
    @Test
    void testGettersReturnUnmodifiableLists() {
        final FloorMapDoc doc = builder()
                .typeStyles(List.of(new TypeStyle("gate", null, "#111111")))
                .groups(List.of(new FloorMapGroup("g1", "Group 1", "#8e24aa", List.of())))
                .build();

        assertThatThrownBy(() -> doc.getTypeStyles().add(new TypeStyle("x", null, "#000000")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> doc.getGroups().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> doc.getValueSchema().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /// Two documents built from one builder must not share list instances — the
    /// aliasing that made the server's duplicate-document path share state with its
    /// source.
    @Test
    void testCopyDoesNotAliasTheOriginalsLists() {
        final FloorMapDoc original = builder()
                .typeStyles(List.of(new TypeStyle("gate", null, "#111111")))
                .build();

        final FloorMapDoc duplicate = original.copy().uuid("uuid-2").name("map-2").build();

        assertThat(duplicate.getTypeStyles()).isEqualTo(original.getTypeStyles());
        assertThat(duplicate.getTypeStyles())
                .as("equal in content but not the same instance")
                .isNotSameAs(original.getTypeStyles());
    }

    /// Absent stays absent: null must not be normalised to an empty list.
    @Test
    void testNullCollectionsArePreserved() {
        final FloorMapDoc doc = builder().build();
        assertThat(doc.getTypeStyles()).isNull();
        assertThat(doc.getGroups()).isNull();
    }

    /// An unset value schema still yields the immutable default.
    @Test
    void testAbsentValueSchemaYieldsTheImmutableDefault() {
        final FloorMapDoc doc = builder().build();
        assertThat(doc.getValueSchema()).isNotEmpty();
        assertThatThrownBy(() -> doc.getValueSchema().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
