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

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.query.api.TimeRange;
import stroom.query.shared.QueryTablePreferences;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable document describing a floor map visualisation.
 *
 * <p>A {@code FloorMapDoc} ties together a <em>facts store</em> (a
 * {@code SqlTemporalStoreDoc}, which the Editor tab writes to) and an
 * <em>events store</em> (a {@code FloorMapEventStoreDoc}, which is read-only here), along
 * with the queries, display preferences, and value-schema metadata the floor
 * map UI needs to parse and render temporal entries on a 2-D canvas.</p>
 *
 * <h3>UI Tabs</h3>
 * <p>When a user opens a Floor Map document in the Stroom UI, the
 * {@code FloorMapPresenter}
 * builds several tabs, each backed by a sub-presenter:</p>
 * <table>
 *   <caption>Floor Map tabs and their presenters</caption>
 *   <tr><th>Tab</th><th>Presenter</th><th>Purpose</th></tr>
 *   <tr><td>Settings</td>
 *       <td>{@code FloorMapSettingsPresenter}</td>
 *       <td>Configures store references, value format, and value schema</td></tr>
 *   <tr><td>Events Query</td>
 *       <td>{@code FloorMapQueryPresenter}</td>
 *       <td>Edits the StroomQL query for the events store</td></tr>
 *   <tr><td>Map</td>
 *       <td>{@code FloorMapMapPresenter}</td>
 *       <td>Read-only (or light-edit) canvas rendering of facts
 *       at a point in time</td></tr>
 *   <tr><td>Editor</td>
 *       <td>{@code FloorMapEditorPresenter}</td>
 *       <td>Full authoring environment with staged (pending)
 *       saves</td></tr>
 * </table>
 *
 * <h3>Two-Store Architecture</h3>
 * <ul>
 *   <li><strong>Facts store</strong> ({@link #factsStoreRef}) — a SQL Temporal Store
 *       containing the spatial data (objects, positions, background image, matrices).</li>
 *   <li><strong>Events store</strong> ({@link #eventsStoreRef}) — a FloorMap Event Store of
 *       state type {@code TEMPORAL_STATE}, containing status / event records keyed by
 *       entity ID. Queried via {@link #eventsQuery}; never written to by the floor map.</li>
 * </ul>
 *
 * <h3>Value Schema</h3>
 * <p>Each temporal entry's {@code Value} column is a serialised string whose
 * format is determined by {@link #valueFormat}; both {@link ValueFormat#JSON} and
 * {@link ValueFormat#XML} are supported. The {@link #valueSchema} list
 * tells the UI how to read individual fields from that value string: each
 * {@link FloorMapFieldMapping} maps a
 * {@link FloorMapFieldMapping.Role Role} (e.g. {@code TYPE}, {@code POSITION})
 * to a path within the serialised structure (e.g. {@code ".type"},
 * {@code ".coords"}).</p>
 *
 * <h3>Null Conventions</h3>
 * <p>This class is JSON-serialised with
 * {@link JsonInclude.Include#NON_NULL NON_NULL}, so {@code null} fields
 * are omitted from the stored JSON. The following getters apply
 * <em>default-on-null</em> logic to guarantee a non-null return value:</p>
 * <ul>
 *   <li>{@link #getValueFormat()} → defaults to {@link ValueFormat#JSON}</li>
 *   <li>{@link #getMatrix()} → never null; the constructor defaults a
 *       {@code null} input to {@link FloorMapTransformationMatrix#identity()}</li>
 *   <li>{@link #getValueSchema()} → defaults to
 *       {@link FloorMapFieldMapping#initialValueSchema()} when the stored schema is
 *       {@code null} <em>or empty</em>, so legacy documents still parse</li>
 * </ul>
 * <p>All other getters return exactly what was passed to the constructor and
 * <strong>may return {@code null}</strong>; see each getter's Javadoc for details.</p>
 *
 * <h3>Immutability and Builder</h3>
 * <p>All fields are {@code final}. Mutation is done via the copy-builder
 * pattern: call {@link #copy()} to obtain a pre-populated {@link Builder},
 * modify the desired fields, and call {@link Builder#build()}. A fresh
 * builder can be obtained via {@link #builder()}.</p>
 *
 * @see FloorMapFieldMapping
 * @see ValueFormat
 * @see FloorMapTransformationMatrix
 */
@Description(
    """
    Defines a floor map document which can be used to visualize data over time.
    """)
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class FloorMapDoc extends AbstractDoc {

    public static final String TYPE = "FloorMap";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.FLOOR_MAP_DOCUMENT_TYPE;

    /**
     * Free-text description of this floor map document.
     * Markdown, shown and edited on the Documentation tab.
     * May be {@code null} if not set.
     */
    @JsonProperty
    private final String description;

    /**
     * HTML template used for rendering object tooltips / popups on the canvas.
     * May be {@code null} if not configured.
     */
    @JsonProperty
    private final String template;

    /**
     * Global transformation matrix applied to the canvas.
     * Never stored as {@code null} — the constructor replaces a {@code null}
     * input with {@link FloorMapTransformationMatrix#identity()}.
     */
    @JsonProperty
    private final FloorMapTransformationMatrix matrix;

    /**
     * Which result column of the events query carries each {@link FloorMapEventRole}.
     *
     * <p>Replaced the {@code entityIdColumn} and {@code locationIdColumn} string settings, and the
     * hardcoded {@code "type"} literal the parser used for the entity's kind, on 2026-09-07. Three
     * mechanisms for one relationship became one; see {@link FloorMapEventRole} for what that
     * asymmetry cost.</p>
     *
     * <p>Never {@code null}: the constructor substitutes {@link FloorMapEventColumns#defaults()}
     * for a document saved before the change, so one using the default aliases — which every
     * document created by the init dialog does — keeps working untouched, and is written back
     * explicitly on its next save. That is not a fallback chain but the observation that the
     * default mapping and the default query are generated from the same {@link FloorMapEventRole}
     * constants, so a document that never changed its aliases is already described by the
     * defaults. One with hand-edited aliases needs the mapping setting, and the Map tab names the
     * mapping role by role rather than drawing nothing in silence.</p>
     */
    @JsonProperty
    private final FloorMapEventColumns eventColumns;

    /**
     * Reference to the SQL Temporal Store document used as the facts store.
     * The facts store contains spatial data: object positions, background
     * images, and transformation matrices.
     * May be {@code null} if not yet configured.
     *
     * <p><strong>Back-compatibility:</strong> previously serialised as
     * {@code "temporalStoreRef"}; the {@link JsonAlias} on the constructor
     * parameter handles migration transparently.</p>
     */
    @JsonProperty("factsStoreRef")
    private final DocRef factsStoreRef;

    /**
     * Reference to the Plan B document used as the events store. The events store
     * contains status / event records keyed by entity ID, and is only ever read.
     *
     * <p>Only the referenced document's <em>name</em> is used at query time — it is
     * substituted into the {@code param('EventStore')} placeholder of
     * {@link #eventsQuery} — so nothing here is coupled to a particular store
     * implementation. In practice the store must expose {@code Key},
     * {@code EffectiveTime} and {@code Value}, which for Plan B means a state type of
     * {@code TEMPORAL_STATE}; the pickers restrict the choice to Plan B documents but
     * cannot filter on state type, so a mismatch surfaces as a query-time error.</p>
     *
     * <p>For Plan B that name is load-bearing twice over: the {@code <map>} element of an
     * ingest XSLT must <em>equal the store's own name</em> too, so renaming this document
     * breaks ingest lookups as well as the events query.</p>
     *
     * May be {@code null} if not yet configured.
     */
    @JsonProperty
    private final DocRef eventsStoreRef;

    /**
     * StroomQL query string for the events store.
     * Executed by
     * {@code FloorMapMapPresenter} to
     * populate the events overlay on the canvas.
     * May be {@code null} if not configured.
     */
    @JsonProperty
    private final String eventsQuery;

    /**
     * StroomQL behind the timeline's density histogram, and behind "Show All".
     *
     * <p>Editable like {@link #eventsQuery}, and defaulted on creation from
     * {@code FloorMapQueryBuilder}. Both read the events store directly rather than wrapping the
     * events query, so a {@code where} clause here narrows the bars or the extent independently of
     * what the map draws.</p>
     */
    @JsonProperty
    private final String histogramQuery;

    @JsonProperty
    private final String extentQuery;

    /**
     * Time range filter applied to the events query.
     * May be {@code null} if no time range restriction is configured.
     */
    @JsonProperty
    private final TimeRange eventsQueryTimeRange;

    /**
     * Table display preferences (column widths, sort order, etc.) for the
     * events query result grid.
     * May be {@code null} if not configured.
     */
    @JsonProperty
    private final QueryTablePreferences eventsQueryTablePreferences;

    /**
     * The serialisation format of the temporal entry's {@code Value} column.
     * May be {@code null} in the stored JSON; {@link #getValueFormat()}
     * defaults to {@link ValueFormat#JSON} in that case.
     *
     * <p>Both {@link ValueFormat#JSON} and {@link ValueFormat#XML} are supported;
     * {@code ValueAccessorFactory.forFormat} selects the reader/writer.</p>
     */
    @JsonProperty
    private final ValueFormat valueFormat;

    /**
     * Ordered list of field mappings that describe the structure of a
     * temporal entry's {@code Value} column. Each entry maps a
     * {@link FloorMapFieldMapping.Role Role} to a path within the
     * serialised value (e.g. {@code ".type"}, {@code ".coords"}).
     *
     * <p>May be {@code null} for documents created before the value
     * schema feature was introduced. New documents are always seeded
     * with {@link FloorMapFieldMapping#initialValueSchema()} by
     * {@code FloorMapInitPresenter}.</p>
     */
    @JsonProperty
    private final List<FloorMapFieldMapping> valueSchema;

    /**
     * Ordered per-type presentation settings (see {@link TypeStyle}). The list
     * <strong>order is the z-order</strong> (earlier types paint behind later
     * ones), and each entry carries the default graphic for imageless facts of
     * that type. Populated via the Settings tab's "Discover" button; may be
     * {@code null}/empty for documents that have never discovered their types.
     */
    @JsonProperty
    private final List<TypeStyle> typeStyles;

    /**
     * User-created groups of map entities (see {@link FloorMapGroup}), in display
     * order — "Maintenance", "Security". Each holds member ids drawn from the one
     * id namespace the map uses, so a group can mix event-stream entities with
     * static facts.
     *
     * <p>Groups are <em>configuration</em>, not floor-plan content: they live on
     * the document rather than in the facts store, and carry no temporal
     * versioning. {@code null}/empty for any document with no groups defined.</p>
     *
     * <p>Whether a group is currently <em>highlighted</em> on the canvas is
     * transient view state and is deliberately not stored here — the same
     * treatment layer visibility gets.</p>
     */
    @JsonProperty
    private final List<FloorMapGroup> groups;

    /**
     * What one map unit means in the real world (see
     * {@link FloorMapMeasurementUnits}) — the unit to display distances in, and
     * how many of it a map unit spans.
     *
     * <p>{@code null} for any document that has not been calibrated, which is
     * the normal state and not an error: display then falls back to
     * {@link FloorMapMeasurementUnits#DEFAULT}, one centimetre per map unit, so
     * every size on screen is still a real-world measurement. The map's true
     * scale is set with the Editor tab's Set Scale tool.</p>
     */
    @JsonProperty
    private final FloorMapMeasurementUnits measurementUnits;

    /**
     * Constructs a {@code FloorMapDoc} from its constituent fields.
     *
     * <p>This constructor is invoked by Jackson during deserialisation and
     * by the {@link Builder}. All parameters are nullable except
     * {@code uuid} and {@code name} (inherited from
     * {@link AbstractDoc}).</p>
     *
     * <p><strong>Default-on-null rules applied in the constructor:</strong></p>
     * <ul>
     *   <li>{@code matrix} — replaced with
     *       {@link FloorMapTransformationMatrix#identity()} if {@code null}</li>
     * </ul>
     *
     * <p><strong>Default-on-null rules applied in getters:</strong></p>
     * <ul>
     *   <li>{@link #getValueFormat()} returns {@link ValueFormat#JSON}
     *       if the stored field is {@code null}</li>
     *   <li>{@link #getValueSchema()} returns
     *       {@link FloorMapFieldMapping#initialValueSchema()} if the stored field is
     *       {@code null} or empty</li>
     * </ul>
     *
     * <p><strong>Back-compatibility:</strong> The {@code factsStoreRef}
     * parameter is annotated with {@code @JsonAlias("temporalStoreRef")}
     * so that existing serialised documents that use the old field name
     * {@code "temporalStoreRef"} are deserialised correctly into
     * {@code factsStoreRef}. When the document is next saved, the
     * field-level {@code @JsonProperty("factsStoreRef")} annotation
     * causes it to be written under the new name, completing the
     * migration.</p>
     *
     * @param uuid                        document UUID; must not be {@code null}
     * @param name                        document name; must not be {@code null}
     * @param version                     document version; may be {@code null}
     * @param createTimeMs                creation timestamp in millis; may be {@code null}
     * @param updateTimeMs                last-update timestamp in millis; may be {@code null}
     * @param createUser                  user who created the document; may be {@code null}
     * @param updateUser                  user who last updated the document; may be {@code null}
     * @param description                 free-text description; may be {@code null}
     * @param template                    HTML tooltip template; may be {@code null}
     * @param matrix                      global canvas matrix; defaults to identity if {@code null}
     * @param eventColumns                events-query role to column mapping; may be
     *                                    {@code null} on a document predating the mapping
     * @param factsStoreRef               facts store {@link DocRef}; may be {@code null}
     * @param eventsStoreRef              Plan B events store {@link DocRef}; may be {@code null}
     * @param eventsQuery                 StroomQL for the events store; may be {@code null}
     * @param eventsQueryTimeRange        time range for the events query; may be {@code null}
     * @param eventsQueryTablePreferences table prefs for events query results; may be {@code null}
     * @param valueFormat                 value serialisation format; may be {@code null}
     *                                    (defaults to {@link ValueFormat#JSON} via getter)
     * @param valueSchema                 value field mappings; may be {@code null}
     *                                    for legacy documents
     * @param typeStyles                  ordered per-type styles; list order is the
     *                                    paint z-order. May be {@code null}
     * @param groups                      user-created entity groups in display order;
     *                                    may be {@code null}
     * @param measurementUnits            what one map unit means in the real world;
     *                                    {@code null} when the map has no scale set
     */
    @JsonCreator
    public FloorMapDoc(@JsonProperty("uuid") final String uuid,
                       @JsonProperty("name") final String name,
                       @JsonProperty("version") final String version,
                       @JsonProperty("createTimeMs") final Long createTimeMs,
                       @JsonProperty("updateTimeMs") final Long updateTimeMs,
                       @JsonProperty("createUser") final String createUser,
                       @JsonProperty("updateUser") final String updateUser,
                       @JsonProperty("description") final String description,
                       @JsonProperty("template") final String template,
                       @JsonProperty("matrix") final FloorMapTransformationMatrix matrix,
                       @JsonProperty("eventColumns") final FloorMapEventColumns eventColumns,
                       @JsonProperty("factsStoreRef")
                       @JsonAlias("temporalStoreRef")
                       final DocRef factsStoreRef,
                       @JsonProperty("eventsStoreRef")
                       final DocRef eventsStoreRef,
                       @JsonProperty("eventsQuery") final String eventsQuery,
                       @JsonProperty("histogramQuery") final String histogramQuery,
                       @JsonProperty("extentQuery") final String extentQuery,
                       @JsonProperty("eventsQueryTimeRange") final TimeRange eventsQueryTimeRange,
                       @JsonProperty("eventsQueryTablePreferences")
                           final QueryTablePreferences eventsQueryTablePreferences,
                       @JsonProperty("valueFormat") final ValueFormat valueFormat,
                       @JsonProperty("valueSchema") final List<FloorMapFieldMapping> valueSchema,
                       @JsonProperty("typeStyles") final List<TypeStyle> typeStyles,
                       @JsonProperty("groups") final List<FloorMapGroup> groups,
                       @JsonProperty("measurementUnits")
                           final FloorMapMeasurementUnits measurementUnits) {
        super(TYPE, uuid,
                name,
                version,
                createTimeMs,
                updateTimeMs,
                createUser,
                updateUser);

        this.description = description;
        this.template = template;
        this.matrix = matrix != null ? matrix : FloorMapTransformationMatrix.identity();
        // Defaulted here rather than in the getter, so callers never see null and the document is
        // made explicit on its next save.
        this.eventColumns = eventColumns == null ? FloorMapEventColumns.defaults() : eventColumns;

        this.factsStoreRef = factsStoreRef;
        this.eventsStoreRef = eventsStoreRef;

        this.eventsQuery = eventsQuery;
        this.histogramQuery = histogramQuery;
        this.extentQuery = extentQuery;
        this.eventsQueryTimeRange = eventsQueryTimeRange;
        this.eventsQueryTablePreferences = eventsQueryTablePreferences;

        this.valueFormat = valueFormat;
        this.valueSchema = copyOrNull(valueSchema);
        this.typeStyles = copyOrNull(typeStyles);
        this.groups = copyOrNull(groups);
        this.measurementUnits = measurementUnits;
    }

    /**
     * Returns the free-text description of this floor map document.
     *
     * @return the description, or {@code null} if not set
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the HTML template used for rendering object tooltips or
     * popups on the canvas.
     *
     * @return the template string, or {@code null} if not configured
     */
    public String getTemplate() {
        return template;
    }

    /**
     * Returns which result column carries each event role.
     *
     * @return the mapping; never {@code null}
     */
    public FloorMapEventColumns getEventColumns() {
        return eventColumns;
    }

    /**
     * Returns the reference to the facts store (SQL Temporal Store).
     *
     * <p>The facts store contains spatial data: object positions,
     * background images, and transformation matrices.</p>
     *
     * @return the facts store {@link DocRef}, or {@code null} if not
     *         yet configured
     */
    public DocRef getFactsStoreRef() {
        return factsStoreRef;
    }

    /**
     * Returns the reference to the events store (a FloorMap Event Store).
     *
     * <p>The events store contains status / event records keyed by
     * entity ID, and is only ever read.</p>
     *
     * @return the events store {@link DocRef}, or {@code null} if not
     *         yet configured
     */
    public DocRef getEventsStoreRef() {
        return eventsStoreRef;
    }

    /**
     * Returns the StroomQL query string for the events store.
     *
     * @return the events query string, or {@code null} if not configured
     */
    public String getEventsQuery() {
        return eventsQuery;
    }

    /**
     * StroomQL for the timeline's density histogram.
     *
     * @return the query, or {@code null} where the document sets none, in which case the caller
     *         falls back to the generated default
     */
    public String getHistogramQuery() {
        return histogramQuery;
    }

    /** StroomQL for the timeline's "Show All" extent; may be {@code null}, as above. */
    public String getExtentQuery() {
        return extentQuery;
    }

    /**
     * Returns the time range filter applied to the events query.
     *
     * @return the events query {@link TimeRange}, or {@code null} if no
     *         time range restriction is configured
     */
    public TimeRange getEventsQueryTimeRange() {
        return eventsQueryTimeRange;
    }

    /**
     * Returns the table display preferences for the events query result
     * grid (column widths, sort order, etc.).
     *
     * @return the events query {@link QueryTablePreferences}, or
     *         {@code null} if not configured
     */
    public QueryTablePreferences getEventsQueryTablePreferences() {
        return eventsQueryTablePreferences;
    }

    /**
     * Returns the global transformation matrix applied to the canvas.
     *
     * <p>This method never returns {@code null}. If the constructor
     * received a {@code null} matrix, it was replaced with
     * {@link FloorMapTransformationMatrix#identity()}.</p>
     *
     * @return the canvas transformation matrix; never {@code null}
     */
    public FloorMapTransformationMatrix getMatrix() {
        return matrix;
    }

    /**
     * Returns the serialisation format used for the temporal entry's
     * {@code Value} column.
     *
     * <p>This method never returns {@code null}. If no explicit format has
     * been set (i.e. the underlying field is {@code null}), it defaults to
     * {@link ValueFormat#JSON}.</p>
     *
     * @return the configured {@link ValueFormat}, or {@link ValueFormat#JSON}
     *         if none was specified; never {@code null}
     */
    public ValueFormat getValueFormat() {
        return valueFormat != null ? valueFormat : ValueFormat.JSON;
    }

    /**
     * Returns the ordered list of field mappings that describe the structure
     * of a temporal entry's {@code Value} column.
     *
     * <p><strong>Back-compatibility:</strong> if the stored schema is
     * {@code null} or empty (e.g. for legacy documents created before
     * the value schema feature was introduced), the
     * {@linkplain FloorMapFieldMapping#initialValueSchema() initial
     * default schema} is returned instead. This ensures that all
     * consumers — Settings tab, entry parser, query builder — get a
     * usable schema without requiring a manual re-save of old
     * documents.</p>
     *
     * @return the value schema list; never {@code null} or empty
     */
    public List<FloorMapFieldMapping> getValueSchema() {
        if (NullSafe.isEmptyCollection(valueSchema)) {
            // initialValueSchema() is already a List.of(...), so immutable.
            return FloorMapFieldMapping.initialValueSchema();
        }
        return Collections.unmodifiableList(valueSchema);
    }

    /**
     * Returns the ordered per-type presentation settings (z-order and default
     * graphic per type). The list order is the paint/z-order.
     *
     * @return the type styles, or {@code null} if none have been configured
     */
    public List<TypeStyle> getTypeStyles() {
        return unmodifiableOrNull(typeStyles);
    }

    /**
     * Returns the user-created entity groups, in display order.
     *
     * @return the groups, or {@code null} if none have been defined
     */
    public List<FloorMapGroup> getGroups() {
        return unmodifiableOrNull(groups);
    }

    /**
     * Returns what one map unit means in the real world.
     *
     * <p>Callers should not branch on {@code null} themselves — pass the result
     * straight to
     * {@link FloorMapMeasurementUnits#format(FloorMapMeasurementUnits, double)},
     * which resolves an uncalibrated document to the default scale.</p>
     *
     * @return the measurement units, or {@code null} if no scale has been set
     */
    public FloorMapMeasurementUnits getMeasurementUnits() {
        return measurementUnits;
    }

    /**
     * Returns a new {@link DocRef.TypedBuilder} pre-configured with this
     * document's {@link #TYPE}.
     *
     * @return a typed builder for creating a {@link DocRef}; never {@code null}
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    /**
     * Compares this document to another for value equality.
     *
     * <p>Two {@code FloorMapDoc} instances are equal if they have the same
     * superclass identity (UUID, name, version, timestamps, users) and
     * every document-specific field is equal. {@code null} fields are
     * handled safely via {@link Objects#equals(Object, Object)}.</p>
     *
     * <p><strong>Note:</strong> equality is based on the <em>raw</em>
     * stored field values, not the default-on-null getter semantics. This
     * means a doc with {@code valueFormat == null} is <em>not</em> equal
     * to one with {@code valueFormat == JSON}, even though their getters
     * would return the same value. This is intentional — it allows the
     * dirty-detection logic in
     * {@code stroom.entity.client.presenter.DocPresenter#onChange()}
     * to correctly detect when a user has explicitly set a field that
     * previously relied on the default.</p>
     *
     * @param o the object to compare against
     * @return {@code true} if the objects are value-equal
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FloorMapDoc that = (FloorMapDoc) o;
        return Objects.equals(description, that.description) &&
               Objects.equals(template, that.template) &&
               Objects.equals(matrix, that.matrix) &&
               Objects.equals(eventColumns, that.eventColumns) &&
               Objects.equals(factsStoreRef, that.factsStoreRef) &&
               Objects.equals(eventsStoreRef, that.eventsStoreRef) &&
               Objects.equals(eventsQuery, that.eventsQuery) &&
               Objects.equals(histogramQuery, that.histogramQuery) &&
               Objects.equals(extentQuery, that.extentQuery) &&
               Objects.equals(eventsQueryTimeRange, that.eventsQueryTimeRange) &&
               Objects.equals(eventsQueryTablePreferences, that.eventsQueryTablePreferences) &&
               Objects.equals(valueFormat, that.valueFormat) &&
               Objects.equals(valueSchema, that.valueSchema) &&
               Objects.equals(typeStyles, that.typeStyles) &&
               // Must be compared: the client decides whether the document is
               // dirty by diffing the written doc against the read one, so a
               // group edit would never light up the save button without this.
               Objects.equals(groups, that.groups) &&
               // Likewise: calibrating the map must light up the save button.
               Objects.equals(measurementUnits, that.measurementUnits);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                description,
                template,
                matrix,
                eventColumns,
                factsStoreRef,
                eventsStoreRef,
                eventsQuery,
                histogramQuery,
                extentQuery,
                eventsQueryTimeRange,
                eventsQueryTablePreferences,
                valueFormat,
                valueSchema,
                typeStyles,
                groups,
                measurementUnits);
    }

    /**
     * Returns a new {@link Builder} pre-populated with all field values
     * from this document, ready for modification via the copy-builder
     * pattern.
     *
     * @return a pre-populated builder; never {@code null}
     */
    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Defensive copy that preserves {@code null}.
     *
     * <p>The document's collections used to be stored and handed out by reference, which made it
     * immutable only by convention — every caller had to remember to copy before mutating, and two
     * documents built from one builder shared list instances. The invariant now lives here instead
     * of in the eight-or-so presenter call sites that were upholding it.</p>
     *
     * <p>{@code null} is preserved rather than normalised to an empty list because the getters
     * distinguish the two: {@code null} means "never configured" and is what
     * {@link #getTypeStyles()} and {@link #getGroups()} document as their absent value.</p>
     */
    private static <T> List<T> copyOrNull(final List<T> list) {
        return list != null ? new ArrayList<>(list) : null;
    }

    /**
     * Unmodifiable view that preserves {@code null}.
     *
     * <p>A copy on the way in stops a caller's later edits reaching the document; this stops a
     * caller editing the document's own list. The elements are safe without copying because
     * {@link TypeStyle}, {@link FloorMapGroup} and {@link FloorMapFieldMapping} expose no setters,
     * so a copy of the list is effectively a deep copy from outside.</p>
     */
    private static <T> List<T> unmodifiableOrNull(final List<T> list) {
        return list != null ? Collections.unmodifiableList(list) : null;
    }

    /**
     * Returns a new empty {@link Builder}.
     *
     * @return a fresh builder; never {@code null}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder for constructing {@link FloorMapDoc} instances.
     *
     * <p>All setter methods accept {@code null}. Where a field has
     * default-on-null semantics, the default is applied at read time
     * by the corresponding getter on the built {@link FloorMapDoc}
     * (see the class-level Javadoc for the full list).</p>
     *
     * <p>To create a modified copy of an existing document, use
     * {@link FloorMapDoc#copy()} which returns a pre-populated builder.</p>
     */
    public static class Builder extends AbstractBuilder<FloorMapDoc, Builder> {

        private String template;
        private String description;
        private FloorMapTransformationMatrix matrix;
        private FloorMapEventColumns eventColumns;

        private DocRef factsStoreRef;
        private DocRef eventsStoreRef;
        private String eventsQuery;
        private String histogramQuery;
        private String extentQuery;
        private TimeRange eventsQueryTimeRange;
        private QueryTablePreferences eventsQueryTablePreferences;
        private ValueFormat valueFormat;
        private List<FloorMapFieldMapping> valueSchema;
        private List<TypeStyle> typeStyles;
        private List<FloorMapGroup> groups;
        private FloorMapMeasurementUnits measurementUnits;

        /**
         * Creates an empty builder. All fields default to {@code null}.
         */
        public Builder() {
        }

        /**
         * Creates a builder pre-populated with all field values from the
         * given document (copy-builder pattern).
         *
         * @param doc the document to copy; must not be {@code null}
         */
        public Builder(final FloorMapDoc doc) {
            super(doc);
            this.template = doc.template;
            this.description = doc.description;
            this.matrix = doc.matrix;
            this.eventColumns = doc.eventColumns;
            this.factsStoreRef = doc.factsStoreRef;
            this.eventsStoreRef = doc.eventsStoreRef;
            this.eventsQuery = doc.eventsQuery;
            this.histogramQuery = doc.histogramQuery;
            this.extentQuery = doc.extentQuery;
            this.eventsQueryTimeRange = doc.eventsQueryTimeRange;
            this.eventsQueryTablePreferences = doc.eventsQueryTablePreferences;
            this.valueFormat = doc.valueFormat;
            this.valueSchema = copyOrNull(doc.valueSchema);
            this.typeStyles = copyOrNull(doc.typeStyles);
            // Every tab's onWrite returns doc.copy()...build(), so a field missed
            // here is silently deleted whenever the user saves from any tab that
            // does not itself write it.
            this.groups = copyOrNull(doc.groups);
            this.measurementUnits = doc.measurementUnits;
        }

        /**
         * Sets the HTML tooltip template.
         *
         * @param template the template string, or {@code null} to clear
         * @return this builder
         */
        public Builder template(final String template) {
            this.template = template;
            return self();
        }

        /**
         * Sets the free-text description.
         *
         * @param description the description, or {@code null} to clear
         * @return this builder
         */
        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        /**
         * Sets the global canvas transformation matrix.
         *
         * <p>If {@code null}, the constructor will default to
         * {@link FloorMapTransformationMatrix#identity()}.</p>
         *
         * @param matrix the matrix, or {@code null} for identity
         * @return this builder
         */
        public Builder matrix(final FloorMapTransformationMatrix matrix) {
            this.matrix = matrix;
            return self();
        }

        /**
         * Sets which result column carries each event role.
         *
         * @param eventColumns the mapping, or {@code null} to clear
         * @return this builder
         */
        public Builder eventColumns(final FloorMapEventColumns eventColumns) {
            this.eventColumns = eventColumns;
            return self();
        }

        /**
         * Sets the facts store reference.
         *
         * @param factsStoreRef the {@link DocRef} to the SQL Temporal Store,
         *                      or {@code null} to clear
         * @return this builder
         */
        public Builder factsStoreRef(final DocRef factsStoreRef) {
            this.factsStoreRef = factsStoreRef;
            return self();
        }

        /**
         * Sets the events store reference.
         *
         * @param eventsStoreRef the {@link DocRef} to the Plan B events store, or
         *                       {@code null} to clear
         * @return this builder
         */
        public Builder eventsStoreRef(final DocRef eventsStoreRef) {
            this.eventsStoreRef = eventsStoreRef;
            return self();
        }

        /**
         * Sets the StroomQL query string for the events store.
         *
         * @param eventsQuery the query string, or {@code null} to clear
         * @return this builder
         */
        public Builder eventsQuery(final String eventsQuery) {
            this.eventsQuery = eventsQuery;
            return self();
        }

        public Builder histogramQuery(final String histogramQuery) {
            this.histogramQuery = histogramQuery;
            return self();
        }

        public Builder extentQuery(final String extentQuery) {
            this.extentQuery = extentQuery;
            return self();
        }

        /**
         * Sets the time range filter for the events query.
         *
         * @param eventsQueryTimeRange the time range, or {@code null} for
         *                             no restriction
         * @return this builder
         */
        public Builder eventsQueryTimeRange(final TimeRange eventsQueryTimeRange) {
            this.eventsQueryTimeRange = eventsQueryTimeRange;
            return self();
        }

        /**
         * Sets the table display preferences for events query results.
         *
         * @param eventsQueryTablePreferences the preferences, or {@code null}
         *                                    to use defaults
         * @return this builder
         */
        public Builder eventsQueryTablePreferences(final QueryTablePreferences eventsQueryTablePreferences) {
            this.eventsQueryTablePreferences = eventsQueryTablePreferences;
            return self();
        }

        /**
         * Sets the serialisation format for the temporal entry's Value column.
         *
         * <p>If {@code null} is passed, {@link FloorMapDoc#getValueFormat()}
         * will fall back to {@link ValueFormat#JSON} at read time.</p>
         *
         * @param valueFormat the desired {@link ValueFormat}, or {@code null}
         *                    to use the default ({@link ValueFormat#JSON})
         * @return this builder
         */
        public Builder valueFormat(final ValueFormat valueFormat) {
            this.valueFormat = valueFormat;
            return self();
        }

        /**
         * Sets the ordered list of field mappings that describe the structure
         * of a temporal entry's Value column.
         *
         * <p>Should not be set to {@code null} for new documents. Use
         * {@link FloorMapFieldMapping#initialValueSchema()} to seed a
         * new document with the standard starting schema.</p>
         *
         * @param valueSchema the list of {@link FloorMapFieldMapping} entries,
         *                    or {@code null} to clear
         * @return this builder
         */
        public Builder valueSchema(final List<FloorMapFieldMapping> valueSchema) {
            this.valueSchema = copyOrNull(valueSchema);
            return self();
        }

        /**
         * Sets the ordered per-type presentation settings (z-order + default
         * graphic per type).
         *
         * @param typeStyles the ordered {@link TypeStyle} list, or {@code null}
         * @return this builder
         */
        public Builder typeStyles(final List<TypeStyle> typeStyles) {
            this.typeStyles = copyOrNull(typeStyles);
            return self();
        }

        /**
         * Sets the user-created entity groups, in display order.
         *
         * @param groups the {@link FloorMapGroup} list, or {@code null} to clear
         * @return this builder
         */
        public Builder groups(final List<FloorMapGroup> groups) {
            this.groups = copyOrNull(groups);
            return self();
        }

        /**
         * Sets what one map unit means in the real world.
         *
         * @param measurementUnits the {@link FloorMapMeasurementUnits}, or
         *                         {@code null} to leave the map without a scale
         * @return this builder
         */
        public Builder measurementUnits(final FloorMapMeasurementUnits measurementUnits) {
            this.measurementUnits = measurementUnits;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * Builds and returns a new immutable {@link FloorMapDoc} from
         * the values set on this builder.
         *
         * @return a new {@link FloorMapDoc}; never {@code null}
         */
        @Override
        public FloorMapDoc build() {
            return new FloorMapDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    template,
                    matrix,
                    eventColumns,
                    factsStoreRef,
                    eventsStoreRef,
                    eventsQuery,
                    histogramQuery,
                    extentQuery,
                    eventsQueryTimeRange,
                    eventsQueryTablePreferences,
                    valueFormat,
                    valueSchema,
                    typeStyles,
                    groups,
                    measurementUnits);
        }
    }
}
