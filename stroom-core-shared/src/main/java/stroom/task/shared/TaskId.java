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

package stroom.task.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class TaskId {

    @JsonProperty
    private final String id;
    @JsonProperty
    private TaskId parentId;

    /**
     * Do not use this constructor directly, instead please use TaskIdFactory.
     */
    @JsonCreator
    public TaskId(@JsonProperty("id") final String id,
                  @JsonProperty("parentId") final TaskId parentId) {
        Objects.requireNonNull(id);
        this.id = id;
        this.parentId = parentId;
    }

    public static TaskId createTestTaskId() {
        return new TaskId("test", null);
    }

    public String getId() {
        return id;
    }

    public TaskId getParentId() {
        return parentId;
    }

    public void setParentId(final TaskId parentId) {
        this.parentId = parentId;
    }

    public boolean isOrHasAncestor(final TaskId ancestorId) {
        return recursiveEquals(this, ancestorId);
    }

    private boolean recursiveEquals(final TaskId id, final TaskId ancestorId) {
        if (id == null || ancestorId == null) {
            return false;
        } else if (id.equals(ancestorId)) {
            return true;
        }

        return recursiveEquals(id.getParentId(), ancestorId);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof TaskId)) {
            return false;
        }

        final TaskId taskId = (TaskId) o;
        return id.equals(taskId.id);
    }

    @Override
    public String toString() {
        return "{" + id + "}";
    }
}
