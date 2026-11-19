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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.catalogue.CatalogueValidation.Problem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// What the catalogue's check found ([CatalogueCheck]), as the contract's check JSON writes it: compact for the
/// tool's result ([#toJson()]), or in full for the listing the tool saves to the workspace ([#toFullJson()]).
///
/// @param counts       The records each event type recognises, and each of its ways, by TypeId in the catalogue's
///                     order.
/// @param overlaps     Records two event types recognise (refused), one a pair of event types: the first such record.
/// @param uncatalogued The records no event type recognises, by shape.
/// @param unparsed     The records whose held values, read by a condition, don't parse.
/// @param several      Event types whose records may be several event types.
/// @param problems     What is refused: the catalogue's own problems and those its records show.
/// @param order        The TypeIds in the catalogue's order (more specific first).
public record CatalogueCheckResult(Map<String, Count> counts,
                                   List<Overlap> overlaps,
                                   Uncatalogued uncatalogued,
                                   Unparsed unparsed,
                                   List<Several> several,
                                   List<Problem> problems,
                                   List<String> order) {

    /// The most overlaps the compact form lists.
    public static final int OVERLAPS_SHOWN = 10;
    /// The most shapes of uncatalogued records the compact form lists: the commonest, then the rarest.
    public static final int SHAPES_SHOWN = 10;
    /// Of [#SHAPES_SHOWN], how many are the rarest.
    public static final int RARE_SHAPES_SHOWN = 3;
    /// The most examples a shape has in the compact form.
    public static final int EXAMPLES_SHOWN = 2;
    /// The longest a record's text is in the compact form.
    public static final int EXCERPT = 300;
    /// The longest a record's text is in the full listing.
    public static final int FULL_EXCERPT = 2_000;
    /// The most reasons the compact form gives why held values don't parse.
    public static final int WHY_SHOWN = 5;
    /// The most values of a field the compact form lists for a type that may be several.
    public static final int VALUES_SHOWN = 10;

    /// Copies the parts.
    public CatalogueCheckResult {
        counts = Map.copyOf(Objects.requireNonNullElse(counts, Map.of()));
        overlaps = List.copyOf(Objects.requireNonNullElse(overlaps, List.of()));
        several = List.copyOf(Objects.requireNonNullElse(several, List.of()));
        problems = List.copyOf(Objects.requireNonNullElse(problems, List.of()));
        order = List.copyOf(Objects.requireNonNullElse(order, List.of()));
    }

    /// @return True if the catalogue can't be confirmed as it is: a problem, an overlap, or a type that may be
    /// several whose warning blocks it.
    public boolean refused() {
        return !problems.isEmpty() || !overlaps.isEmpty() || several.stream().anyMatch(Several::blocking);
    }

    /// @return The check as the tool's result gives it: counts and problems, the lists cut short.
    public Map<String, Object> toJson() {
        return json(false);
    }

    /// @return The check in full, for the listing saved to the workspace.
    public Map<String, Object> toFullJson() {
        return json(true);
    }

    private Map<String, Object> json(final boolean full) {
        final Map<String, Object> out = new LinkedHashMap<>();
        final Map<String, Object> countsOut = new LinkedHashMap<>();
        for (final String typeId : order) {
            final Count count = counts.get(typeId);
            if (count != null) {
                countsOut.put(typeId, count.toJson());
            }
        }
        counts.forEach((typeId, count) -> countsOut.putIfAbsent(typeId, count.toJson()));
        out.put("counts", countsOut);
        out.put("overlaps", (full
                ? overlaps
                : overlaps.subList(0, Math.min(OVERLAPS_SHOWN, overlaps.size())))
                .stream().map(overlap -> overlap.toJson(full)).toList());
        out.put("uncatalogued", uncatalogued.toJson(full));
        out.put("unparsed", unparsed.toJson(full));
        out.put("several", several.stream().map(one -> one.toJson(full)).toList());
        out.put("problems", problems.stream().map(Problem::toJson).toList());
        out.put("order", order);
        return out;
    }

    static String excerpt(final String text, final boolean full) {
        final int most = full
                ? FULL_EXCERPT
                : EXCERPT;
        return text == null || text.length() <= most
                ? text
                : text.substring(0, most) + "…";
    }


    // --------------------------------------------------------------------------------


    /// The records an event type recognises.
    ///
    /// @param records How many, by any of its ways.
    /// @param byWay   How many each way recognises, in its order (ways may overlap).
    public record Count(int records, List<Integer> byWay) {

        /// Copies the list.
        public Count {
            byWay = List.copyOf(byWay);
        }

        Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("records", records);
            out.put("by_way", byWay);
            return out;
        }
    }


    // --------------------------------------------------------------------------------


    /// A record two (or more) event types recognise: refused, with the fields whose values tell their other records
    /// apart, so one can be tightened.
    ///
    /// @param record        The first such record's index in the scope's records (from 0).
    /// @param text          Its text.
    /// @param typeIds       The two event types, in the catalogue's order.
    /// @param tellingFields The fields whose values tell the two types' other records apart, best first.
    /// @param records       How many records both recognise.
    public record Overlap(int record, String text, List<String> typeIds, List<TellingField> tellingFields,
                          int records) {

        /// Copies the lists.
        public Overlap {
            typeIds = List.copyOf(typeIds);
            tellingFields = List.copyOf(tellingFields);
        }

        Map<String, Object> toJson(final boolean full) {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("record", record);
            out.put("text", excerpt(text, full));
            out.put("type_ids", typeIds);
            out.put("telling_fields", tellingFields.stream().map(TellingField::toJson).toList());
            if (full) {
                out.put("records", records);
            }
            return out;
        }
    }


    // --------------------------------------------------------------------------------


    /// A field whose values tell two event types' records apart.
    ///
    /// @param field  The field, as the records spell it.
    /// @param values A few of its values in each type's other records, by TypeId (none where they don't have it).
    public record TellingField(String field, Map<String, List<String>> values) {

        /// Copies the values, in order.
        public TellingField {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("field", field);
            out.putAll(values);
            return out;
        }
    }


    // --------------------------------------------------------------------------------


    /// The records no event type recognises: the catch-all translates them.
    ///
    /// @param records How many.
    /// @param shapes  Their shapes, commonest first.
    public record Uncatalogued(int records, List<Shape> shapes) {

        /// None.
        public static final Uncatalogued NONE = new Uncatalogued(0, List.of());

        /// Copies the list.
        public Uncatalogued {
            shapes = List.copyOf(shapes);
        }

        Map<String, Object> toJson(final boolean full) {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("records", records);
            out.put("shapes", shown(full).stream().map(shape -> shape.toJson(full)).toList());
            return out;
        }

        // The shapes shown: every one in full; else the commonest, then the rarest (seen once or twice, always
        // among them).
        private List<Shape> shown(final boolean full) {
            if (full || shapes.size() <= SHAPES_SHOWN) {
                return shapes;
            }
            final List<Shape> out = new ArrayList<>(shapes.subList(0, SHAPES_SHOWN - RARE_SHAPES_SHOWN));
            out.addAll(shapes.subList(shapes.size() - RARE_SHAPES_SHOWN, shapes.size()));
            return out;
        }
    }


    // --------------------------------------------------------------------------------


    /// Records of one shape.
    ///
    /// @param signature Their shape's signature (as the survey's shapes sign records).
    /// @param records   How many.
    /// @param examples  Their texts, the records that differ most first.
    public record Shape(String signature, int records, List<String> examples) {

        /// Copies the list.
        public Shape {
            examples = List.copyOf(examples);
        }

        Map<String, Object> toJson(final boolean full) {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("signature", signature);
            out.put("records", records);
            out.put("examples", (full
                    ? examples
                    : examples.subList(0, Math.min(EXAMPLES_SHOWN, examples.size())))
                    .stream().map(text -> excerpt(text, full)).toList());
            return out;
        }
    }


    // --------------------------------------------------------------------------------


    /// The records whose held values, read by a condition, don't parse: no condition inside them holds (but a
    /// `not`), as in processing.
    ///
    /// @param records How many.
    /// @param why     Why, each reason once.
    public record Unparsed(int records, List<String> why) {

        /// None.
        public static final Unparsed NONE = new Unparsed(0, List.of());

        /// Copies the list.
        public Unparsed {
            why = List.copyOf(why);
        }

        Map<String, Object> toJson(final boolean full) {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("records", records);
            out.put("why", full
                    ? why
                    : why.subList(0, Math.min(WHY_SHOWN, why.size())));
            return out;
        }
    }


    // --------------------------------------------------------------------------------


    /// An event type whose records may be several event types: they split by a field of their own, or come in
    /// clearly different shapes.
    ///
    /// @param typeId   The event type.
    /// @param byField  The field they split by, or null.
    /// @param shapes   Their clearly different shapes, or none.
    /// @param blocking True when it can't be confirmed until it is split or said to be one (`one_type_because`): the
    ///                 draft's seed.
    public record Several(String typeId, ByField byField, List<ShapeCount> shapes, boolean blocking) {

        /// Copies the list.
        public Several {
            shapes = List.copyOf(shapes);
        }

        Map<String, Object> toJson(final boolean full) {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("type_id", typeId);
            out.put("by_field", byField == null
                    ? null
                    : byField.toJson(full));
            out.put("shapes", shapes.stream().map(ShapeCount::toJson).toList());
            out.put("blocking", blocking);
            return out;
        }
    }


    // --------------------------------------------------------------------------------


    /// A field an event type's records split by, and how many records have each of its values.
    ///
    /// @param field  The field.
    /// @param values Its values and their records, commonest first.
    public record ByField(String field, Map<String, Integer> values) {

        /// Copies the values, in order.
        public ByField {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        Map<String, Object> toJson(final boolean full) {
            final Map<String, Object> shown = new LinkedHashMap<>();
            values.entrySet().stream()
                    .limit(full
                            ? Long.MAX_VALUE
                            : VALUES_SHOWN)
                    .forEach(entry -> shown.put(entry.getKey(), entry.getValue()));
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("field", field);
            out.put("values", shown);
            return out;
        }
    }


    // --------------------------------------------------------------------------------


    /// One of an event type's shapes, and how many of its records have it.
    ///
    /// @param signature The shape's signature.
    /// @param records   How many.
    public record ShapeCount(String signature, int records) {

        Map<String, Object> toJson() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("signature", signature);
            out.put("records", records);
            return out;
        }
    }
}
