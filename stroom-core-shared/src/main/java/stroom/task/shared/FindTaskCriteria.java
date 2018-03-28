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

import stroom.entity.shared.HasIsConstrained;
import stroom.util.shared.SharedObject;
import stroom.util.shared.Task;
import stroom.util.shared.TaskId;
import stroom.util.shared.UserTokenUtil;

import java.util.HashSet;
import java.util.Set;

public class FindTaskCriteria implements SharedObject, HasIsConstrained {
    private static final long serialVersionUID = 2759048534848720682L;

    private Set<TaskId> ancestorIdSet;
    private Set<TaskId> idSet;
    private String sessionId;

    public FindTaskCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public void addAncestorId(final TaskId ancestorId) {
        if (ancestorIdSet == null) {
            ancestorIdSet = new HashSet<>();
        }
        ancestorIdSet.add(ancestorId);
    }

    public void addId(final TaskId id) {
        if (idSet == null) {
            idSet = new HashSet<>();
        }
        idSet.add(id);
        addAncestorId(id);
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public boolean isConstrained() {
        return (ancestorIdSet != null && ancestorIdSet.size() > 0) || (idSet != null && idSet.size() > 0)
                || sessionId != null;
    }

    public boolean isMatch(final Task<?> task) {
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
        if (sessionId != null) {
            if (sessionId.equals(UserTokenUtil.getSessionId(task.getUserToken()))) {
                return true;
            }
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
        if (sessionId != null) {
            sb.append("Session Id: ");
            sb.append(sessionId);
        }

        return sb.toString();
    }
}
