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

/// Immutable document describing a floor map visualisation.
///
/// A `FloorMapDoc` ties together a *facts store* (a
/// `SqlTemporalStoreDoc`, which the Editor tab writes to) and an
/// *events store* (a `FloorMapEventStoreDoc`, which is read-only here), along
/// with the queries, display preferences, and value-schema metadata the floor
/// map UI needs to parse and render temporal entries on a 2-D canvas.
///
/// ### UI Tabs
///
/// When a user opens a Floor Map document in the Stroom UI, the
/// `FloorMapPresenter`
/// builds several tabs, each backed by a sub-presenter:
///
/// Floor Map tabs and their presenters
///
/// | Tab | Presenter | Purpose |
/// |---|---|---|
/// | Settings | `FloorMapSettingsPresenter` | Configures store references, value format, and value schema |
/// | Events Query | `FloorMapQueryPresenter` | Edits the StroomQL query for the events store |
/// | Map | `FloorMapMapPresenter` | Read-only (or light-edit) canvas rendering of facts at a point in time |
/// | Editor | `FloorMapEditorPresenter` | Full authoring environment with staged (pending) saves |
///
/// ### Two-Store Architecture
///
/// - **Facts store** ([#factsStoreRef]) — a SQL Temporal Store
///   containing the spatial data (objects, positions, background image, matrices).
/// - **Events store** ([#eventsStoreRef]) — a FloorMap Event Store of
///   state type `TEMPORAL_STATE`, containing status / event records keyed by
///   entity ID. Queried via [#eventsQuery]; never written to by the floor map.
///
/// ### Value Schema
///
/// Each temporal entry's `Value` column is a serialised string whose
/// format is determined by [#valueFormat]; both [ValueFormat#JSON] and
/// [ValueFormat#XML] are supported. The [#valueSchema] list
/// tells the UI how to read individual fields from that value string: each
/// [FloorMapFieldMapping] maps a
/// [Role][FloorMapFieldMapping.Role] (e.g. `TYPE`, `POSITION`)
/// to a path within the serialised structure (e.g. `".type"`,
/// `".coords"`).
///
/// ### Null Conventions
///
/// This class is JSON-serialised with
/// [NON_NULL][JsonInclude.Include#NON_NULL], so `null` fields
/// are omitted from the stored JSON. The following getters apply
/// *default-on-null* logic to guarantee a non-null return value:
///
/// - [#getValueFormat()] → defaults to [ValueFormat#JSON]
/// - [#getMatrix()] → never null; the constructor defaults a
///   `null` input to [FloorMapTransformationMatrix#identity()]
/// - [#getValueSchema()] → defaults to
///   [FloorMapFieldMapping#initialValueSchema()] when the stored schema is
///   `null` *or empty*, so legacy documents still parse
///
/// All other getters return exactly what was passed to the constructor and
/// **may return `null`**; see each getter's Javadoc for details.
///
/// ### Immutability and Builder
///
/// All fields are `final`. Mutation is done via the copy-builder
/// pattern: call [#copy()] to obtain a pre-populated [Builder],
/// modify the desired fields, and call [Builder#build()]. A fresh
/// builder can be obtained via [#builder()].
///
/// @see FloorMapFieldMapping
/// @see ValueFormat
/// @see FloorMapTransformationMatrix
@Description(
    """
    Defines a floor map document which can be used to visualize data over time.
    """)
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class FloorMapDoc extends AbstractDoc {

    public static final String TYPE = "FloorMap";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.FLOOR_MAP_DOCUMENT_TYPE;

    /// Free-text description of this floor map document.
    /// Markdown, shown and edited on the Documentation tab.
    /// May be `null` if not set.
    @JsonProperty
    private final String description;

    /// HTML template used for rendering object tooltips / popups on the canvas.
    /// May be `null` if not configured.
    @JsonProperty
    private final String template;

    /// Global transformation matrix applied to the canvas.
    /// Never stored as `null` — the constructor replaces a `null`
    /// input with [FloorMapTransformationMatrix#identity()].
    @JsonProperty
    private final FloorMapTransformationMatrix matrix;

    /// Which result column of the events query carries each [FloorMapEventRole].
    ///
    /// Replaced the `entityIdColumn` and `locationIdColumn` string settings, and the
    /// hardcoded `"type"` literal the parser used for the entity's kind, on 2026-09-07. Three
    /// mechanisms for one relationship became one; see [FloorMapEventRole] for what that
    /// asymmetry cost.
    ///
    /// Never `null`: the constructor substitutes [FloorMapEventColumns#defaults()]
    /// for a document saved before the change, so one using the default aliases — which every
    /// document created by the init dialog does — keeps working untouched, and is written back
    /// explicitly on its next save. That is not a fallback chain but the observation that the
    /// default mapping and the default query are generated from the same [FloorMapEventRole]
    /// constants, so a document that never changed its aliases is already described by the
    /// defaults. One with hand-edited aliases needs the mapping setting, and the Map tab names the
    /// mapping role by role rather than drawing nothing in silence.
    @JsonProperty
    private final FloorMapEventColumns eventColumns;

    /// Reference to the SQL Temporal Store document used as the facts store.
    /// The facts store contains spatial data: object positions, background
    /// images, and transformation matrices.
    /// May be `null` if not yet configured.
    ///
    /// **Back-compatibility:** previously serialised as
    /// `"temporalStoreRef"`; the [JsonAlias] on the constructor
    /// parameter handles migration transparently.
    @JsonProperty("factsStoreRef")
    private final DocRef factsStoreRef;

    /// Reference to the Plan B document used as the events store. The events store
    /// contains status / event records keyed by entity ID, and is only ever read.
    ///
    /// Only the referenced document's *name* is used at query time — it is
    /// substituted into the `param('EventStore')` placeholder of
    /// [#eventsQuery] — so nothing here is coupled to a particular store
    /// implementation. In practice the store must expose `Key`,
    /// `EffectiveTime` and `Value`, which for Plan B means a state type of
    /// `TEMPORAL_STATE`; the pickers restrict the choice to Plan B documents but
    /// cannot filter on state type, so a mismatch surfaces as a query-time error.
    ///
    /// For Plan B that name is load-bearing twice over: the `<map>` element of an
    /// ingest XSLT must *equal the store's own name* too, so renaming this document
    /// breaks ingest lookups as well as the events query.
    ///
    /// May be `null` if not yet configured.
    @JsonProperty
    private final DocRef eventsStoreRef;

    /// StroomQL query string for the events store.
    /// Executed by
    /// `FloorMapMapPresenter` to
    /// populate the events overlay on the canvas.
    /// May be `null` if not configured.
    @JsonProperty
    private final String eventsQuery;

    /// StroomQL behind the timeline's density histogram, and behind "Show All".
    ///
    /// Editable like [#eventsQuery], and defaulted on creation from
    /// `FloorMapQueryBuilder`. Both read the events store directly rather than wrapping the
    /// events query, so a `where` clause here narrows the bars or the extent independently of
    /// what the map draws.
    @JsonProperty
    private final String histogramQuery;

    @JsonProperty
    private final String extentQuery;

    /// Time range filter applied to the events query.
    /// May be `null` if no time range restriction is configured.
    @JsonProperty
    private final TimeRange eventsQueryTimeRange;

    /// Table display preferences (column widths, sort order, etc.) for the
    /// events query result grid.
    /// May be `null` if not configured.
    @JsonProperty
    private final QueryTablePreferences eventsQueryTablePreferences;

    /// The serialisation format of the temporal entry's `Value` column.
    /// May be `null` in the stored JSON; [#getValueFormat()]
    /// defaults to [ValueFormat#JSON] in that case.
    ///
    /// Both [ValueFormat#JSON] and [ValueFormat#XML] are supported;
    /// `ValueAccessorFactory.forFormat` selects the reader/writer.
    @JsonProperty
    private final ValueFormat valueFormat;

    /// Ordered list of field mappings that describe the structure of a
    /// temporal entry's `Value` column. Each entry maps a
    /// [Role][FloorMapFieldMapping.Role] to a path within the
    /// serialised value (e.g. `".type"`, `".coords"`).
    ///
    /// May be `null` for documents created before the value
    /// schema feature was introduced. New documents are always seeded
    /// with [FloorMapFieldMapping#initialValueSchema()] by
    /// `FloorMapInitPresenter`.
    @JsonProperty
    private final List<FloorMapFieldMapping> valueSchema;

    /// Ordered per-type presentation settings (see [TypeStyle]). The list
    /// **order is the z-order** (earlier types paint behind later
    /// ones), and each entry carries the default graphic for imageless facts of
    /// that type. Populated via the Settings tab's "Discover" button; may be
    /// `null`/empty for documents that have never discovered their types.
    @JsonProperty
    private final List<TypeStyle> typeStyles;

    /// User-created groups of map entities (see [FloorMapGroup]), in display
    /// order — "Maintenance", "Security". Each holds member ids drawn from the one
    /// id namespace the map uses, so a group can mix event-stream entities with
    /// static facts.
    ///
    /// Groups are *configuration*, not floor-plan content: they live on
    /// the document rather than in the facts store, and carry no temporal
    /// versioning. `null`/empty for any document with no groups defined.
    ///
    /// Whether a group is currently *highlighted* on the canvas is
    /// transient view state and is deliberately not stored here — the same
    /// treatment layer visibility gets.
    @JsonProperty
    private final List<FloorMapGroup> groups;

    /// What one map unit means in the real world (see
    /// [FloorMapMeasurementUnits]) — the unit to display distances in, and
    /// how many of it a map unit spans.
    ///
    /// `null` for any document that has not been calibrated, which is
    /// the normal state and not an error: display then falls back to
    /// [FloorMapMeasurementUnits#DEFAULT], one centimetre per map unit, so
    /// every size on screen is still a real-world measurement. The map's true
    /// scale is set with the Editor tab's Set Scale tool.
    @JsonProperty
    private final FloorMapMeasurementUnits measurementUnits;

    /// Constructs a `FloorMapDoc` from its constituent fields.
    ///
    /// This constructor is invoked by Jackson during deserialisation and
    /// by the [Builder]. All parameters are nullable except
    /// `uuid` and `name` (inherited from
    /// [AbstractDoc]).
    ///
    /// **Default-on-null rules applied in the constructor:**
    ///
    /// - `matrix` — replaced with
    ///   [FloorMapTransformationMatrix#identity()] if `null`
    ///
    /// **Default-on-null rules applied in getters:**
    ///
    /// - [#getValueFormat()] returns [ValueFormat#JSON]
    ///   if the stored field is `null`
    /// - [#getValueSchema()] returns
    ///   [FloorMapFieldMapping#initialValueSchema()] if the stored field is
    ///   `null` or empty
    ///
    /// **Back-compatibility:** The `factsStoreRef`
    /// parameter is annotated with `@JsonAlias("temporalStoreRef")`
    /// so that existing serialised documents that use the old field name
    /// `"temporalStoreRef"` are deserialised correctly into
    /// `factsStoreRef`. When the document is next saved, the
    /// field-level `@JsonProperty("factsStoreRef")` annotation
    /// causes it to be written under the new name, completing the
    /// migration.
    ///
    /// @param uuid                        document UUID; must not be `null`
    /// @param name                        document name; must not be `null`
    /// @param version                     document version; may be `null`
    /// @param createTimeMs                creation timestamp in millis; may be `null`
    /// @param updateTimeMs                last-update timestamp in millis; may be `null`
    /// @param createUser                  user who created the document; may be `null`
    /// @param updateUser                  user who last updated the document; may be `null`
    /// @param description                 free-text description; may be `null`
    /// @param template                    HTML tooltip template; may be `null`
    /// @param matrix                      global canvas matrix; defaults to identity if `null`
    /// @param eventColumns                events-query role to column mapping; may be
    ///         `null` on a document predating the mapping
    /// @param factsStoreRef               facts store [DocRef]; may be `null`
    /// @param eventsStoreRef              Plan B events store [DocRef]; may be `null`
    /// @param eventsQuery                 StroomQL for the events store; may be `null`
    /// @param eventsQueryTimeRange        time range for the events query; may be `null`
    /// @param eventsQueryTablePreferences table prefs for events query results; may be `null`
    /// @param valueFormat                 value serialisation format; may be `null`
    ///         (defaults to [ValueFormat#JSON] via getter)
    /// @param valueSchema                 value field mappings; may be `null`
    ///         for legacy documents
    /// @param typeStyles                  ordered per-type styles; list order is the
    ///         paint z-order. May be `null`
    /// @param groups                      user-created entity groups in display order;
    ///         may be `null`
    /// @param measurementUnits            what one map unit means in the real world;
    ///         `null` when the map has no scale set
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

    /// Returns the free-text description of this floor map document.
    ///
    /// @return the description, or `null` if not set
    public String getDescription() {
        return description;
    }

    /// Returns the HTML template used for rendering object tooltips or
    /// popups on the canvas.
    ///
    /// @return the template string, or `null` if not configured
    public String getTemplate() {
        return template;
    }

    /// Returns which result column carries each event role.
    ///
    /// @return the mapping; never `null`
    public FloorMapEventColumns getEventColumns() {
        return eventColumns;
    }

    /// Returns the reference to the facts store (SQL Temporal Store).
    ///
    /// The facts store contains spatial data: object positions,
    /// background images, and transformation matrices.
    ///
    /// @return the facts store [DocRef], or `null` if not
    ///         yet configured
    public DocRef getFactsStoreRef() {
        return factsStoreRef;
    }

    /// Returns the reference to the events store (a FloorMap Event Store).
    ///
    /// The events store contains status / event records keyed by
    /// entity ID, and is only ever read.
    ///
    /// @return the events store [DocRef], or `null` if not
    ///         yet configured
    public DocRef getEventsStoreRef() {
        return eventsStoreRef;
    }

    /// Returns the StroomQL query string for the events store.
    ///
    /// @return the events query string, or `null` if not configured
    public String getEventsQuery() {
        return eventsQuery;
    }

    /// StroomQL for the timeline's density histogram.
    ///
    /// @return the query, or `null` where the document sets none, in which case the caller
    ///         falls back to the generated default
    public String getHistogramQuery() {
        return histogramQuery;
    }

    /// StroomQL for the timeline's "Show All" extent; may be `null`, as above.
    public String getExtentQuery() {
        return extentQuery;
    }

    /// Returns the time range filter applied to the events query.
    ///
    /// @return the events query [TimeRange], or `null` if no
    ///         time range restriction is configured
    public TimeRange getEventsQueryTimeRange() {
        return eventsQueryTimeRange;
    }

    /// Returns the table display preferences for the events query result
    /// grid (column widths, sort order, etc.).
    ///
    /// @return the events query [QueryTablePreferences], or
    ///         `null` if not configured
    public QueryTablePreferences getEventsQueryTablePreferences() {
        return eventsQueryTablePreferences;
    }

    /// Returns the global transformation matrix applied to the canvas.
    ///
    /// This method never returns `null`. If the constructor
    /// received a `null` matrix, it was replaced with
    /// [FloorMapTransformationMatrix#identity()].
    ///
    /// @return the canvas transformation matrix; never `null`
    public FloorMapTransformationMatrix getMatrix() {
        return matrix;
    }

    /// Returns the serialisation format used for the temporal entry's
    /// `Value` column.
    ///
    /// This method never returns `null`. If no explicit format has
    /// been set (i.e. the underlying field is `null`), it defaults to
    /// [ValueFormat#JSON].
    ///
    /// @return the configured [ValueFormat], or [ValueFormat#JSON]
    ///         if none was specified; never `null`
    public ValueFormat getValueFormat() {
        return valueFormat != null ? valueFormat : ValueFormat.JSON;
    }

    /// Returns the ordered list of field mappings that describe the structure
    /// of a temporal entry's `Value` column.
    ///
    /// **Back-compatibility:** if the stored schema is
    /// `null` or empty (e.g. for legacy documents created before
    /// the value schema feature was introduced), the
    /// [initial default schema][FloorMapFieldMapping#initialValueSchema()] is returned instead.
    /// This ensures that all consumers — Settings tab, entry parser, query builder — get a
    /// usable schema without requiring a manual re-save of old
    /// documents.
    ///
    /// @return the value schema list; never `null` or empty
    public List<FloorMapFieldMapping> getValueSchema() {
        if (NullSafe.isEmptyCollection(valueSchema)) {
            // initialValueSchema() is already a List.of(...), so immutable.
            return FloorMapFieldMapping.initialValueSchema();
        }
        return Collections.unmodifiableList(valueSchema);
    }

    /// Returns the ordered per-type presentation settings (z-order and default
    /// graphic per type). The list order is the paint/z-order.
    ///
    /// @return the type styles, or `null` if none have been configured
    public List<TypeStyle> getTypeStyles() {
        return unmodifiableOrNull(typeStyles);
    }

    /// Returns the user-created entity groups, in display order.
    ///
    /// @return the groups, or `null` if none have been defined
    public List<FloorMapGroup> getGroups() {
        return unmodifiableOrNull(groups);
    }

    /// Returns what one map unit means in the real world.
    ///
    /// Callers should not branch on `null` themselves — pass the result
    /// straight to
    /// [FloorMapMeasurementUnits#format(FloorMapMeasurementUnits, double)],
    /// which resolves an uncalibrated document to the default scale.
    ///
    /// @return the measurement units, or `null` if no scale has been set
    public FloorMapMeasurementUnits getMeasurementUnits() {
        return measurementUnits;
    }

    /// Returns a new [DocRef.TypedBuilder] pre-configured with this
    /// document's [#TYPE].
    ///
    /// @return a typed builder for creating a [DocRef]; never `null`
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    /// Compares this document to another for value equality.
    ///
    /// Two `FloorMapDoc` instances are equal if they have the same
    /// superclass identity (UUID, name, version, timestamps, users) and
    /// every document-specific field is equal. `null` fields are
    /// handled safely via [Objects#equals(Object, Object)].
    ///
    /// **Note:** equality is based on the *raw*
    /// stored field values, not the default-on-null getter semantics. This
    /// means a doc with `valueFormat == null` is *not* equal
    /// to one with `valueFormat == JSON`, even though their getters
    /// would return the same value. This is intentional — it allows the
    /// dirty-detection logic in
    /// `stroom.entity.client.presenter.DocPresenter#onChange()`
    /// to correctly detect when a user has explicitly set a field that
    /// previously relied on the default.
    ///
    /// @param o the object to compare against
    /// @return `true` if the objects are value-equal
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

    /// {@inheritDoc}
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

    /// Returns a new [Builder] pre-populated with all field values
    /// from this document, ready for modification via the copy-builder
    /// pattern.
    ///
    /// @return a pre-populated builder; never `null`
    public Builder copy() {
        return new Builder(this);
    }

    /// Defensive copy that preserves `null`.
    ///
    /// The document's collections used to be stored and handed out by reference, which made it
    /// immutable only by convention — every caller had to remember to copy before mutating, and two
    /// documents built from one builder shared list instances. The invariant now lives here instead
    /// of in the eight-or-so presenter call sites that were upholding it.
    ///
    /// `null` is preserved rather than normalised to an empty list because the getters
    /// distinguish the two: `null` means "never configured" and is what
    /// [#getTypeStyles()] and [#getGroups()] document as their absent value.
    private static <T> List<T> copyOrNull(final List<T> list) {
        return list != null ? new ArrayList<>(list) : null;
    }

    /// Unmodifiable view that preserves `null`.
    ///
    /// A copy on the way in stops a caller's later edits reaching the document; this stops a
    /// caller editing the document's own list. The elements are safe without copying because
    /// [TypeStyle], [FloorMapGroup] and [FloorMapFieldMapping] expose no setters,
    /// so a copy of the list is effectively a deep copy from outside.
    private static <T> List<T> unmodifiableOrNull(final List<T> list) {
        return list != null ? Collections.unmodifiableList(list) : null;
    }

    /// Returns a new empty [Builder].
    ///
    /// @return a fresh builder; never `null`
    public static Builder builder() {
        return new Builder();
    }

    /// Mutable builder for constructing [FloorMapDoc] instances.
    ///
    /// All setter methods accept `null`. Where a field has
    /// default-on-null semantics, the default is applied at read time
    /// by the corresponding getter on the built [FloorMapDoc]
    /// (see the class-level Javadoc for the full list).
    ///
    /// To create a modified copy of an existing document, use
    /// [FloorMapDoc#copy()] which returns a pre-populated builder.
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

        /// Creates an empty builder. All fields default to `null`.
        public Builder() {
        }

        /// Creates a builder pre-populated with all field values from the
        /// given document (copy-builder pattern).
        ///
        /// @param doc the document to copy; must not be `null`
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

        /// Sets the HTML tooltip template.
        ///
        /// @param template the template string, or `null` to clear
        /// @return this builder
        public Builder template(final String template) {
            this.template = template;
            return self();
        }

        /// Sets the free-text description.
        ///
        /// @param description the description, or `null` to clear
        /// @return this builder
        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        /// Sets the global canvas transformation matrix.
        ///
        /// If `null`, the constructor will default to
        /// [FloorMapTransformationMatrix#identity()].
        ///
        /// @param matrix the matrix, or `null` for identity
        /// @return this builder
        public Builder matrix(final FloorMapTransformationMatrix matrix) {
            this.matrix = matrix;
            return self();
        }

        /// Sets which result column carries each event role.
        ///
        /// @param eventColumns the mapping, or `null` to clear
        /// @return this builder
        public Builder eventColumns(final FloorMapEventColumns eventColumns) {
            this.eventColumns = eventColumns;
            return self();
        }

        /// Sets the facts store reference.
        ///
        /// @param factsStoreRef the [DocRef] to the SQL Temporal Store,
        ///         or `null` to clear
        /// @return this builder
        public Builder factsStoreRef(final DocRef factsStoreRef) {
            this.factsStoreRef = factsStoreRef;
            return self();
        }

        /// Sets the events store reference.
        ///
        /// @param eventsStoreRef the [DocRef] to the Plan B events store, or
        ///         `null` to clear
        /// @return this builder
        public Builder eventsStoreRef(final DocRef eventsStoreRef) {
            this.eventsStoreRef = eventsStoreRef;
            return self();
        }

        /// Sets the StroomQL query string for the events store.
        ///
        /// @param eventsQuery the query string, or `null` to clear
        /// @return this builder
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

        /// Sets the time range filter for the events query.
        ///
        /// @param eventsQueryTimeRange the time range, or `null` for
        ///         no restriction
        /// @return this builder
        public Builder eventsQueryTimeRange(final TimeRange eventsQueryTimeRange) {
            this.eventsQueryTimeRange = eventsQueryTimeRange;
            return self();
        }

        /// Sets the table display preferences for events query results.
        ///
        /// @param eventsQueryTablePreferences the preferences, or `null`
        ///         to use defaults
        /// @return this builder
        public Builder eventsQueryTablePreferences(final QueryTablePreferences eventsQueryTablePreferences) {
            this.eventsQueryTablePreferences = eventsQueryTablePreferences;
            return self();
        }

        /// Sets the serialisation format for the temporal entry's Value column.
        ///
        /// If `null` is passed, [FloorMapDoc#getValueFormat()]
        /// will fall back to [ValueFormat#JSON] at read time.
        ///
        /// @param valueFormat the desired [ValueFormat], or `null`
        ///         to use the default ([ValueFormat#JSON])
        /// @return this builder
        public Builder valueFormat(final ValueFormat valueFormat) {
            this.valueFormat = valueFormat;
            return self();
        }

        /// Sets the ordered list of field mappings that describe the structure
        /// of a temporal entry's Value column.
        ///
        /// Should not be set to `null` for new documents. Use
        /// [FloorMapFieldMapping#initialValueSchema()] to seed a
        /// new document with the standard starting schema.
        ///
        /// @param valueSchema the list of [FloorMapFieldMapping] entries,
        ///         or `null` to clear
        /// @return this builder
        public Builder valueSchema(final List<FloorMapFieldMapping> valueSchema) {
            this.valueSchema = copyOrNull(valueSchema);
            return self();
        }

        /// Sets the ordered per-type presentation settings (z-order + default
        /// graphic per type).
        ///
        /// @param typeStyles the ordered [TypeStyle] list, or `null`
        /// @return this builder
        public Builder typeStyles(final List<TypeStyle> typeStyles) {
            this.typeStyles = copyOrNull(typeStyles);
            return self();
        }

        /// Sets the user-created entity groups, in display order.
        ///
        /// @param groups the [FloorMapGroup] list, or `null` to clear
        /// @return this builder
        public Builder groups(final List<FloorMapGroup> groups) {
            this.groups = copyOrNull(groups);
            return self();
        }

        /// Sets what one map unit means in the real world.
        ///
        /// @param measurementUnits the [FloorMapMeasurementUnits], or
        ///         `null` to leave the map without a scale
        /// @return this builder
        public Builder measurementUnits(final FloorMapMeasurementUnits measurementUnits) {
            this.measurementUnits = measurementUnits;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        /// Builds and returns a new immutable [FloorMapDoc] from
        /// the values set on this builder.
        ///
        /// @return a new [FloorMapDoc]; never `null`
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
