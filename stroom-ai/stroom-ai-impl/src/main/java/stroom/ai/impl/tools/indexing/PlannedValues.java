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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.validation.LockedSaxon;
import stroom.util.shared.NullSafe;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The values an index plan's indexing XSLT writes for each event, read from the Events themselves by each field's
/// source path, as [ElasticDocument] and [IndexingXslt] write them: the events `drop_when` drops left out, the
/// stand-ins left out (or written as null) passed over, the `outcome` transform applied, a field of one value
/// written as all its source's values joined by a space (as `xsl:value-of` writes them), and a field of several
/// values as each (once each, for a union of paths). So verify_index can count what a search of the index must find
/// without asking the index (seen in chat 177: an agent took "every expected count from the index itself" and
/// verified the index against itself).
///
/// A field whose values aren't the Events' own (a shared template's call, StreamId and EventId, an analysed text
/// field, a date the index converts, an object made distinct by a key) has no values here: [#written] says why.
public final class PlannedValues {

    private final FieldPlan plan;
    private final List<XdmNode> events;
    private final List<Integer> eventsPerRecord;
    private final int dropped;
    private final String unreadable;
    private final Map<String, XPathExecutable> compiled = new HashMap<>();

    private PlannedValues(final FieldPlan plan,
                          final List<XdmNode> events,
                          final List<Integer> eventsPerRecord,
                          final int dropped,
                          final String unreadable) {
        this.plan = plan;
        this.events = events;
        this.eventsPerRecord = eventsPerRecord;
        this.dropped = dropped;
        this.unreadable = unreadable;
    }

    /// Reads Events records, each an `<Events>` document of one or more `<Event>`s, as the plan's XSLT reads them.
    ///
    /// @param plan    The index plan.
    /// @param records The records, in stream order; one that is null or isn't XML counts as a record of no events.
    /// @return The events the plan's XSLT indexes, or one that can't be read (its `drop_when` doesn't compile).
    public static PlannedValues of(final FieldPlan plan, final List<String> records) {
        final List<XdmNode> kept = new ArrayList<>();
        final List<Integer> perRecord = new ArrayList<>();
        int dropped = 0;
        final Map<String, XPathExecutable> drops = new HashMap<>();
        for (final String record : NullSafe.list(records)) {
            final List<XdmNode> events = record == null
                    ? List.of()
                    : events(record);
            perRecord.add(events.size());
            for (final XdmNode event : events) {
                boolean drop = false;
                try {
                    if (!plan.dropWhen().isEmpty()) {
                        final XPathSelector test = executable(drops, namespace(event), dropTest(plan)).load();
                        test.setContextItem(event);
                        drop = test.effectiveBooleanValue();
                    }
                } catch (final SaxonApiException e) {
                    return new PlannedValues(plan, List.of(), perRecord, 0, "its drop_when can't be read ("
                                                                         + e.getMessage() + ")");
                }
                if (drop) {
                    dropped++;
                } else {
                    kept.add(event);
                }
            }
        }
        return new PlannedValues(plan, kept, perRecord, dropped, null);
    }

    /// @return The events indexed (the drop_when ones left out).
    public int indexed() {
        return events.size();
    }

    /// @return The events drop_when drops.
    public int dropped() {
        return dropped;
    }

    /// @return How many events each record held, in order.
    public List<Integer> eventsPerRecord() {
        return List.copyOf(eventsPerRecord);
    }

    /// @param fieldName A field of the index, as the plan names it (a field of objects' items as `users.login`).
    /// @return Each indexed event's values for it, in order (none for an event without it), or why they can't be
    /// read from the Events.
    public Written written(final String fieldName) {
        return read(fieldName, true);
    }

    /// As [#written], for whether each event has a value at all: a text or date field's values are read too, as
    /// the Events hold them.
    ///
    /// @param fieldName A field of the index, as the plan names it.
    /// @return Each indexed event's values for it, or why they can't be read from the Events.
    public Written present(final String fieldName) {
        return read(fieldName, false);
    }

    private Written read(final String fieldName, final boolean asSearched) {
        if (unreadable != null) {
            return Written.unknown("the plan's " + unreadable);
        }
        final Optional<PlannedField> field = plan.leafFields().stream()
                .filter(leaf -> leaf.name().equals(fieldName))
                .findFirst();
        if (field.isEmpty()) {
            return Written.unknown(fieldName + " isn't a field of the plan kept with the indexing XSLT (a shared "
                                   + "template's, or one the XSLT computes): its value can't be read from the "
                                   + "Events");
        }
        if (field.get().isConstant()) {
            // The value the user asked for, in every document: Elasticsearch fills it from the mapping, Lucene's
            // indexing XSLT writes it.
            final List<List<String>> every = events.stream()
                    .map(event -> List.of(field.get().constant()))
                    .toList();
            return new Written(constantSource(field.get()), every, null);
        }
        final String why = notTheEventsOwn(field.get(), asSearched);
        if (why != null) {
            return Written.unknown(why);
        }
        final List<String> leftOut = plan.standIns(Discovery.LEAVE_OUT);
        final List<String> asNull = plan.isElasticsearch()
                ? plan.standIns(Discovery.NULL)
                : List.of();
        final List<List<String>> out = new ArrayList<>();
        for (final XdmNode event : events) {
            final List<String> values = new ArrayList<>();
            try {
                final XPathSelector selector = executable(compiled, namespace(event), field.get().source()).load();
                selector.setContextItem(event);
                final XdmValue found = selector.evaluate();
                for (final XdmItem item : found) {
                    final String value = item.getStringValue();
                    if (!leftOut.contains(normalised(value))) {
                        values.add(value);
                    }
                }
            } catch (final SaxonApiException e) {
                return Written.unknown("its source " + field.get().source() + " can't be read (" + e.getMessage()
                                       + ")");
            }
            out.add(asWritten(field.get(), values, asNull));
        }
        return new Written(field.get().source(), out, null);
    }

