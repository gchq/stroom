/*
 * Copyright 2016 Crown Copyright
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

package stroom.query.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.query.api.v2.TimeRange;
import stroom.svg.shared.SvgImage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description(
        "A Query Document defines a {{< glossary \"StroomQL\" >}} query and is used to execute that query " +
                "and view its results.\n" +
                "A Query can query main types of data source including " +
                "[Views]({{< relref \"#view\" >}}), [Lucene Indexes]({{< relref \"#lucene-index\" >}}), and " +
                "{{< glossary \"searchable\" \"Searchables\" >}}."
)
@JsonPropertyOrder({
        "uuid",
        "name",
        "uniqueName",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "timeRange",
        "query",
        "queryTablePreferences"})
@JsonInclude(Include.NON_NULL)
public class QueryDoc extends AbstractDoc {

    public static final String DOCUMENT_TYPE = "Query";
    public static final SvgImage ICON = SvgImage.DOCUMENT_QUERY;

    @JsonProperty
    private String description;
    @JsonProperty
    private TimeRange timeRange;
    @JsonProperty
    private String query;
    @JsonProperty
    private QueryTablePreferences queryTablePreferences;

    public QueryDoc() {
    }

    @JsonCreator
    public QueryDoc(@JsonProperty("uuid") final String uuid,
                    @JsonProperty("name") final String name,
                    @JsonProperty("uniqueName") final String uniqueName,
                    @JsonProperty("version") final String version,
                    @JsonProperty("createTimeMs") final Long createTimeMs,
                    @JsonProperty("updateTimeMs") final Long updateTimeMs,
                    @JsonProperty("createUser") final String createUser,
                    @JsonProperty("updateUser") final String updateUser,
                    @JsonProperty("description") final String description,
                    @JsonProperty("timeRange") TimeRange timeRange,
                    @JsonProperty("query") final String query,
                    @JsonProperty("queryTablePreferences") final QueryTablePreferences queryTablePreferences) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.timeRange = timeRange;
        this.query = query;
        this.queryTablePreferences = queryTablePreferences;
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return DOCUMENT_TYPE;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(final TimeRange timeRange) {
        this.timeRange = timeRange;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(final String query) {
        this.query = query;
    }

    public QueryTablePreferences getQueryTablePreferences() {
        return queryTablePreferences;
    }

    public void setQueryTablePreferences(final QueryTablePreferences queryTablePreferences) {
        this.queryTablePreferences = queryTablePreferences;
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
        final QueryDoc that = (QueryDoc) o;
        return Objects.equals(query, that.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), query);
    }
}
