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

package stroom.search.impl;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequestSource;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.task.shared.TaskId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class NodeSearchTask {

    @JsonProperty
    private final NodeSearchTaskType type;
    @JsonProperty
    private final TaskId sourceTaskId;
    @JsonProperty
    private final String taskName;
    @JsonProperty
    private final SearchRequestSource searchRequestSource;
    @JsonProperty
    private final QueryKey key;
    @JsonProperty
    private final Query query;
    @JsonProperty
    private final List<CoprocessorSettings> settings;
    @JsonProperty
    private final DateTimeSettings dateTimeSettings;
    @JsonProperty
    private final List<Long> shards; // Specific to Lucene.

    @JsonCreator
    public NodeSearchTask(@JsonProperty("type") final NodeSearchTaskType type,
                          @JsonProperty("sourceTaskId") final TaskId sourceTaskId,
                          @JsonProperty("taskName") final String taskName,
                          @JsonProperty("searchRequestSource") final SearchRequestSource searchRequestSource,
                          @JsonProperty("key") final QueryKey key,
                          @JsonProperty("query") final Query query,
                          @JsonProperty("settings") final List<CoprocessorSettings> settings,
                          @JsonProperty("dateTimeSettings") final DateTimeSettings dateTimeSettings,
                          @JsonProperty("shards") final List<Long> shards) {
        this.type = type;
        this.sourceTaskId = sourceTaskId;
        this.taskName = taskName;
        this.searchRequestSource = searchRequestSource;
        this.key = key;
        this.query = query;
        this.settings = settings;
        this.dateTimeSettings = dateTimeSettings;
        this.shards = shards;
    }

    public NodeSearchTaskType getType() {
        return type;
    }

    public TaskId getSourceTaskId() {
        return sourceTaskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public SearchRequestSource getSearchRequestSource() {
        return searchRequestSource;
    }

    public QueryKey getKey() {
        return key;
    }

    public Query getQuery() {
        return query;
    }

    public List<CoprocessorSettings> getSettings() {
        return settings;
    }

    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    public List<Long> getShards() {
        return shards;
    }
}
