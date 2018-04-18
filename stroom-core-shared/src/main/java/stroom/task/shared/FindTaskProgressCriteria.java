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
import stroom.entity.shared.Sort;
import stroom.entity.shared.Sort.Direction;
import stroom.util.shared.CompareUtil;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class FindTaskProgressCriteria extends FindNamedEntityCriteria implements Comparator<TaskProgress> {
    public static final String FIELD_NODE = "Node";
    public static final String FIELD_NAME = "Name";
    public static final String FIELD_USER = "User";
    public static final String FIELD_SUBMIT_TIME = "Submit Time";
    public static final String FIELD_AGE = "Age";
    public static final String FIELD_INFO = "Info";

    private static final long serialVersionUID = 2014515855795611224L;
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
        if (getSortList() != null) {
            for (final Sort sort : getSortList()) {
                final String field = sort.getField();

                int compare = 0;
                if (FIELD_NAME.equals(field)) {
                    compare = CompareUtil.compareString(o1.getTaskName(), o2.getTaskName());
                } else if (FIELD_USER.equals(field)) {
                    compare = CompareUtil.compareString(o1.getUserName(), o2.getUserName());
                } else if (FIELD_SUBMIT_TIME.equals(field)) {
                    compare = CompareUtil.compareLong(o1.getSubmitTimeMs(), o2.getSubmitTimeMs());
                } else if (FIELD_AGE.equals(field)) {
                    compare = CompareUtil.compareLong(o1.getSubmitTimeMs(), o2.getSubmitTimeMs());
                }
                if (Direction.DESCENDING.equals(sort.getDirection())) {
                    compare = compare * -1;
                }

                if (compare != 0) {
                    return compare;
                }
            }
        }

        return 0;
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

    public void validateSortField() {
        if(this.getSortList().isEmpty()){
            Sort defaultSort = new Sort(FindTaskProgressCriteria.FIELD_SUBMIT_TIME, Direction.ASCENDING, true);
            this.getSortList().add(defaultSort);
            return;
        }

        for(Sort sort : this.getSortList()) {
            if (!Arrays.asList(
                    FindTaskProgressCriteria.FIELD_AGE,
                    FindTaskProgressCriteria.FIELD_INFO,
                    FindTaskProgressCriteria.FIELD_NAME,
                    FindTaskProgressCriteria.FIELD_NODE,
                    FindTaskProgressCriteria.FIELD_SUBMIT_TIME,
                    FindTaskProgressCriteria.FIELD_USER).contains(sort.getField())) {
                throw new IllegalArgumentException(
                        "A sort field of " + sort.getField() + " is not valid! It must be one of FindTaskProgressCriteria.FIELD_xxx");
            }
        }
    }
}
