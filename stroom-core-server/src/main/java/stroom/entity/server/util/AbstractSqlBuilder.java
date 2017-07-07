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

package stroom.entity.server.util;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.IncludeExcludeEntityIdSet;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.Period;
import stroom.entity.shared.Range;
import stroom.entity.shared.Sort;
import stroom.entity.shared.Sort.Direction;
import stroom.entity.shared.StringCriteria;

import javax.persistence.Query;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractSqlBuilder extends CoreSqlBuilder {
    AbstractSqlBuilder() {
        super();
    }

    AbstractSqlBuilder(final String prepared, final Object... args) {
        super(prepared, args);
    }

    /**
     * Utility to restrict the results in the query.
     */
    void applyRestrictionCriteria(final Query query, final BaseCriteria criteria) {
        if (criteria.getPageRequest() != null) {
            final PageRequest pageRequest = criteria.getPageRequest();
            if (pageRequest.getOffset() != null) {
                query.setFirstResult(pageRequest.getOffset().intValue());
            }
            if (pageRequest.getLength() != null) {
                // Add one so we know we have more results in the next page
                query.setMaxResults(pageRequest.getLength() + 1);
            }
        }
    }

    /**
     * Utility to restrict the results in the query.
     */
    public void applyRestrictionCriteria(final BaseCriteria criteria) {
        if (criteria.getPageRequest() != null) {
            final PageRequest pageRequest = criteria.getPageRequest();
            if (pageRequest.getOffset() != null && pageRequest.getOffset() > 0 && pageRequest.getLength() != null) {
                append(" LIMIT ");
                arg(pageRequest.getLength().longValue() + 1);
                append(" OFFSET ");
                arg(pageRequest.getOffset());
            } else if (pageRequest.getLength() != null) {
                append(" LIMIT ");
                arg(pageRequest.getLength().longValue() + 1);
            }
            append(" ");
        }
    }

    public void appendOrderBy(final Map<String, String> fieldMap, final BaseCriteria criteria, final String prefix) {
        final List<Sort> sortList = criteria.getSortList();
        if (sortList != null) {
            final Set<String> uniqueFields = new HashSet<>();
            boolean doneOne = false;
            for (final Sort sort : sortList) {
                final String field = sort.getField();

                if (!uniqueFields.add(field)) {
                    throw new IllegalArgumentException("Attempt to sort by the same field more than once");
                }

                // This should not be null.
                if (field == null) {
                    throw new NullPointerException("Null value for field in sort");
                }

                final String val = fieldMap.get(field);
                // This could be null if we aren't allowing sorting on this field.
                if (val != null) {
                    if (!doneOne) {
                        append(" ORDER BY");
                    } else {
                        append(",");
                    }
                    doneOne = true;

                    if (sort.isIgnoreCase()) {
                        append(" UPPER(");
                        if (prefix != null) {
                            append(prefix);
                            append(".");
                        }
                    } else {
                        append(" ");
                        if (prefix != null) {
                            append(prefix);
                            append(".");
                        }
                    }

                    append(val);

                    if (sort.isIgnoreCase()) {
                        append(")");
                    }
                    if (Direction.DESCENDING.equals(sort.getDirection())) {
                        append(" DESC");
                    }
                }
            }
        }
    }

    /**
     * <p>
     * Add a set query like A in ('A','B').
     * </p>
     */
    public void appendCriteriaSetQuery(final String fieldOrEntity,
                                       final CriteriaSet<Long> set) {
        if (set != null && set.isConstrained()) {
            append(" AND");
            internalAppendCriteriaSetQuery(fieldOrEntity, set);
        }
    }

    private void internalAppendCriteriaSetQuery(final String fieldOrEntity,
                                                final CriteriaSet<Long> set) {
        if (set.isMatchNothing()) {
            // Force the query to return nothing if the set is empty.
            append(" 1=2");

        } else if (Boolean.TRUE.equals(set.getMatchNull())) {
            append(" ");

            if (set.size() > 0) {
                append("(");
                appendCriteriaSet(fieldOrEntity, set);
                append(" OR ");
                appendNull(fieldOrEntity);
                append(")");

            } else {
                appendNull(fieldOrEntity);
            }

        } else {
            append(" ");
            appendCriteriaSet(fieldOrEntity, set);
        }
    }

    abstract void appendCriteriaSet(String fieldOrEntity, CriteriaSet<Long> set);

    /**
     * <p>
     * Add a set query like A in ('A','B').
     * </p>
     */
    public <T extends HasPrimitiveValue> void appendPrimitiveValueSetQuery(final String fieldOrEntity,
                                                                           final CriteriaSet<T> set) {
        if (set != null && set.isConstrained()) {
            append(" AND");
            internalAppendPrimitiveValueSetQuery(fieldOrEntity, set);
        }
    }

    private <T extends HasPrimitiveValue> void internalAppendPrimitiveValueSetQuery(final String fieldOrEntity,
                                                                                    final CriteriaSet<T> set) {
        if (set.isMatchNothing()) {
            // Force the query to return nothing if the set is empty.
            append(" 1=2");

        } else if (Boolean.TRUE.equals(set.getMatchNull())) {
            append(" ");

            if (set.size() > 0) {
                append("(");
                appendPrimitiveValueSet(fieldOrEntity, set);
                append(" OR ");
                appendNull(fieldOrEntity);
                append(")");

            } else {
                appendNull(fieldOrEntity);
            }

        } else {
            append(" ");
            appendPrimitiveValueSet(fieldOrEntity, set);
        }
    }

    abstract <T extends HasPrimitiveValue> void appendPrimitiveValueSet(String fieldOrEntity, CriteriaSet<T> set);

    /**
     * <p>
     * Add a set query like A in ('A','B').
     * </p>
     */
    public void appendEntityIdSetQuery(final String fieldOrEntity,
                                       final EntityIdSet<?> set) {
        if (set != null && set.isConstrained()) {
            append(" AND");
            internalAppendEntityIdSetQuery(fieldOrEntity, set);
        }
    }

    private void internalAppendEntityIdSetQuery(final String fieldOrEntity,
                                                final EntityIdSet<?> set) {
        if (set.isMatchNothing()) {
            // Force the query to return nothing if the set is empty.
            append(" 1=2");

        } else if (Boolean.TRUE.equals(set.getMatchNull())) {
            append(" ");

            if (set.size() > 0) {
                append("(");
                appendEntityIdSet(fieldOrEntity, set);
                append(" OR ");
                appendNull(fieldOrEntity);
                append(")");

            } else {
                appendNull(fieldOrEntity);
            }

        } else {
            append(" ");
            appendEntityIdSet(fieldOrEntity, set);
        }
    }

    abstract void appendEntityIdSet(String fieldOrEntity, EntityIdSet<?> set);

    /**
     * <p>
     * Add a set query like A in ('A','B').
     * </p>
     */
    public void appendIncludeExcludeSetQuery(final String fieldOrEntity, final IncludeExcludeEntityIdSet<?> includeExcludeEntityIdSet) {
        if (includeExcludeEntityIdSet != null) {
            if (includeExcludeEntityIdSet.getInclude() != null
                    && includeExcludeEntityIdSet.getInclude().isConstrained()) {
                append(" AND");
                internalAppendEntityIdSetQuery(fieldOrEntity, includeExcludeEntityIdSet.getInclude());
            }
            if (includeExcludeEntityIdSet.getExclude() != null
                    && includeExcludeEntityIdSet.getExclude().isConstrained()) {
                append(" AND NOT(");
                internalAppendEntityIdSetQuery(fieldOrEntity, includeExcludeEntityIdSet.getExclude());
                append(")");
            }
        }
    }

    private void appendNull(final String fieldOrEntity) {
        append(fieldOrEntity);
        append(" IS NULL");
    }

    /**
     * <p>
     * Add a value query like A = ?.
     * </p>
     */
    public void appendValueQuery(final String field, final Object value) {
        if (value != null) {
            append(" AND ");
            append(field);
            append(" = ");
            if (value instanceof HasPrimitiveValue) {
                arg(((HasPrimitiveValue) value).getPrimitiveValue());
            } else {
                arg(value);
            }
        }
    }

    public void appendValueQuery(final String field, final StringCriteria value) {
        if (value != null) {
            append(" AND ");
            if (!appendValue(field, value)) {
                // Trim off AND.
                setLength(length() - 5);
            }
        }
    }

    public void appendValuesQuery(final String field, final List<StringCriteria> values) {
        if (values != null && values.size() > 0) {
            append(" AND (");

            boolean added = false;
            for (final StringCriteria value : values) {
                if (appendValue(field, value)) {
                    append(" OR ");
                    added = true;
                }
            }

            if (added) {
                // Trim off last OR.
                setLength(length() - 4);
                append(")");
            } else {
                // Trim off AND.
                setLength(length() - 6);
            }
        }
    }

    private boolean appendValue(final String field, final StringCriteria value) {
        if (value != null) {
            if (value.getMatchNull() != null) {
                if (Boolean.TRUE.equals(value.getMatchNull())) {
                    append(field);
                    append(" IS NULL");
                } else if (Boolean.FALSE.equals(value.getMatchNull())) {
                    append(field);
                    append(" IS NOT NULL");
                }
                return true;
            } else if (value.getString() != null) {
                if (value.getMatchStyle() == null) {
                    append(field);
                    append(" = ");
                    arg(value.getString());
                    return true;
                } else {
                    if (value.isCaseInsensitive()) {
                        append("UPPER(");
                    }
                    append(field);
                    if (value.isCaseInsensitive()) {
                        append(")");
                    }
                    append(" LIKE ");
                    arg(value.getMatchString());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Append a range query.
     */
    public void appendRangeQuery(final String field, final Range<?> range) {
        if (range != null) {
            final Number size = range.size();
            // Exact range
            if (size != null && size.longValue() == 1) {
                append(" AND ");
                append(field);
                append(" = ");
                arg(range.getFrom());
            } else {
                if (range.getFrom() != null) {
                    append(" AND ");
                    append(field);
                    append(" >= ");
                    arg(range.getFrom());
                }
                if (range.getTo() != null) {
                    append(" AND ");
                    append(field);
                    append(" < ");
                    arg(range.getTo());
                }
                if (range.isMatchNull()) {
                    append(" AND ");
                    append(field);
                    append(" IS NULL");
                }
            }
        }
    }

//    public  void appendLongRangeQuery(final String field, final Range<Long> range) {
//        if (range != null) {
//            final Number size = range.size();
//            // Exact range
//            if (size != null && size.longValue() == 1) {
//                append(" AND ");
//                append(field);
//                append(" = ");
//                arg(range.getFrom());
//            } else {
//                if (range.getFrom() != null) {
//                    append(" AND ");
//                    append(field);
//                    append(" >= ");
//                    arg(range.getFrom());
//                }
//                if (range.getTo() != null) {
//                    append(" AND ");
//                    append(field);
//                    append(" < ");
//                    arg(range.getTo());
//                }
//                if (range.isMatchNull()) {
//                    append(" AND ");
//                    append(field);
//                    append(" IS NULL");
//                }
//            }
//        }
//    }
//
//    /**
//     * Append a range query.
//     */
//    public void appendTimeStampRangeQuery(final String field, final Period range) {
//        if (range != null) {
//            final Number size = range.size();
//            // Exact range
//            if (size != null && size.longValue() == 1) {
//                append(" AND ");
//                append(field);
//                append(" = ");
//                arg(range.getFrom());
//            } else {
//                if (range.getFrom() != null) {
//                    append(" AND ");
//                    append(field);
//                    append(" >= ");
//                    arg(new Date(range.getFrom()));
//                }
//                if (range.getTo() != null) {
//                    append(" AND ");
//                    append(field);
//                    append(" < ");
//                    arg(new Date(range.getTo()));
//                }
//                if (range.isMatchNull()) {
//                    append(" AND ");
//                    append(field);
//                    append(" IS NULL");
//                }
//            }
//        }
//    }
}