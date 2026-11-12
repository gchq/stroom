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
    // The node that runs the search, from which a node fetches the search's join tables, if any.
    @JsonProperty
    private final String sourceNode;

    /// Creates a node search task with no source node, as for a search with no joins.
    ///
    /// @param type                The kind of node search.
    /// @param sourceTaskId        The task that runs the search on the source node.
    /// @param taskName            The name of the node search's task.
    /// @param searchRequestSource Where the search came from.
    /// @param key                 The key of the search.
    /// @param query               The search's query.
    /// @param settings            The search's coprocessor settings.
    /// @param dateTimeSettings    The search's date and time settings.
    /// @param shards              The index shards to search on the node, for a Lucene search.
    public NodeSearchTask(final NodeSearchTaskType type,
                          final TaskId sourceTaskId,
                          final String taskName,
                          final SearchRequestSource searchRequestSource,
                          final QueryKey key,
                          final Query query,
                          final List<CoprocessorSettings> settings,
                          final DateTimeSettings dateTimeSettings,
                          final List<Long> shards) {
        this(type, sourceTaskId, taskName, searchRequestSource, key, query, settings, dateTimeSettings, shards, null);
    }

    @JsonCreator
    public NodeSearchTask(@JsonProperty("type") final NodeSearchTaskType type,
                          @JsonProperty("sourceTaskId") final TaskId sourceTaskId,
                          @JsonProperty("taskName") final String taskName,
                          @JsonProperty("searchRequestSource") final SearchRequestSource searchRequestSource,
                          @JsonProperty("key") final QueryKey key,
                          @JsonProperty("query") final Query query,
                          @JsonProperty("settings") final List<CoprocessorSettings> settings,
                          @JsonProperty("dateTimeSettings") final DateTimeSettings dateTimeSettings,
                          @JsonProperty("shards") final List<Long> shards,
                          @JsonProperty("sourceNode") final String sourceNode) {
        this.type = type;
        this.sourceTaskId = sourceTaskId;
        this.taskName = taskName;
        this.searchRequestSource = searchRequestSource;
        this.key = key;
        this.query = query;
        this.settings = settings;
        this.dateTimeSettings = dateTimeSettings;
        this.shards = shards;
        this.sourceNode = sourceNode;
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

    /// Gets the node that runs the search, from which the node searching this task fetches the
    /// search's join tables, if its query has joins.
    ///
    /// @return The node's name, or `null` if not given.
    public String getSourceNode() {
        return sourceNode;
    }

    /// Copies this task with the node that runs the search.
    ///
    /// @param sourceNode The node's name.
    /// @return The copy.
    public NodeSearchTask withSourceNode(final String sourceNode) {
        return new NodeSearchTask(
                type,
                sourceTaskId,
                taskName,
                searchRequestSource,
                key,
                query,
                settings,
                dateTimeSettings,
                shards,
                sourceNode);
    }
}
