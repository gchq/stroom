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

package stroom.task.server;

import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskProgress;
import stroom.util.shared.Task;

import java.util.Comparator;

class ExtendedFindTaskProgressCriteria extends FindTaskProgressCriteria implements Comparator<TaskProgress> {
    private static final long serialVersionUID = 2014515855795611224L;

    private String sessionId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public boolean isMatch(final Task<?> task) {
        if (!super.isMatch(task)) {
            return false;
        }
        return sessionId == null || sessionId.equals(task.getUserIdentity().getSessionId());
    }
}
