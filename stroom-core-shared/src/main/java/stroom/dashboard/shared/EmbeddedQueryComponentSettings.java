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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "queryRef",
        "automate",
        "selectionHandlers",
        "lastQueryKey",
        "lastQueryNode"
})
@JsonInclude(Include.NON_NULL)
public class EmbeddedQueryComponentSettings implements ComponentSettings {

    @JsonProperty
    private final DocRef queryRef;
    @JsonProperty
    private final Automate automate;
    @JsonProperty
    private final List<ComponentSelectionHandler> selectionHandlers;
    @JsonProperty
    private final QueryKey lastQueryKey;
    @JsonProperty
    private final String lastQueryNode;

    @SuppressWarnings("checkstyle:LineLength")
    @JsonCreator
    public EmbeddedQueryComponentSettings(@JsonProperty("queryRef") final DocRef queryRef,
                                          @JsonProperty("automate") final Automate automate,
                                          @JsonProperty("selectionHandlers") final List<ComponentSelectionHandler> selectionHandlers,
                                          @JsonProperty("lastQueryKey") final QueryKey lastQueryKey,
                                          @JsonProperty("lastQueryNode") final String lastQueryNode) {
        this.queryRef = queryRef;
        this.automate = automate;
        this.selectionHandlers = selectionHandlers;
        this.lastQueryKey = lastQueryKey;
        this.lastQueryNode = lastQueryNode;
    }

    public DocRef getQueryRef() {
        return queryRef;
    }

    public Automate getAutomate() {
        return automate;
    }

    public List<ComponentSelectionHandler> getSelectionHandlers() {
        return selectionHandlers;
    }

    public QueryKey getLastQueryKey() {
        return lastQueryKey;
    }

    public String getLastQueryNode() {
        return lastQueryNode;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EmbeddedQueryComponentSettings that = (EmbeddedQueryComponentSettings) o;
        return Objects.equals(queryRef, that.queryRef) &&
                Objects.equals(automate, that.automate) &&
                Objects.equals(selectionHandlers, that.selectionHandlers) &&
                Objects.equals(lastQueryKey, that.lastQueryKey) &&
                Objects.equals(lastQueryNode, that.lastQueryNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryRef, automate, selectionHandlers, lastQueryKey, lastQueryNode);
    }

    @Override
    public String toString() {
        return "EmbeddedQueryComponentSettings{" +
                "queryRef=" + queryRef +
                ", automate=" + automate +
                ", selectionHandlers=" + selectionHandlers +
                ", lastQueryKey=" + lastQueryKey +
                ", lastQueryNode='" + lastQueryNode + '\'' +
                '}';
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
    public static final class Builder implements ComponentSettings.Builder {

        private DocRef queryRef;
        private Automate automate;
        private List<ComponentSelectionHandler> selectionHandlers;
        private QueryKey lastQueryKey;
        private String lastQueryNode;

        private Builder() {
        }

        private Builder(final EmbeddedQueryComponentSettings settings) {
            this.queryRef = settings.queryRef;
            this.automate = settings.automate == null
                    ? null
                    : settings.automate.copy().build();
            this.selectionHandlers = settings.selectionHandlers == null
                    ? null
                    : new ArrayList<>(settings.selectionHandlers);
            this.lastQueryKey = settings.lastQueryKey;
            this.lastQueryNode = settings.lastQueryNode;
        }

        public Builder queryRef(final DocRef queryRef) {
            this.queryRef = queryRef;
            return this;
        }


        public Builder automate(final Automate automate) {
            this.automate = automate;
            return this;
        }

        public Builder selectionHandlers(final List<ComponentSelectionHandler> selectionHandlers) {
            this.selectionHandlers = selectionHandlers;
            return this;
        }

        public Builder addSelectionHandler(final ComponentSelectionHandler selectionHandler) {
            if (this.selectionHandlers == null) {
                this.selectionHandlers = new ArrayList<>();
            }
            this.selectionHandlers.add(selectionHandler);
            return this;
        }

        public Builder lastQueryKey(final QueryKey lastQueryKey) {
            this.lastQueryKey = lastQueryKey;
            return this;
        }

        public Builder lastQueryNode(final String lastQueryNode) {
            this.lastQueryNode = lastQueryNode;
            return this;
        }

        @Override
        public EmbeddedQueryComponentSettings build() {
            return new EmbeddedQueryComponentSettings(
                    queryRef,
                    automate,
                    selectionHandlers,
                    lastQueryKey,
                    lastQueryNode);
        }
    }
}
