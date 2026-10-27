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

package stroom.floormap.client.presenter;

import stroom.floormap.client.model.FloorMapLocationResolver;
import stroom.floormap.client.model.FloorMapObject;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapEventColumns;
import stroom.floormap.shared.FloorMapEventRole;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.query.api.Column;
import stroom.query.api.Row;
import stroom.query.api.TableResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// The floor map's events query as data: the columns its roles may be mapped to, the rows it
/// returns as map objects (the latest per entity), and the variables it runs with. Held apart from
/// [stroom.floormap.client.component.FloorMapQueryController] so the Map tab and tests can use it.
public final class FloorMapEventQuery {

    private FloorMapEventQuery() {
        // Static helpers only
    }

    /// The names of the result columns a role may be mapped to.
    ///
    /// Special columns are left out. An ungrouped query's result always carries hidden bookkeeping
    /// columns such as `__stream_id__` and `__event_id__`, which the query never selects and the
    /// results table never shows, so offering them would let a role be pointed at a column the
    /// query does not select.
    ///
    /// @param columns the result columns; may be `null`
    /// @return the selectable column names, in result order; never `null`
    public static List<String> selectableColumnNames(final List<Column> columns) {
        final List<String> names = new ArrayList<>();
        if (columns == null) {
            return names;
        }
        for (final Column column : columns) {
            if (column != null && !Boolean.TRUE.equals(column.isSpecial())) {
                names.add(column.getName());
            }
        }
        return names;
    }

    /// The distinct column names a mapping refers to, in [FloorMapEventRole] order.
    ///
    /// Used to seed the dropdowns before any result exists. These are only what the document
    /// currently maps, not columns known to exist: the defaults match the default query's aliases,
    /// but nothing checks any name against an edited query until it is run, when
    /// [#updateColumnSelections()] unmaps any role whose column the result lacks. Offering only
    /// these means seeding can never introduce a name the mapping does not already hold.
    ///
    /// @param eventColumns the mapping to read; may be `null`
    /// @return the mapped column names, without duplicates or unmapped roles; never `null`
    public static List<String> mappedColumnNames(final FloorMapEventColumns eventColumns) {
        final List<String> names = new ArrayList<>();
        if (eventColumns == null) {
            return names;
        }
        for (final FloorMapEventRole role : FloorMapEventRole.values()) {
            // getColumn already returns null for a blank column.
            final String column = eventColumns.getColumn(role);
            if (column != null && !names.contains(column)) {
                names.add(column);
            }
        }
        return names;
    }

    /// Reduces a time window to the most recent row per entity.
    ///
    /// The Map tab queries a trailing window rather than an instant, because an instant is
    /// unsatisfiable against a store that applies the time range literally — see
    /// `FloorMapMapPresenter.runQueryAtSelectedTime`. A window can return several events for
    /// one entity, and the canvas wants exactly one position each, so the extras are dropped here.
    /// A store that already deduplicates server-side returns one row per key anyway, and this then
    /// costs a pass over the rows and changes nothing.
    ///
    /// **How "most recent" is decided, and where that is imperfect.** The time
    /// column arrives already rendered as text, formatted to the viewing user's date-time
    /// preference, so there is no timestamp to compare — only its presentation. Two forms are
    /// handled properly: epoch milliseconds, compared numerically, and the ISO-8601 form Stroom
    /// emits when no pattern preference is set, which sorts correctly as text. A user pattern that
    /// is *not* lexicographically ordered — `dd/MM/yyyy` being the obvious one — makes
    /// the text comparison pick the wrong row of the window. The error is bounded by the window
    /// (an entity can appear at a position up to that stale, not at a wrong one) and is strictly
    /// better than the zero rows this replaced, but the real fix is for the query to carry a raw
    /// numeric time alongside the formatted one.
    ///
    /// With no usable time column the last row for each entity wins, which is at least
    /// deterministic for a given result.
    ///
    /// @param columns      the result columns; may be `null`
    /// @param rows         the rows to reduce; may be `null`
    /// @param entityColumn the column naming the entity; `null` leaves rows untouched
    /// @param timeColumn   the column holding the effective time; absent or unmatched falls back to
    ///         last-row-wins
    /// @return one row per entity, in first-appearance order; never `null`
    public static List<Row> latestPerEntity(final List<Column> columns,
                                     final List<Row> rows,
                                     final String entityColumn,
                                     final String timeColumn) {
        if (rows == null || columns == null || entityColumn == null) {
            return rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        }

        int entityColIndex = -1;
        int timeColIndex = -1;
        for (int i = 0; i < columns.size(); i++) {
            final String name = columns.get(i).getName();
            if (entityColumn.equals(name)) {
                entityColIndex = i;
            } else if (timeColumn != null && timeColumn.equals(name)) {
                timeColIndex = i;
            }
        }
        if (entityColIndex == -1) {
            // Nothing to group by; parseRows will reject these rows anyway.
            return new ArrayList<>(rows);
        }

        // LinkedHashMap so the surviving rows keep the order the entities first appeared, which
        // keeps the canvas's draw order stable between frames rather than reshuffling per poll.
        final Map<String, Row> latest = new LinkedHashMap<>();
        for (final Row row : rows) {
            final String entityId = valueAt(row, entityColIndex);
            if (entityId == null) {
                continue;
            }
            final Row incumbent = latest.get(entityId);
            if (incumbent == null || isAfter(row, incumbent, timeColIndex)) {
                latest.put(entityId, row);
            }
        }
        return new ArrayList<>(latest.values());
    }

