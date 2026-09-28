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

package stroom.floormap.client.playback;

import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapEventColumns;
import stroom.floormap.shared.FloorMapEventRole;
import stroom.floormap.shared.FloorMapFieldMapping;

/**
 * The events query a new {@link FloorMapDoc} starts with, and the column names it aliases.
 *
 * <p>These belong together because they have to agree. The floor map finds each meaning by
 * matching {@link FloorMapDoc#getEventColumns()} against the result column names, exactly; a name
 * that matches nothing leaves the index at {@code -1} and the whole parse returns no entities. The
 * map then looks exactly as it does when animation is switched off, while the query itself still
 * returns rows perfectly well — so a disagreement between the query text and the mapping is both
 * easy to introduce and hard to read back from the symptom.</p>
 *
 * <p>{@link #defaultQuery()} therefore interpolates {@link FloorMapEventRole#getDefaultColumn()},
 * the same values {@link FloorMapEventColumns#defaults()} seeds the mapping with, rather than
 * repeating the aliases as literal text. Renaming a column changes the query and the mapping
 * together or not at all — which is what the {@code Event Type} defect broke, where the query said
 * {@code Event Type} and the parser looked for {@code type}.</p>
 */
public final class FloorMapEventsQuery {

    /**
     * Result column holding the entity identity — the key events are grouped by.
     *
     * <p>Defined by {@link FloorMapEventRole#ENTITY_ID}; this constant is the same string, kept
     * for the callers that name the column rather than the role.</p>
     */
    public static final String ENTITY_ID_COLUMN = FloorMapEventRole.ENTITY_ID.getDefaultColumn();

    /**
     * Result column holding literal {@code "x, y"} coordinates.
     *
     * <p>Fixed at ingest, so an entity placed this way does <b>not</b> follow a fact that is later
     * moved. See {@link #LOCATION_REF_COLUMN} for the form that does.</p>
     */
    public static final String LOCATION_COLUMN = FloorMapEventRole.LOCATION.getDefaultColumn();

    /**
     * Result column holding the key of the fact the event happened at.
     *
     * <p>Split from {@link #LOCATION_COLUMN} on 2026-09-07. One column used to carry both forms,
     * told apart by <em>shape</em> — two numbers meant a position, anything else a key. That made
     * a fact key which happened to look like two numbers, or to contain a comma, impossible to
     * express; it needed a rule about part counts to be documented, learned and preserved; and it
     * meant a malformed position was silently read as a reference to a fact that did not exist,
     * reported as a missing desk rather than as a bad coordinate.</p>
     */
    public static final String LOCATION_REF_COLUMN = FloorMapEventRole.LOCATION_REF.getDefaultColumn();

    /** Result column holding the entry's effective time; drives the timeline. */
    public static final String EFFECTIVE_TIME_COLUMN = "Effective Time";

    /**
     * Result column holding the entity's kind — person, vehicle, asset.
     *
     * <p>Aliased {@code Type} because that is the name {@code FloorMapQueryPresenter.parseRows}
     * matches, case-insensitively, to decide an entity's type. Type is the layer key: it drives
     * which icon and colour an entity gets, and whether the Layers panel can hide or dim it.</p>
     *
     * <p>It was aliased {@code Event Type} until 2026-09-04, which matched nothing, so every entity
     * fell through to the {@code id.contains("@")} fallback — anything with an email-shaped id
     * became a person and everything else an {@code object}. A vehicle could not be styled as one.
     * The two coincide for people, which is why it survived so long.</p>
     *
     * <p>The old name also claimed to be the event's own type rather than the entity's. That was
     * wrong on the data: {@code .type} holds the kind of thing in both stores — the facts schema
     * maps the same path to {@link FloorMapFieldMapping.Role#TYPE} for desks and areas — while what
     * happened is carried by {@link #STATUS_COLUMN} and {@link #MESSAGE_COLUMN}.</p>
     *
     * <p>Existing documents keep whatever alias their stored query text uses, so a map written
     * before this change still needs its query edited by hand — one word — or it stays on the
     * fallback. Matching by a fixed name at all is the weakness; see
     * {@code docs/task-floormap-events-column-mapping.md}.</p>
     */
    public static final String EVENT_TYPE_COLUMN = FloorMapEventRole.TYPE.getDefaultColumn();

    /** Result column holding the event status. */
    public static final String STATUS_COLUMN = "Status";

    /** Result column holding the event message. */
    public static final String MESSAGE_COLUMN = "Message";

    private FloorMapEventsQuery() {
        // Constants only.
    }

    /**
     * Builds the StroomQL a newly created floor map starts with.
     *
     * <p>Reads the Plan B temporal-state fields {@code Key}, {@code EffectiveTime} and
     * {@code Value}, pulling the individual event properties out of the JSON value with
     * {@code jq}. The store is referenced as {@code param('EventStore')}, which
     * {@code FloorMapQueryPresenter.buildQueryVariables} substitutes with the configured store's
     * name at query time.</p>
     *
     * @return the default events query; never null
     */
    public static String defaultQuery() {
        return "from param('EventStore')\n"
               + "select EffectiveTime as \"" + EFFECTIVE_TIME_COLUMN + "\",\n"
               + "  Key as \"" + ENTITY_ID_COLUMN + "\",\n"
               + "  jq(Value, '.location') as \"" + LOCATION_COLUMN + "\",\n"
               + "  jq(Value, '.locationRef') as \"" + LOCATION_REF_COLUMN + "\",\n"
               + "  jq(Value, '.type') as \"" + EVENT_TYPE_COLUMN + "\",\n"
               + "  jq(Value, '.status') as \"" + STATUS_COLUMN + "\",\n"
               + "  jq(Value, '.message') as \"" + MESSAGE_COLUMN + "\"";
    }
}
