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

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.Sort;
import stroom.util.shared.Sort.Direction;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class FindTaskProgressCriteria extends BaseCriteria implements Comparator<TaskProgress> {
    private static final long serialVersionUID = 2014515855795611224L;

    public static final String FIELD_NODE = "Node";
    public static final String FIELD_NAME = "Name";
    public static final String FIELD_USER = "User";
    public static final String FIELD_SUBMIT_TIME = "Submit Time";
    public static final String FIELD_AGE = "Age";
    public static final String FIELD_INFO = "Info";

    private Set<TaskProgress> expandedTasks;
    private String nameFilter;

    public String getNameFilter() {
        return nameFilter;
    }

    public void setNameFilter(final String nameFilter) {
        this.nameFilter = nameFilter;
    }

    @Override
    public int compare(final TaskProgress o1, final TaskProgress o2) {
        if (getSortList() != null) {
            for (final Sort sort : getSortList()) {
                final String field = sort.getField();

                int compare = 0;
                switch (field) {
                    case FIELD_NAME:
                        compare = CompareUtil.compareString(o1.getTaskName(), o2.getTaskName());
                        break;
                    case FIELD_USER:
                        compare = CompareUtil.compareString(o1.getUserName(), o2.getUserName());
                        break;
                    case FIELD_SUBMIT_TIME:
                        compare = CompareUtil.compareLong(o1.getSubmitTimeMs(), o2.getSubmitTimeMs());
                        break;
                    case FIELD_AGE:
                        compare = CompareUtil.compareLong(o1.getAgeMs(), o2.getAgeMs());
                        break;
                    case FIELD_INFO:
                        compare = CompareUtil.compareString(o1.getTaskInfo(), o2.getTaskInfo());
                        break;
                    case FIELD_NODE:
                        compare = CompareUtil.compareString(o1.getNodeName(), o2.getNodeName());
                        break;
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
        if (this.getSortList().isEmpty()) {
            Sort defaultSort = new Sort(FindTaskProgressCriteria.FIELD_SUBMIT_TIME, Direction.ASCENDING, true);
            this.getSortList().add(defaultSort);
        } else {
            for (Sort sort : this.getSortList()) {
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

    public boolean isMatch(final String sessionId) {
        return true;
    }
}
