/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.floormap.client.playback.FloorMapEventsQuery;
import stroom.query.api.Column;

import java.util.Arrays;
import java.util.List;

/**
 * The query and columns a {@link FloorMapEventStoreDataPresenter} starts with.
 *
 * <p>Held apart from the presenter so they can be tested. {@code AbstractQueryDataPresenter} calls
 * {@code GWT.create()} in a static initialiser, so touching any static member of a subclass - even
 * a plain string constant - initialises that superclass and throws outside a browser. The same
 * separation {@code HistogramDataModel} uses, for the same reason: the part worth testing must not
 * sit behind something that cannot run.</p>
 */
final class FloorMapEventStoreDataDefaults {

    // The grid shown before the query is first run - column headers over an empty table, and
    // nothing more. It is NOT a set of settings the server honours: QueryTablePreferencesUtil
    // matches preferences to query columns by id, and the ids StroomQL generates are derived from
    // the column alias (SearchRequestFactory.createColumnId gives "effective_time-1", "value-1"
    // and so on), so plain names like these never match and nothing here is applied. Expressions
    // and names are never copied across in any case - the query text owns both. Once a search has
    // run, the grid rebuilds from the columns the query actually produced, and anything the user
    // then changes does persist, because it is built from those real columns.
    private static final Column TIME_COL = Column.builder()
            .id("EffectiveTime")
            .name(FloorMapEventsQuery.EFFECTIVE_TIME_COLUMN)
            .expression("EffectiveTime")
            .build();
    private static final Column KEY_COL = Column.builder().id("Key").name("Key").expression("Key").build();
    private static final Column VALUE_COL = Column.builder().id("Value").name("Value").expression("Value").build();
    private static final Column LOCATION_COL = Column.builder()
            .id("Location")
            .name(FloorMapEventsQuery.LOCATION_COLUMN)
            .expression("jq(Value, '.location')")
            .build();
    private static final Column LOCATION_REF_COL = Column.builder()
            .id("LocationRef")
            .name(FloorMapEventsQuery.LOCATION_REF_COLUMN)
            .expression("jq(Value, '.locationRef')")
            .build();
    private static final Column TYPE_COL = Column.builder()
            .id("Type")
            .name(FloorMapEventsQuery.EVENT_TYPE_COLUMN)
            .expression("jq(Value, '.type')")
            .build();
    private static final Column STATUS_COL = Column.builder()
            .id("Status")
            .name(FloorMapEventsQuery.STATUS_COLUMN)
            .expression("jq(Value, '.status')")
            .build();
    private static final Column MESSAGE_COL = Column.builder()
            .id("Message")
            .name(FloorMapEventsQuery.MESSAGE_COLUMN)
            .expression("jq(Value, '.message')")
            .build();

    private FloorMapEventStoreDataDefaults() {
        // Constants only.
    }

    /**
     * The Plan B temporal state default, plus the event properties the floor map reads.
     *
     * <p><b>One line, necessarily.</b> The query editor is a {@code g:TextBox} - a single-line
     * {@code <input>} - so {@code setText} discards newlines rather than wrapping. A multi-line
     * default does not come back multi-line; it comes back with the line breaks removed and the
     * tokens either side fused, which is how {@code limit 100} became {@code limit 100select}.
     * {@code FloorMapEventsQuery.defaultQuery()} may span lines because the Events Query tab has a
     * real editor. This one may not.</p>
     *
     * <p>{@code Key} keeps its Plan B name rather than the floor map's {@code Entity ID} alias: this
     * tab reads the store, and the store's column is {@code Key}. The five aliases come from
     * {@link FloorMapEventsQuery} rather than literal text so that renaming one changes the events
     * query and this tab together - the same reason that class exists at all.</p>
     *
     * <p>The store is named directly rather than through {@code param('EventStore')}: the Data tab
     * runs against the document it is part of, and {@code AbstractQueryDataPresenter.onRun} sends
     * no params. That also means no {@code readMode} or {@code asAt}, so this takes the ordinary
     * range read rather than the map's point-in-time snapshot.</p>
     *
     * @param storeName the store document's name
     * @return the default query text, on a single line; never null
     */
    static String query(final String storeName) {
        return "from \"" + storeName + "\" limit 100"
               + " select EffectiveTime as \"" + FloorMapEventsQuery.EFFECTIVE_TIME_COLUMN + "\""
               + ", Key"
               + ", Value"
               + ", jq(Value, '.location') as \"" + FloorMapEventsQuery.LOCATION_COLUMN + "\""
               + ", jq(Value, '.locationRef') as \"" + FloorMapEventsQuery.LOCATION_REF_COLUMN + "\""
               + ", jq(Value, '.type') as \"" + FloorMapEventsQuery.EVENT_TYPE_COLUMN + "\""
               + ", jq(Value, '.status') as \"" + FloorMapEventsQuery.STATUS_COLUMN + "\""
               + ", jq(Value, '.message') as \"" + FloorMapEventsQuery.MESSAGE_COLUMN + "\"";
    }

    /**
     * The eight columns, in the order the grid shows them.
     *
     * @return the default column list; never null
     */
    static List<Column> columns() {
        return Arrays.asList(
                TIME_COL, KEY_COL, VALUE_COL, LOCATION_COL, LOCATION_REF_COL, TYPE_COL, STATUS_COL, MESSAGE_COL);
    }
}
