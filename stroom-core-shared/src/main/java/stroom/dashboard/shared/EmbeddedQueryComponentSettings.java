/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dashboard.shared;

import stroom.docref.DocRef;
import stroom.query.api.QueryKey;
import stroom.query.api.TableSettings;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryTablePreferences;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "reference",
        "queryRef",
        "automate",
        "selectionHandlers",
        "lastQueryKey",
        "lastQueryNode",
        "showTable",
        "queryTablePreferences",
        "selectionFilter",
        "embeddedQueryDoc"
})
@JsonInclude(Include.NON_NULL)
public final class EmbeddedQueryComponentSettings
        extends AbstractQueryComponentSettings
        implements ComponentSettings, HasSelectionQuery, HasSelectionFilter {

    @JsonProperty
    private final Boolean reference;
    @JsonProperty
    private final DocRef queryRef;
    @JsonProperty
    private final Boolean showTable;
    @JsonProperty
    private final QueryTablePreferences queryTablePreferences;
    @JsonProperty
    private final List<ComponentSelectionHandler> selectionFilter;
    @JsonProperty
    private final QueryDoc embeddedQueryDoc;

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public EmbeddedQueryComponentSettings(
            @JsonProperty("reference") final Boolean reference,
            @JsonProperty("queryRef") final DocRef queryRef,
            @JsonProperty("automate") final Automate automate,
            @JsonProperty("selectionHandlers") final List<ComponentSelectionHandler> selectionHandlers,
            @JsonProperty("lastQueryKey") final QueryKey lastQueryKey,
            @JsonProperty("lastQueryNode") final String lastQueryNode,
            @JsonProperty("showTable") final Boolean showTable,
            @JsonProperty("queryTablePreferences") final QueryTablePreferences queryTablePreferences,
            @JsonProperty("selectionFilter") final List<ComponentSelectionHandler> selectionFilter,
            @JsonProperty("embeddedQueryDoc") final QueryDoc embeddedQueryDoc) {
        super(automate, selectionHandlers, lastQueryKey, lastQueryNode);
        this.reference = reference;
        this.queryRef = queryRef;
        this.showTable = showTable;
        this.queryTablePreferences = queryTablePreferences;
        this.selectionFilter = selectionFilter;
        this.embeddedQueryDoc = embeddedQueryDoc;
    }

    public Boolean getReference() {
        return reference;
    }

    public boolean reference() {
        return reference == null || reference;
    }

    public DocRef getQueryRef() {
        return queryRef;
    }

    public Boolean getShowTable() {
        return showTable;
    }

    public QueryTablePreferences getQueryTablePreferences() {
        return queryTablePreferences;
    }

    @Override
    public List<ComponentSelectionHandler> getSelectionFilter() {
        return selectionFilter;
    }

    public QueryDoc getEmbeddedQueryDoc() {
        return embeddedQueryDoc;
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
        final EmbeddedQueryComponentSettings that = (EmbeddedQueryComponentSettings) o;
        return Objects.equals(reference, that.reference) &&
               Objects.equals(queryRef, that.queryRef) &&
               Objects.equals(showTable, that.showTable) &&
               Objects.equals(queryTablePreferences, that.queryTablePreferences) &&
               Objects.equals(selectionFilter, that.selectionFilter) &&
               Objects.equals(embeddedQueryDoc, that.embeddedQueryDoc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                reference,
                queryRef,
                showTable,
                queryTablePreferences,
                selectionFilter,
                embeddedQueryDoc);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link TableSettings tableSettings}
     */
    public static final class Builder
            extends AbstractQueryComponentSettings.AbstractBuilder<EmbeddedQueryComponentSettings, Builder>
            implements
            HasSelectionQueryBuilder<EmbeddedQueryComponentSettings, Builder>,
            HasSelectionFilterBuilder<EmbeddedQueryComponentSettings, Builder> {

        private Boolean reference;
        private DocRef queryRef;
        private Boolean showTable;
        private QueryTablePreferences queryTablePreferences;
        private List<ComponentSelectionHandler> selectionFilter;
        private QueryDoc embeddedQueryDoc;

        private Builder() {
            super();
        }

        private Builder(final EmbeddedQueryComponentSettings settings) {
            super(settings);
            this.reference = settings.reference;
            this.queryRef = settings.queryRef;
            this.showTable = settings.showTable;
            this.queryTablePreferences = settings.queryTablePreferences == null
                    ? null
                    : settings.queryTablePreferences.copy().build();
            this.selectionFilter = settings.selectionFilter == null
                    ? null
                    : new ArrayList<>(settings.selectionFilter);
            this.embeddedQueryDoc = settings.embeddedQueryDoc;
        }

        public Builder reference(final Boolean reference) {
            this.reference = reference;
            return self();
        }

        public Builder queryRef(final DocRef queryRef) {
            this.queryRef = queryRef;
            return self();
        }

        public Builder showTable(final Boolean showTable) {
            this.showTable = showTable;
            return self();
        }

        public Builder queryTablePreferences(final QueryTablePreferences queryTablePreferences) {
            this.queryTablePreferences = queryTablePreferences;
            return self();
        }

        @Override
        public Builder selectionFilter(final List<ComponentSelectionHandler> selectionFilter) {
            this.selectionFilter = selectionFilter;
            return self();
        }

        public Builder embeddedQueryDoc(final QueryDoc embeddedQueryDoc) {
            this.embeddedQueryDoc = embeddedQueryDoc;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public EmbeddedQueryComponentSettings build() {
            return new EmbeddedQueryComponentSettings(
                    reference,
                    queryRef,
                    automate,
                    selectionQuery,
                    lastQueryKey,
                    lastQueryNode,
                    showTable,
                    queryTablePreferences,
                    selectionFilter,
                    embeddedQueryDoc);
        }
    }
}