    /// Whether `candidate` is the later of the two rows at `timeColIndex`.
    ///
    /// With no time column every row is treated as later than the one before it, which makes
    /// the last row for an entity win.
    private static boolean isAfter(final Row candidate, final Row incumbent, final int timeColIndex) {
        if (timeColIndex == -1) {
            return true;
        }
        final String candidateTime = valueAt(candidate, timeColIndex);
        final String incumbentTime = valueAt(incumbent, timeColIndex);
        if (candidateTime == null) {
            return false;
        }
        if (incumbentTime == null) {
            return true;
        }
        final Long candidateMs = asEpochMs(candidateTime);
        final Long incumbentMs = asEpochMs(incumbentTime);
        if (candidateMs != null && incumbentMs != null) {
            return candidateMs >= incumbentMs;
        }
        return candidateTime.compareTo(incumbentTime) >= 0;
    }

    /// The value at `index`, or `null` if the row is short or holds nothing there.
    private static String valueAt(final Row row, final int index) {
        final List<String> values = row == null ? null : row.getValues();
        return values == null || values.size() <= index ? null : values.get(index);
    }

    /// Parses epoch milliseconds, or `null` when the text is not a bare number.
    private static Long asEpochMs(final String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /// Parses all rows of the supplied [TableResult] into
    /// [FloorMapObject] instances using the given entity and location
    /// column mappings.
    ///
    /// Static and package-visible because two callers run the same events
    /// query: this editor tab, and [FloorMapMapPresenter], which owns the
    /// timeline-driven playback query feeding the animated entity overlay.
    ///
    /// An entity is positioned outright when its [FloorMapEventRole#LOCATION] column holds
    /// coordinates; one carrying a [FloorMapEventRole#LOCATION_REF] instead gets a
    /// [FloorMapObject#getLocationRef()] and must be run through
    /// [FloorMapLocationResolver#resolve] against the current facts before it is drawn.
    ///
    /// @param tableResult  the query result to parse
    /// @param eventColumns which result column carries each role
    /// @param warnings     receives a message for contradictory or malformed location data, at most
    ///         one per result; may be `null`
    /// @return a list of map objects; never `null`
    public static List<FloorMapObject> parseRows(final TableResult tableResult,
                                          final FloorMapEventColumns eventColumns,
                                          final Consumer<String> warnings) {
        if (tableResult == null) {
            return new ArrayList<>();
        }
        return parseRows(tableResult.getColumns(), tableResult.getRows(), eventColumns, warnings);
    }

    /// As [#parseRows(TableResult, FloorMapEventColumns, Consumer)], over a caller-supplied
    /// row list.
    ///
    /// Split out so a caller can filter the rows first — [#latestPerEntity] reduces a
    /// time window to one row per entity — without rebuilding a [TableResult] whose
    /// `totalResults` would then disagree with its contents.
    ///
    /// @param columns      the result columns; may be `null`
    /// @param rows         the rows to parse; may be `null`
    /// @param eventColumns which result column carries each role
    /// @param warnings     receives a message for contradictory or malformed location data, at most
    ///         one per result; may be `null`
    /// @return a list of map objects; never `null`
    public static List<FloorMapObject> parseRows(final List<Column> columns,
                                          final List<Row> rows,
                                          final FloorMapEventColumns eventColumns,
                                          final Consumer<String> warnings) {
        final List<FloorMapObject> list = new ArrayList<>();

        if (rows == null || columns == null || eventColumns == null) {
            return list;
        }

        final int entityIdx = columnIndex(columns, eventColumns.getColumn(FloorMapEventRole.ENTITY_ID));
        final int locationIdx = columnIndex(columns, eventColumns.getColumn(FloorMapEventRole.LOCATION));
        final int refIdx = columnIndex(columns, eventColumns.getColumn(FloorMapEventRole.LOCATION_REF));
        final int typeIdx = columnIndex(columns, eventColumns.getColumn(FloorMapEventRole.TYPE));

        // No identity, nothing to draw. Either location role on its own is enough - a store whose
        // events only ever carry fact keys has no Location column at all, and one whose events are
        // all pre-resolved has no Location Ref.
        if (entityIdx == -1 || (locationIdx == -1 && refIdx == -1)) {
            return list;
        }

        boolean bothWarned = false;
        for (final Row row : rows) {
            final List<String> values = row.getValues();
            if (values == null || values.size() <= entityIdx) {
                continue;
            }
            final String entityId = values.get(entityIdx);
            if (entityId == null) {
                continue;
            }

            String type = "object";
            if (typeIdx != -1 && values.size() > typeIdx && values.get(typeIdx) != null) {
                type = values.get(typeIdx);
            } else if (entityId.contains("@")) {
                // Kept as a last resort: data carrying no type at all is a real case, and dropping
                // this would change behaviour for anyone relying on it.
                type = FloorMapJsonKeys.PERSON;
            }

            final String locationStr = cell(values, locationIdx);
            final String refStr = FloorMapLocationResolver.parseReference(cell(values, refIdx));

            // Location wins, because it needs no lookup. Both set is contradictory data rather than
            // a preference, so it is warned about once per result - warning per row would be three
            // messages a second during playback.
            if (locationStr != null && refStr != null && !bothWarned && warnings != null) {
                bothWarned = true;
                warnings.accept("Floor map events: '" + entityId + "' carries both a "
                                + FloorMapEventRole.LOCATION.getDisplayName() + " and a "
                                + FloorMapEventRole.LOCATION_REF.getDisplayName()
                                + ". Only one can be true, and the coordinates are being used"
                                + " because they need no lookup. Fix the data, or unmap one of the"
                                + " two roles on the Events Query tab.");
            }

            final double[] coords = FloorMapLocationResolver.parseCoordinates(locationStr);
            if (coords != null) {
                list.add(new FloorMapObject(entityId, type, coords[0], coords[1]));
            } else if (refStr != null) {
                final FloorMapObject object = new FloorMapObject(entityId, type, 0, 0);
                object.setLocationRef(refStr);
                list.add(object);
            } else if (locationStr != null && warnings != null && !bothWarned) {
                // A Location that is not two numbers is malformed, full stop. Under the old
                // single-column scheme it silently became a reference to a fact named after a
                // coordinate string, and the map reported a missing desk.
                bothWarned = true;
                warnings.accept("Floor map events: '" + entityId + "' has "
                                + FloorMapEventRole.LOCATION.getDisplayName() + " '" + locationStr
                                + "', which is not two comma-separated numbers. Coordinates are"
                                + "'x, y'; a fact key belongs in "
                                + FloorMapEventRole.LOCATION_REF.getDisplayName() + ".");
            }
        }

        return list;
    }

    /// The value at `index`, or `null` when absent, blank, or the column is unmapped.
    private static String cell(final List<String> values, final int index) {
        if (index < 0 || index >= values.size()) {
            return null;
        }
        final String value = values.get(index);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /// Exact match, as the mapping promises: an alias that matches nothing is a fault, not a hint.
    private static int columnIndex(final List<Column> columns, final String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < columns.size(); i++) {
            final Column column = columns.get(i);
            if (column != null && name.equals(column.getName())) {
                return i;
            }
        }
        return -1;
    }

    /// Builds the query parameter map from a [FloorMapDoc]'s store references.
    /// Parameters `FactStore` and `EventStore` are mapped to the
    /// store names so that `param('FactStore')` and `param('EventStore')`
    /// references in queries resolve correctly.
    ///
    /// @param doc the floor map document; never null
    /// @return a parameter map, possibly empty but never null
    public static Map<String, String> buildQueryVariables(final FloorMapDoc doc) {
        final Map<String, String> vars = new HashMap<>();
        if (doc.getFactsStoreRef() != null && doc.getFactsStoreRef().getName() != null) {
            vars.put("FactStore", doc.getFactsStoreRef().getName());
        }
        if (doc.getEventsStoreRef() != null && doc.getEventsStoreRef().getName() != null) {
            vars.put("EventStore", doc.getEventsStoreRef().getName());
        }
        return vars;
    }
}
