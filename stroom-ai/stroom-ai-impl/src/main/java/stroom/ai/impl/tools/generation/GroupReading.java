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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// A repeated group's loop, as [XsltReader] reads it: the `xsl:for-each` the generator writes a group's element in
/// once per item ([FieldGroup]), its item held in a variable.
///
/// Expressions are as written, the record's variables replaced by what they select; the loop's own variable (and a
/// zip's lists' variables) are left as they are, so what reads the item can be told from what reads the record.
///
/// @param items    What the loop selects: a split's pieces, an array's members, or (a zip) `1 to count($list)`.
/// @param variable The variable holding the item (the group's `as`).
/// @param lists    For a zip: each list's variable, in order, to what it selects; else empty.
/// @param leaves   What each item's element holds, paths relative to it (a Data group's Data entries as `Data`);
///                 a group inside it as a leaf with a group of its own.
public record GroupReading(String items,
                           String variable,
                           Map<String, String> lists,
                           List<LeafReading> leaves) {

    /// Copies the lists given.
    public GroupReading {
        lists = Collections.unmodifiableMap(new LinkedHashMap<>(lists));
        leaves = List.copyOf(leaves);
    }

    /// @param parser The parser of the XSLT it was read from.
    /// @return What it writes, to compare with another reading's: its items, its variable, its lists, and each
    /// leaf's key and form.
    public List<Object> form(final XPathTreeParser parser) {
        final List<Object> listForms = new ArrayList<>();
        lists.forEach((name, select) -> listForms.add(Arrays.asList(name, XsltReader.keyOf(select, parser))));
        final List<Object> leafForms = new ArrayList<>();
        for (final LeafReading leaf : leaves) {
            leafForms.add(Arrays.asList(leaf.key(), leaf.form(parser)));
        }
        return Arrays.asList("group", XsltReader.keyOf(items, parser), variable, listForms, leafForms);
    }

    /// @return It as a map, for the reader's dumps: items, variable, lists, leaves.
    public Map<String, Object> toMap() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("variable", variable);
        out.put("lists", lists);
        out.put("leaves", leaves.stream().map(LeafReading::toMap).toList());
        return out;
    }
}
