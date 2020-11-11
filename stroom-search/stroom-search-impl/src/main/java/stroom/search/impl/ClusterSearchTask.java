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

package stroom.search.impl;

import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.task.shared.TaskId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

@JsonInclude(Include.NON_NULL)
public class ClusterSearchTask implements Serializable {
    private static final long serialVersionUID = -1305243739417365803L;

    @JsonProperty
    private final TaskId sourceTaskId;
    @JsonProperty
    private final String taskName;
    @JsonProperty
    private final QueryKey key;
    @JsonProperty
    private final Query query;
    @JsonProperty
    private final List<Long> shards;
    @JsonProperty
    private final String[] storedFields;
    @JsonProperty
    private final List<CoprocessorSettings> settings;
    @JsonProperty
    private final String dateTimeLocale;
    @JsonProperty
    private final long now;

    @JsonCreator
    public ClusterSearchTask(@JsonProperty("sourceTaskId") final TaskId sourceTaskId,
                             @JsonProperty("taskName") final String taskName,
                             @JsonProperty("key") final QueryKey key,
                             @JsonProperty("query") final Query query,
                             @JsonProperty("shards") final List<Long> shards,
                             @JsonProperty("storedFields") final String[] storedFields,
                             @JsonProperty("settings") final List<CoprocessorSettings> settings,
                             @JsonProperty("dateTimeLocale") final String dateTimeLocale,
                             @JsonProperty("now") final long now) {
        this.sourceTaskId = sourceTaskId;
        this.taskName = taskName;
        this.key = key;
        this.query = query;
        this.shards = shards;
        this.storedFields = storedFields;
        this.settings = settings;
        this.dateTimeLocale = dateTimeLocale;
        this.now = now;
    }

    public TaskId getSourceTaskId() {
        return sourceTaskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public QueryKey getKey() {
        return key;
    }

    public Query getQuery() {
        return query;
    }

    public List<Long> getShards() {
        return shards;
    }

    public String[] getStoredFields() {
        return storedFields;
    }

    public List<CoprocessorSettings> getSettings() {
        return settings;
    }

    public String getDateTimeLocale() {
        return dateTimeLocale;
    }

    public long getNow() {
        return now;
    }
}
