/*
 * Copyright 2017 Crown Copyright
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

package stroom.pathways.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.pathways.shared.pathway.Pathway;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@Description(
        """
        Analyses trace logs held in a Plan B store to learn the paths that traces take between services, \
        e.g. A -> B -> C.
        Each distinct path is remembered, so that a new or changed path can be reported as soon as it appears.
        For each span on a path it also learns constraints on the span's duration, kind, flags and \
        attributes, held as an exact value, a set, a range or a regular expression, and widens them as \
        further traces are seen.
        Whether new paths and constraints may be added, and whether those already learnt may be widened, \
        is controlled per document, so Pathways can be left learning or fixed so that anything deviating \
        from what it has learnt is reported instead of absorbed.
        Findings are written to a nominated Feed for analytic rules to act on.
        Pathways makes no judgement about the changes it reports.
        """)
@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "sharedFileStore",
        "ignoredSpanNames",
        "ignoredAttributes",
        "pathways"})
@JsonInclude(Include.NON_NULL)
public class PathwaysDoc extends AbstractDoc {

    private static final boolean DEFAULT_ALLOW_PATHWAY_CREATION = true;
    private static final boolean DEFAULT_ALLOW_PATHWAY_MUTATION = true;
    private static final boolean DEFAULT_ALLOW_CONSTRAINT_CREATION = true;
    private static final boolean DEFAULT_ALLOW_CONSTRAINT_MUTATION = true;
    private static final SimpleDuration DEFAULT_NARROWING_WINDOW = new SimpleDuration(7L, TimeUnit.DAYS);

    public static final String TYPE = "Pathways";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.PATHWAYS_DOCUMENT_TYPE;

    @JsonProperty
    private final String description;
    @JsonProperty
    private final SimpleDuration temporalOrderingTolerance;
    /**
     * How far back a pathway is held to. What a trace teaches only ever widens the model, so an
     * envelope left to itself ends up admitting everything and saying nothing. Each night the model is
     * narrowed to what has actually been seen within this of now, which is also what gives a widening
     * its meaning again: something outside the envelope is something that has not happened lately,
     * rather than something that has not happened since the model was new.
     */
    @JsonProperty
    private final SimpleDuration narrowingWindow;
    /**
     * Spans that are no part of a pathway, as names where '*' stands for any run of characters, e.g.
     * 'Ping'. For work the runtime does when it feels like it rather than when the code says to, such
     * as a connection pool checking a connection it has not used for a while: whether it happens is
     * decided by how long something sat idle, so recording it doubles the paths for every place it
     * can appear. Such a span is left out of the model as well as the path, along with whatever it
     * ran, and it takes no part in deciding what else ran at the same time. Matching is case
     * sensitive and covers the whole name.
     */
    @JsonProperty
    private final List<String> ignoredSpanNames;
    /**
     * Span attributes the model should not learn a value for, as names where '*' stands for any run of
     * characters, e.g. 'thread.*'. A matching attribute is recorded once as accepting anything and is
     * never changed again, so it stays visible against the node without the model growing every time
     * its value differs. Matching is case sensitive and covers the whole name.
     */
    @JsonProperty
    private final List<String> ignoredAttributes;
    @JsonProperty
    private final List<Pathway> pathways;
    @JsonProperty
    private final boolean allowPathwayCreation;
    @JsonProperty
    private final boolean allowPathwayMutation;
    @JsonProperty
    private final boolean allowConstraintCreation;
    @JsonProperty
    private final boolean allowConstraintMutation;
    @JsonProperty
    private final DocRef infoFeed;
    /**
     * Where this document's learnt model lives on the shared filesystem, and how many ways it is
     * split, or {@code null} while it has not been configured.
     *
     * <p>Shares {@link SharedFileStoreSettings} with the Plan B store types, which is where the path
     * and shard count mean the same two things, but a Pathways document is not a Plan B store and
     * carries them directly rather than through a settings object.
     */
    @JsonProperty
    private final SharedFileStoreSettings sharedFileStore;

    /**
     * Whether a model or a queue already exists under {@link #sharedFileStore}, so the editor can stop
     * the path and shard count being changed out from under them.
     *
     * <p>Stamped onto the document as it is fetched and never stored: where the data is decides this,
     * not what the document says about itself.
     */
    @JsonProperty("hasSharedFileStoreData")
    @JsonInclude(Include.NON_NULL)
    private final Boolean hasSharedFileStoreData;


    @JsonCreator
    public PathwaysDoc(@JsonProperty("uuid") final String uuid,
                       @JsonProperty("name") final String name,
                       @JsonProperty("version") final String version,
                       @JsonProperty("createTimeMs") final Long createTimeMs,
                       @JsonProperty("updateTimeMs") final Long updateTimeMs,
                       @JsonProperty("createUser") final String createUser,
                       @JsonProperty("updateUser") final String updateUser,
                       @JsonProperty("description") final String description,
                       @JsonProperty("temporalOrderingTolerance") final SimpleDuration temporalOrderingTolerance,
                       @JsonProperty("narrowingWindow") final SimpleDuration narrowingWindow,
                       @JsonProperty("ignoredSpanNames") final List<String> ignoredSpanNames,
                       @JsonProperty("ignoredAttributes") final List<String> ignoredAttributes,
                       @JsonProperty("pathways") final List<Pathway> pathways,
                       @JsonProperty("allowPathwayCreation") final Boolean allowPathwayCreation,
                       @JsonProperty("allowPathwayMutation") final Boolean allowPathwayMutation,
                       @JsonProperty("allowConstraintCreation") final Boolean allowConstraintCreation,
                       @JsonProperty("allowConstraintMutation") final Boolean allowConstraintMutation,
                       @JsonProperty("infoFeed") final DocRef infoFeed,
                       @JsonProperty("sharedFileStore") final SharedFileStoreSettings sharedFileStore,
                       @JsonProperty("hasSharedFileStoreData") final Boolean hasSharedFileStoreData) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.temporalOrderingTolerance = temporalOrderingTolerance;
        this.narrowingWindow = narrowingWindow;
        // Held as given, empty included, because a document saved with nothing to ignore means exactly
        // that and must not be read as never having been asked.
        this.ignoredSpanNames = ignoredSpanNames;
        this.ignoredAttributes = ignoredAttributes;
        this.pathways = pathways;
        this.allowPathwayCreation =
                Objects.requireNonNullElse(allowPathwayCreation, DEFAULT_ALLOW_PATHWAY_CREATION);
        this.allowPathwayMutation =
                Objects.requireNonNullElse(allowPathwayMutation, DEFAULT_ALLOW_PATHWAY_MUTATION);
        this.allowConstraintCreation =
                Objects.requireNonNullElse(allowConstraintCreation, DEFAULT_ALLOW_CONSTRAINT_CREATION);
        this.allowConstraintMutation =
                Objects.requireNonNullElse(allowConstraintMutation, DEFAULT_ALLOW_CONSTRAINT_MUTATION);
        this.infoFeed = infoFeed;
        this.sharedFileStore = sharedFileStore;
        this.hasSharedFileStoreData = hasSharedFileStoreData;
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    public String getDescription() {
        return description;
    }

    public SimpleDuration getTemporalOrderingTolerance() {
        return temporalOrderingTolerance;
    }

    /**
     * How far back the model is held to, never null — a document saved before there was such a setting
     * takes the default rather than being held to nothing at all.
     */
    public SimpleDuration getNarrowingWindow() {
        return narrowingWindow == null
                ? DEFAULT_NARROWING_WINDOW
                : narrowingWindow;
    }

    public List<String> getIgnoredSpanNames() {
        return ignoredSpanNames;
    }

    public List<String> getIgnoredAttributes() {
        return ignoredAttributes;
    }

    public List<Pathway> getPathways() {
        return pathways;
    }

    public boolean isAllowPathwayCreation() {
        return allowPathwayCreation;
    }

    public boolean isAllowPathwayMutation() {
        return allowPathwayMutation;
    }

    public boolean isAllowConstraintCreation() {
        return allowConstraintCreation;
    }

    public boolean isAllowConstraintMutation() {
        return allowConstraintMutation;
    }

    public DocRef getInfoFeed() {
        return infoFeed;
    }

    public boolean hasSharedFileStoreData() {
        return Boolean.TRUE.equals(hasSharedFileStoreData);
    }

    public SharedFileStoreSettings getSharedFileStore() {
        return sharedFileStore;
    }

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
        final PathwaysDoc that = (PathwaysDoc) o;
        return allowPathwayCreation == that.allowPathwayCreation &&
               allowPathwayMutation == that.allowPathwayMutation &&
               allowConstraintCreation == that.allowConstraintCreation &&
               allowConstraintMutation == that.allowConstraintMutation &&
               Objects.equals(description, that.description) &&
               Objects.equals(temporalOrderingTolerance, that.temporalOrderingTolerance) &&
               Objects.equals(narrowingWindow, that.narrowingWindow) &&
               Objects.equals(ignoredSpanNames, that.ignoredSpanNames) &&
               Objects.equals(ignoredAttributes, that.ignoredAttributes) &&
               Objects.equals(pathways, that.pathways) &&
               Objects.equals(infoFeed, that.infoFeed) &&
               // hasSharedFileStoreData is left out on purpose: it is stamped onto the document as
               // it is fetched and never stored, so comparing it would make an untouched document
               // look edited.
               Objects.equals(sharedFileStore, that.sharedFileStore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                description,
                temporalOrderingTolerance,
                narrowingWindow,
                ignoredSpanNames,
                ignoredAttributes,
                pathways,
                allowPathwayCreation,
                allowPathwayMutation,
                allowConstraintCreation,
                allowConstraintMutation,
                infoFeed,
                sharedFileStore);
    }

    @Override
    public String toString() {
        return "PathwaysDoc{" +
               "description='" + description + '\'' +
               ", temporalOrderingTolerance=" + temporalOrderingTolerance +
               ", narrowingWindow=" + narrowingWindow +
               ", pathways=" + pathways +
               ", allowPathwayCreation=" + allowPathwayCreation +
               ", allowPathwayMutation=" + allowPathwayMutation +
               ", allowConstraintCreation=" + allowConstraintCreation +
               ", allowConstraintMutation=" + allowConstraintMutation +
               ", infoFeed=" + infoFeed +
               ", sharedFileStore=" + sharedFileStore +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractBuilder<PathwaysDoc, Builder> {

        private String description;
        private SimpleDuration temporalOrderingTolerance = new SimpleDuration(0L, TimeUnit.NANOSECONDS);
        private SimpleDuration narrowingWindow = DEFAULT_NARROWING_WINDOW;
        private List<String> ignoredSpanNames;
        private List<String> ignoredAttributes;
        private List<Pathway> pathways;
        private boolean allowPathwayCreation = true;
        private boolean allowPathwayMutation = true;
        private boolean allowConstraintCreation = true;
        private boolean allowConstraintMutation = true;
        private DocRef infoFeed;
        private SharedFileStoreSettings sharedFileStore;

        // Intentionally not copied — it is always recomputed server-side.
        private Boolean hasSharedFileStoreData;

        private Builder() {
        }

        private Builder(final PathwaysDoc pathwaysDoc) {
            super(pathwaysDoc);
            this.description = pathwaysDoc.description;
            this.temporalOrderingTolerance = pathwaysDoc.temporalOrderingTolerance;
            this.narrowingWindow = pathwaysDoc.narrowingWindow;
            this.ignoredSpanNames = pathwaysDoc.ignoredSpanNames;
            this.ignoredAttributes = pathwaysDoc.ignoredAttributes;
            this.pathways = pathwaysDoc.pathways;
            this.allowPathwayCreation = pathwaysDoc.allowPathwayCreation;
            this.allowPathwayMutation = pathwaysDoc.allowPathwayMutation;
            this.allowConstraintCreation = pathwaysDoc.allowConstraintCreation;
            this.allowConstraintMutation = pathwaysDoc.allowConstraintMutation;
            this.infoFeed = pathwaysDoc.infoFeed;
            this.sharedFileStore = pathwaysDoc.sharedFileStore;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder narrowingWindow(final SimpleDuration narrowingWindow) {
            this.narrowingWindow = narrowingWindow;
            return self();
        }

        public Builder temporalOrderingTolerance(final SimpleDuration temporalOrderingTolerance) {
            this.temporalOrderingTolerance = temporalOrderingTolerance;
            return self();
        }

        public Builder ignoredSpanNames(final List<String> ignoredSpanNames) {
            this.ignoredSpanNames = ignoredSpanNames;
            return self();
        }

        public Builder ignoredAttributes(final List<String> ignoredAttributes) {
            this.ignoredAttributes = ignoredAttributes;
            return self();
        }

        public Builder pathways(final List<Pathway> pathways) {
            this.pathways = pathways;
            return self();
        }

        public Builder allowPathwayCreation(final boolean allowPathwayCreation) {
            this.allowPathwayCreation = allowPathwayCreation;
            return self();
        }

        public Builder allowPathwayMutation(final boolean allowPathwayMutation) {
            this.allowPathwayMutation = allowPathwayMutation;
            return self();
        }

        public Builder allowConstraintCreation(final boolean allowConstraintCreation) {
            this.allowConstraintCreation = allowConstraintCreation;
            return self();
        }

        public Builder allowConstraintMutation(final boolean allowConstraintMutation) {
            this.allowConstraintMutation = allowConstraintMutation;
            return self();
        }

        public Builder infoFeed(final DocRef infoFeed) {
            this.infoFeed = infoFeed;
            return self();
        }

        public Builder sharedFileStore(final SharedFileStoreSettings sharedFileStore) {
            this.sharedFileStore = sharedFileStore;
            return self();
        }

        public Builder hasSharedFileStoreData(final Boolean hasSharedFileStoreData) {
            this.hasSharedFileStoreData = hasSharedFileStoreData;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public PathwaysDoc build() {
            return new PathwaysDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    temporalOrderingTolerance,
                    narrowingWindow,
                    ignoredSpanNames,
                    ignoredAttributes,
                    pathways,
                    allowPathwayCreation,
                    allowPathwayMutation,
                    allowConstraintCreation,
                    allowConstraintMutation,
                    infoFeed,
                    sharedFileStore,
                    hasSharedFileStoreData);
        }
    }
}