    /// @param field A constant field.
    /// @return Where its values come from, as a finding says it.
    public static String constantSource(final PlannedField field) {
        return "the constant '" + field.constant() + "', in every document";
    }

    // The values the XSLT writes from those the source selects.
    private static List<String> asWritten(final PlannedField field,
                                          final List<String> values,
                                          final List<String> asNull) {
        if (values.isEmpty()) {
            return List.of();
        }
        final List<String> transformed = values.stream()
                .map(value -> "outcome".equals(field.transform())
                        ? ("false".equals(value)
                        ? "failure"
                        : "success")
                        : value)
                .toList();
        if (!field.multiple()) {
            // A stand-in the user chose to write as null: the field is null, holding no value.
            if (asNull.contains(normalised(values.getFirst()))) {
                return List.of();
            }
            return List.of(String.join(" ", transformed));
        }
        return FieldPlans.branches(field.source()).size() > 1
                ? List.copyOf(new LinkedHashSet<>(transformed))
                : transformed;
    }

    // Why a field's values aren't the Events' own, or null when they are.
    private String notTheEventsOwn(final PlannedField field, final boolean asSearched) {
        if (field.source().startsWith("@")) {
            return field.name() + " is written by Stroom (" + field.source() + "), not read from the Events";
        }
        if (!asSearched) {
            return null;
        }
        if ("text".equals(field.type())) {
            return field.name() + " is analysed text: the index holds its words, not the value the Events hold";
        }
        if ("date".equals(field.type())) {
            return field.name() + " is a date the index converts: its value there isn't the Events' text";
        }
        final boolean distinct = plan.fields().stream()
                .anyMatch(parent -> parent.isObjects() && NullSafe.isNonBlankString(parent.distinctBy())
                                    && field.name().startsWith(parent.name() + "."));
        if (distinct) {
            return field.name() + " is an item of objects made distinct by a key, which leaves some elements out";
        }
        return null;
    }

    private static String dropTest(final FieldPlan plan) {
        return String.join(" or ", plan.dropWhen().stream().map(test -> "(" + test + ")").toList());
    }

    private static XPathExecutable executable(final Map<String, XPathExecutable> cache,
                                              final String namespace,
                                              final String expression) throws SaxonApiException {
        final String key = namespace + "\n" + expression;
        XPathExecutable executable = cache.get(key);
        if (executable == null) {
            executable = LockedSaxon.xpath(namespace).compile(expression);
            cache.put(key, executable);
        }
        return executable;
    }

    // A record's Event elements, in the namespace of its Events.
    private static List<XdmNode> events(final String record) {
        final XdmNode document;
        try {
            document = LockedSaxon.parse(record);
        } catch (final SaxonApiException e) {
            return List.of();
        }
        final List<XdmNode> events = new ArrayList<>();
        for (final XdmNode node : document.children()) {
            if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
                collect(node, events);
            }
        }
        return events;
    }

    private static void collect(final XdmNode element, final List<XdmNode> events) {
        if ("Event".equals(element.getNodeName().getLocalName())) {
            events.add(element);
            return;
        }
        for (final XdmNode child : element.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                collect(child, events);
            }
        }
    }

    // The namespace the event's paths are read in: its own (event-logging:3, or an environment's fork), as the
    // XSLT's xpath-default-namespace is.
    private static String namespace(final XdmNode event) {
        return NullSafe.string(event.getNodeName().getNamespaceURI());
    }

    private static String normalised(final String value) {
        return String.join(" ", NullSafe.string(value).strip().split("\\s+"));
    }


    // --------------------------------------------------------------------------------


    /// A field's values, event by event, as the XSLT writes them.
    ///
    /// @param source   The source path they are read from.
    /// @param perEvent Each indexed event's values (none for an event without it); empty when unknown.
    /// @param unknown  Why they can't be read from the Events, or null.
    public record Written(String source, List<List<String>> perEvent, String unknown) {

        static Written unknown(final String why) {
            return new Written(null, List.of(), why);
        }

        /// @return True if the values were read from the Events.
        public boolean known() {
            return unknown == null;
        }
    }
}
