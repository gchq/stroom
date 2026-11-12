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

package stroom.ai.impl.tools.generation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A group entry as the mapping reads it: its sources, its fields' reads of the items, and what is refused.
class TestFieldGroup extends GenerationFixtures {

    private static FieldMapping group(final String text) {
        return FieldMapping.fromJson(json(text));
    }

    // A group nested depth deep, each level a User Data holding the next.
    private static Map<String, Object> nested(final int depth) {
        Map<String, Object> inner = json("""
                {"path": "Data", "data_name": "leaf", "item": true}""");
        for (int level = depth; level >= 1; level--) {
            final Map<String, Object> group = new LinkedHashMap<>();
            group.put("path", level == 1
                    ? "EventDetail/Send/Data"
                    : "Data");
            group.put("data_name", "level" + level);
            group.put("for_each", Map.of("split", "list" + level, "on", ",", "scope", "record"));
            group.put("as", "item" + level);
            group.put("fields", List.of(inner));
            inner = group;
        }
        return inner;
    }

    @Test
    void aGroupReadsItsSourceAsAndFields() {
        final FieldMapping entry = group("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ","},
                 "as": "recipient",
                 "fields": [{"path": "EventDetail/Send/Destination/User/Id", "item": true},
                            {"path": "Domain", "item": "recipient", "transform": "domain"}]}""");
        assertThat(entry.isGroup()).isTrue();
        assertThat(entry.group().forEach().kind()).isEqualTo(ItemSource.SPLIT);
        assertThat(entry.group().itemName()).isEqualTo("recipient");
        // A field's path given from Event is kept relative to the group.
        assertThat(entry.group().fields()).extracting(FieldMapping::path).containsExactly("Id", "Domain");
        assertThat(entry.group().fields()).extracting(FieldMapping::item).containsExactly("", "recipient");
        assertThat(FieldGroup.leaves(List.of(entry))).extracting(leaf -> leaf.entry().path())
                .containsExactly("EventDetail/Send/Destination/User/Id", "EventDetail/Send/Destination/User/Domain");
        assertThat(FieldGroup.itemsText(FieldGroup.leaves(List.of(entry)).getFirst().groups()))
                .isEqualTo("each recipient (each of `recipients` split on ',')");
        // Kept as given: item true written as true, the group's keys after the entry's own.
        assertThat(entry.toJson(DumpMode.COMPACT)).containsOnlyKeys("path", "for_each", "as", "fields");
        assertThat(FieldMapping.fromJson(entry.toJson(DumpMode.COMPACT))).isEqualTo(entry);
    }

    @Test
    void aDataGroupsDataAreDocumentedAsDataWrittenInIt() {
        final FieldMapping entry = group("""
                {"path": "EventDetail/Send/Data", "data_name": "recipient", "for_each": {"array": "recipients"},
                 "fields": [{"path": "Data", "data_name": "id", "field": "id"}]}""");
        final FieldGroup.Leaf leaf = FieldGroup.leaves(List.of(entry)).getFirst();
        assertThat(leaf.entry().path()).isEqualTo("EventDetail/Send/Data");
        assertThat(leaf.entry().dataKey()).isEqualTo("recipient/id");
        assertThat(leaf.grouped()).isTrue();
    }

    @Test
    void groupsNestSixteenDeepAtMost() {
        assertThat(FieldMapping.fromJson(nested(FieldGroup.MAX_DEPTH)).isGroup()).isTrue();
        assertThatThrownBy(() -> FieldMapping.fromJson(nested(FieldGroup.MAX_DEPTH + 1)))
                .hasMessageContaining("groups nest 16 deep at most");
    }

    @Test
    void whatAGroupAndItsFieldsMayReadIsChecked() {
        assertThatThrownBy(() -> group("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "r", "on": ","},
                 "fields": [{"path": "Id", "field": "address"}]}"""))
                .hasMessageContaining("are pieces of text (a split), with no keys: read the piece with item: true");
        assertThatThrownBy(() -> group("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"zip": {"a": "x", "b": "y"}},
                 "fields": [{"path": "Id", "item": true}]}"""))
                .hasMessageContaining("a zip's item is one value from each list, read by its name");
        assertThatThrownBy(() -> group("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"zip": {"a": "x", "b": "y"}},
                 "fields": [{"path": "Id", "field": "c"}]}"""))
                .hasMessageContaining("a zip's item has the names ['a', 'b'], one value each: not ['c']");
        assertThatThrownBy(() -> group("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"array": "r"},
                 "fields": [{"path": "Id", "field": "id", "item": "sender"}]}"""))
                .hasMessageContaining("item 'sender' names no group it is in");
        assertThatThrownBy(() -> group("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"array": "r"},
                 "fields": [{"path": "EventSource/User/Id", "field": "id"}]}"""))
                .hasMessageContaining("is outside the group's element EventDetail/Send/Destination/User");
        assertThatThrownBy(() -> group("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"array": "r"},
                 "fields": [{"path": "Id", "field": "id", "repeat": true}]}"""))
                .hasMessageContaining("a group's field is written once per item already");
        assertThatThrownBy(() -> group("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"array": "r"}, "field": "x",
                 "fields": [{"path": "Id", "field": "id"}]}"""))
                .hasMessageContaining("it has no input of its own, so no field");
        assertThatThrownBy(() -> group("""
                {"path": "EventDetail/Send/Data", "data_name": "r", "for_each": {"array": "r"},
                 "fields": [{"path": "Id", "field": "id"}]}"""))
                .hasMessageContaining("a Data group holds Data entries only");
        assertThatThrownBy(() -> group("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "r"},
                 "fields": [{"path": "Id", "item": true}]}"""))
                .hasMessageContaining("split names the field whose text is split, and on the separator");
        assertThatThrownBy(() -> group("""
                {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "r", "on": ",", "array": "r"},
                 "fields": [{"path": "Id", "item": true}]}"""))
                .hasMessageContaining("give exactly one of split");
    }
}
