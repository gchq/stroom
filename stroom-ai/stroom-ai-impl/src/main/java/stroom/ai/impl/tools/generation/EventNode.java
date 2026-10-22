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

import stroom.ai.impl.tools.validation.EventSchema.Child;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// An element one rule's events may hold, as [EventTrees] builds them from the mapping: its schema child (null for
/// Event itself), its path, and what is mapped at or below it.
final class EventNode {

    final Child child;
    final String path;
    final Map<String, EventNode> kids = new LinkedHashMap<>();
    final List<DataEntry> data = new ArrayList<>();
    FieldMapping leaf;
    // this element is written once per value of the entry's input
    FieldMapping repeat;
    // written by a shared XSLT's named template, called here
    SharedTemplate shared;

    /// @param child The element's schema child, or null for Event.
    /// @param path  Its path from Event, e.g. `Event/EventSource/User`.
    EventNode(final Child child, final String path) {
        this.child = child;
        this.path = path;
    }


    // --------------------------------------------------------------------------------


    /// A Data entry below the element: its schema child (Data) and the entry.
    ///
    /// @param child The Data element's schema child.
    /// @param entry The field mapping writing it.
    record DataEntry(Child child, FieldMapping entry) {

    }
}
