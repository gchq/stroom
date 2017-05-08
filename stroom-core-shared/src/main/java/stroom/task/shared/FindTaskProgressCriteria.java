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

import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.OrderBy;
import stroom.util.shared.CompareUtil;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class FindTaskProgressCriteria extends FindNamedEntityCriteria implements Comparator<TaskProgress> {
    private static final long serialVersionUID = 2014515855795611224L;

    public static final OrderBy ORDER_BY_USER = new OrderBy("User");
    public static final OrderBy ORDER_BY_SUBMIT_TIME = new OrderBy("Submit Time");
    public static final OrderBy ORDER_BY_AGE = new OrderBy("Age");

    private FindTaskCriteria findTaskCriteria = new FindTaskCriteria();
    private String sessionId;
    private Set<TaskProgress> expandedTasks;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public FindTaskCriteria getFindTaskCriteria() {
        return findTaskCriteria;
    }

    public boolean matches(final TaskProgress taskProgress) {
        boolean match = true;
        if (sessionId != null) {
            if (!sessionId.equals(taskProgress.getSessionId())) {
                match = false;
            }
        }
        return match;
    }

    @Override
    public int compare(final TaskProgress o1, final TaskProgress o2) {
        int compare = 0;
        if (ORDER_BY_NAME.equals(getOrderBy())) {
            compare = CompareUtil.compareString(o1.getTaskName(), o2.getTaskName());
        } else if (ORDER_BY_USER.equals(getOrderBy())) {
            compare = CompareUtil.compareString(o1.getUserName(), o2.getUserName());
        } else if (ORDER_BY_SUBMIT_TIME.equals(getOrderBy())) {
            compare = CompareUtil.compareLong(o1.getSubmitTimeMs(), o2.getSubmitTimeMs());
        } else if (ORDER_BY_AGE.equals(getOrderBy())) {
            compare = CompareUtil.compareLong(o1.getSubmitTimeMs(), o2.getSubmitTimeMs());
        }
        if (getOrderByDirection() == OrderByDirection.DESCENDING) {
            compare = compare * -1;
        }
        return compare;
    }

    public void setExpanded(final TaskProgress taskProgress, final boolean expanded) {
        if (expanded) {
            if (expandedTasks == null) {
                expandedTasks = new HashSet<>();
            }
            expandedTasks.add(taskProgress);
        } else {
            if (expandedTasks != null) {
                expandedTasks.remove(taskProgress);
                if (expandedTasks.size() == 0) {
                    expandedTasks = null;
                }
            }
        }
    }

    public boolean isExpanded(final TaskProgress taskProgress) {
        if (expandedTasks != null) {
            return expandedTasks.contains(taskProgress);
        }
        return false;
    }
}
