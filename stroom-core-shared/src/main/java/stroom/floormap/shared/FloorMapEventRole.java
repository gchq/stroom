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

package stroom.floormap.shared;

/**
 * What a column of the events query <em>means</em>.
 *
 * <h3>Why this exists</h3>
 * <p>A floor map has to connect result column names to meanings, and the events side used to do it
 * three different ways: two free-string settings on the document matched exactly, and — for the
 * entity's kind — a <b>hardcoded literal</b>, {@code col.getName().equalsIgnoreCase("type")}. One
 * relationship, three mechanisms, and no guarantee any of them agreed with the query.</p>
 *
 * <p>That cost real defects rather than tidiness. Every new floor map was born unable to render
 * events until the init dialog was taught to write both settings; entity type was never read from
 * the data at all, because the query aliased the column {@code Event Type} while the parser looked
 * for {@code type}, so every entity fell through an {@code id.contains("@")} heuristic and a
 * vehicle could not be styled as one. Both were the same root cause.</p>
 *
 * <p>Facts have never had the problem: their query is generated from a schema of role-to-path
 * mappings, so text and meaning agree by construction. Events cannot copy that exactly — the
 * events query is deliberately editable, which is why {@code FloorMapEventsQueryOrder} exists at
 * all — so the query stays free text and this enum structures the <em>bridge</em> instead. One
 * concept, one place to validate.</p>
 *
 * <h3>The role set is deliberately only what is read</h3>
 * <p>The default query also selects {@code Status} and {@code Message}, and nothing consumes them.
 * They are not roles: a mapping entry for a value no code reads is a promise the feature does not
 * keep. When something reads them, they get roles then.</p>
 */
public enum FloorMapEventRole {

    /**
     * The entity the event is about — a person, a vehicle, an asset.
     *
     * <p>Events are reduced by this, so each distinct value becomes one thing drawn on the map.
     * Without it nothing reaches the canvas, however well the query runs.</p>
     */
    ENTITY_ID("Entity ID", true),

    /**
     * Literal coordinates, {@code "x, y"}.
     *
     * <p>Fixed at ingest: an entity placed this way stays where the event said, and does <b>not</b>
     * follow a fact that is later moved in the Editor.</p>
     */
    LOCATION("Location", false),

    /**
     * The key of the fact the event happened at — a desk, a gate, a camera.
     *
     * <p>Resolved against the facts loaded for the current timeline instant, so moving the fact
     * moves everyone recorded as being at it. The more useful of the two forms, and the reason
     * they are worth telling apart.</p>
     */
    LOCATION_REF("Location Ref", false),

    /**
     * The kind of thing the entity is, which selects its type style and its Layers-panel layer.
     *
     * <p>Optional, and falls back to an {@code id.contains("@")} heuristic when unmapped or
     * absent — email-shaped ids read as people, everything else as an object. Kept because data
     * carrying no type at all is a real case.</p>
     */
    TYPE("Type", false);

    private final String defaultColumn;
    private final boolean required;

    FloorMapEventRole(final String defaultColumn, final boolean required) {
        this.defaultColumn = defaultColumn;
        this.required = required;
    }

    /**
     * The column alias the generated default query gives this role.
     *
     * <p>Held here rather than beside the query text so the two cannot drift: the query builder
     * emits this after {@code as}, and the mapping is seeded with it. That pairing is what the
     * {@code Event Type} defect broke.</p>
     *
     * @return the default column alias
     */
    public String getDefaultColumn() {
        return defaultColumn;
    }

    /**
     * Whether a map can draw anything without this role mapped.
     *
     * @return {@code true} only for {@link #ENTITY_ID}
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Human-readable name for the Events Query tab's control and for messages.
     *
     * @return the label, e.g. {@code "Location Ref"}
     */
    public String getDisplayName() {
        return defaultColumn;
    }
}
