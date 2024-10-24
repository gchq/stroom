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

package stroom.dashboard.shared;

import stroom.docref.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.TableSettings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "queryRef",
        "automate",
        "selectionHandlers",
        "lastQueryKey",
        "lastQueryNode",
        "showTable"
})
@JsonInclude(Include.NON_NULL)
public class EmbeddedQueryComponentSettings extends AbstractQueryComponentSettings {

    @JsonProperty
    private final DocRef queryRef;
    @JsonProperty
    private final Boolean showTable;

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public EmbeddedQueryComponentSettings(@JsonProperty("queryRef") final DocRef queryRef,
                                          @JsonProperty("automate") final Automate automate,
                                          @JsonProperty("selectionHandlers") final List<ComponentSelectionHandler> selectionHandlers,
                                          @JsonProperty("lastQueryKey") final QueryKey lastQueryKey,
                                          @JsonProperty("lastQueryNode") final String lastQueryNode,
                                          @JsonProperty("showTable") final Boolean showTable) {
        super(automate, selectionHandlers, lastQueryKey, lastQueryNode);
        this.queryRef = queryRef;
        this.showTable = showTable;
    }

    public DocRef getQueryRef() {
        return queryRef;
    }

    public Boolean getShowTable() {
        return showTable;
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
        return Objects.equals(queryRef, that.queryRef) &&
                Objects.equals(showTable, that.showTable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), queryRef, showTable);
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
            extends AbstractBuilder<EmbeddedQueryComponentSettings, EmbeddedQueryComponentSettings.Builder> {

        private DocRef queryRef;
        private Boolean showTable;

        private Builder() {
            super();
        }

        private Builder(final EmbeddedQueryComponentSettings settings) {
            super(settings);
            this.queryRef = settings.queryRef;
            this.showTable = settings.showTable;
        }

        public Builder queryRef(final DocRef queryRef) {
            this.queryRef = queryRef;
            return self();
        }

        public Builder showTable(final Boolean showTable) {
            this.showTable = showTable;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public EmbeddedQueryComponentSettings build() {
            return new EmbeddedQueryComponentSettings(
                    queryRef,
                    automate,
                    selectionHandlers,
                    lastQueryKey,
                    lastQueryNode,
                    showTable);
        }
    }
}
