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

import stroom.util.shared.HasIsConstrained;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FindTaskCriteria implements HasIsConstrained {

    @JsonProperty
    private String sessionId;
    @JsonProperty
    private Set<TaskId> ancestorIdSet;
    @JsonProperty
    private Set<TaskId> idSet;

    public FindTaskCriteria() {
    }

    @JsonCreator
    public FindTaskCriteria(@JsonProperty("sessionId") final String sessionId,
                            @JsonProperty("ancestorIdSet") final Set<TaskId> ancestorIdSet,
                            @JsonProperty("idSet") final Set<TaskId> idSet) {
        this.sessionId = sessionId;
        this.ancestorIdSet = ancestorIdSet;
        this.idSet = idSet;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public Set<TaskId> getAncestorIdSet() {
        return ancestorIdSet;
    }

    public void setAncestorIdSet(final Set<TaskId> ancestorIdSet) {
        this.ancestorIdSet = ancestorIdSet;
    }

    public void addAncestorId(final TaskId ancestorId) {
        if (ancestorIdSet == null) {
            ancestorIdSet = new HashSet<>();
        }
        ancestorIdSet.add(ancestorId);
    }

    public Set<TaskId> getIdSet() {
        return idSet;
    }

    public void setIdSet(final Set<TaskId> idSet) {
        this.idSet = idSet;
    }

    public void addId(final TaskId id) {
        if (idSet == null) {
            idSet = new HashSet<>();
        }
        idSet.add(id);
        addAncestorId(id);
    }

    @Override
    @JsonIgnore
    public boolean isConstrained() {
        return (ancestorIdSet != null && ancestorIdSet.size() > 0) || (idSet != null && idSet.size() > 0);
    }

    public boolean isMatch(final TaskId taskId, final String sessionId) {
        return (isAncestorIdSetMatch(taskId) && isIdSetMatch(taskId)) || isSessionIdMatch(sessionId);
    }

    private boolean isAncestorIdSetMatch(final TaskId taskId) {
        if (ancestorIdSet == null) {
            return true;
        }
        for (final TaskId ancestorId : ancestorIdSet) {
            if (taskId.isOrHasAncestor(ancestorId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIdSetMatch(final TaskId taskId) {
        if (idSet == null) {
            return true;
        }
        return idSet.contains(taskId);
    }

    private boolean isSessionIdMatch(final String sessionId) {
        if (this.sessionId != null) {
            return this.sessionId.equals(sessionId);
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (ancestorIdSet != null && ancestorIdSet.size() > 0) {
            sb.append("Ancestor Id: ");
            for (final TaskId ancestorId : ancestorIdSet) {
                sb.append(ancestorId);
                sb.append(", ");
            }
        }
        if (idSet != null && idSet.size() > 0) {
            sb.append("Id: ");
            for (final TaskId id : idSet) {
                sb.append(id);
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}
