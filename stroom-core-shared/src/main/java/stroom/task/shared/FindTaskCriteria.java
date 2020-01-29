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

package stroom.task.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import stroom.docref.SharedObject;
import stroom.util.shared.HasIsConstrained;

import java.util.HashSet;
import java.util.Set;

public class FindTaskCriteria implements SharedObject, HasIsConstrained {
    private static final long serialVersionUID = 2759048534848720682L;

    private String sessionId;
    private Set<TaskId> ancestorIdSet;
    private Set<TaskId> idSet;

    public FindTaskCriteria() {
        // Default constructor necessary for GWT serialisation.
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

    @JsonIgnore
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

    @JsonIgnore
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

    @JsonIgnore
    public boolean isMatch(final Task<?> task, final String sessionId) {
        if (ancestorIdSet != null && ancestorIdSet.size() > 0) {
            for (final TaskId ancestorId : ancestorIdSet) {
                if (task.getId().isOrHasAncestor(ancestorId)) {
                    return true;
                }
            }
        }
        if (idSet != null && idSet.size() > 0) {
            if (idSet.contains(task.getId())) {
                return true;
            }
        }

        return this.sessionId == null || this.sessionId.equals(sessionId);
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
