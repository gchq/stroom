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

package stroom.entity.server.util;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseCriteria.OrderBySetting;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.IncludeExcludeEntityIdSet;
import stroom.entity.shared.OrderBy;
import stroom.entity.shared.PageRequest;
import stroom.entity.shared.Period;
import stroom.entity.shared.Range;
import stroom.entity.shared.StringCriteria;

import javax.persistence.Query;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public final class SQLUtil {
    private SQLUtil() {
        // Utility
    }

    /**
     * Utility to restrict the results in the query.
     */
    public static void applyRestrictionCriteria(final Query query, final BaseCriteria criteria) {
        if (criteria.getPageRequest() != null) {
            final PageRequest pageRequest = criteria.getPageRequest();
            if (pageRequest.getOffset() != null) {
                query.setFirstResult(pageRequest.getOffset().intValue());
            }
            if (pageRequest.getLength() != null) {
                // Add one so we know we have more results in the next page
                query.setMaxResults(pageRequest.getLength().intValue() + 1);
            }
        }
    }

    /**
     * Utility to restrict the results in the query.
     */
    public static void applyRestrictionCriteria(final BaseCriteria criteria, final SQLBuilder sql) {
        if (criteria.getPageRequest() != null) {
            final PageRequest pageRequest = criteria.getPageRequest();
            if (pageRequest.getOffset() != null && pageRequest.getOffset() > 0 && pageRequest.getLength() != null) {
                sql.append(" LIMIT ");
                sql.arg(pageRequest.getLength().longValue() + 1);
                sql.append(" OFFSET ");
                sql.arg(pageRequest.getOffset());
            } else if (pageRequest.getLength() != null) {
                sql.append(" LIMIT ");
                sql.arg(pageRequest.getLength().longValue() + 1);
            }
            sql.append(" ");
        }
    }

    public static void appendOrderBy(final SQLBuilder sql, final boolean isEjbQl, final BaseCriteria criteria,
            final String prefix) {
        final List<OrderBySetting> orderBySettings = criteria.getOrderByList();
        if (orderBySettings != null && orderBySettings.size() > 0) {
            sql.append(" ORDER BY");
            boolean doneOne = false;

            for (final OrderBySetting orderBySetting : orderBySettings) {
                // This should not really be null.
                final OrderBy orderBy = orderBySetting.getOrderBy();
                if (orderBy != null) {
                    final String val = isEjbQl ? orderBy.getEJBQL() : orderBy.getSQL();

                    if (doneOne) {
                        sql.append(",");
                    }
                    doneOne = true;

                    if (orderBy.isCaseInsensitive()) {
                        sql.append(" UPPER(");
                        if (prefix != null) {
                            sql.append(prefix);
                            sql.append(".");
                        }
                    } else {
                        sql.append(" ");
                        if (prefix != null) {
                            sql.append(prefix);
                            sql.append(".");
                        }
                    }

                    sql.append(val);

                    if (orderBy.isCaseInsensitive()) {
                        sql.append(")");
                    }
                    if (!orderBySetting.isAscending()) {
                        sql.append(" DESC");
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
    public static void appendSetQuery(final SQLBuilder sql, final boolean isEjbQl, final String field,
            final CriteriaSet<?> set, final boolean quote) {
        if (set != null && set.isConstrained()) {
            sql.append(" AND ");
            if (set.isMatchNothing()) {
                // Force the query to return nothing if the set is empty.
                sql.append("1=2");
            } else {
                sql.append(field);
                sql.append(" IN (");
                for (final Object item : set) {
                    if (!isEjbQl) {
                        if (item instanceof HasPrimitiveValue) {
                            sql.arg(((HasPrimitiveValue) item).getPrimitiveValue());
                        } else {
                            sql.arg(String.valueOf(item));
                        }
                    } else {
                        if (item instanceof HasPrimitiveValue) {
                            sql.append(((HasPrimitiveValue) item).getPrimitiveValue());
                        } else {
                            if (quote) {
                                sql.append("'");
                            }
                            sql.append(String.valueOf(item));
                            if (quote) {
                                sql.append("'");
                            }
                        }
                    }
                    sql.append(",");
                }
                // Remove the last comma
                sql.setLength(sql.length() - 1);
                sql.append(")");
            }
        }
    }

    /**
     * <p>
     * Add a set query like A in ('A','B').
     * </p>
     */
    public static void appendIncludeExcludeSetQuery(final SQLBuilder sql, final boolean isEjbQl,
            final String fieldOrEntity, final IncludeExcludeEntityIdSet<?> includeExcludeEntityIdSet) {
        if (includeExcludeEntityIdSet != null) {
            if (includeExcludeEntityIdSet.getInclude() != null
                    && includeExcludeEntityIdSet.getInclude().isConstrained()) {
                sql.append(" AND");
                internalAppendSetQuery(sql, isEjbQl, fieldOrEntity, includeExcludeEntityIdSet.getInclude());
            }
            if (includeExcludeEntityIdSet.getExclude() != null
                    && includeExcludeEntityIdSet.getExclude().isConstrained()) {
                sql.append(" AND NOT(");
                internalAppendSetQuery(sql, isEjbQl, fieldOrEntity, includeExcludeEntityIdSet.getExclude());
                sql.append(")");
            }
        }
    }

    /**
     * <p>
     * Add a set query like A in ('A','B').
     * </p>
     */
    public static void appendSetQuery(final SQLBuilder sql, final boolean isEjbQl, final String fieldOrEntity,
            final EntityIdSet<?> set) {
        if (set != null && set.isConstrained()) {
            sql.append(" AND");
            internalAppendSetQuery(sql, isEjbQl, fieldOrEntity, set);
        }
    }

    private static void internalAppendSetQuery(final SQLBuilder sql, final boolean isEjbQl, final String fieldOrEntity,
            final EntityIdSet<?> set) {
        if (set.isMatchNothing()) {
            // Force the query to return nothing if the set is empty.
            sql.append(" 1=2");

        } else if (Boolean.TRUE.equals(set.getMatchNull())) {
            sql.append(" ");

            if (set.size() > 0) {
                sql.append("(");
                appendSet(sql, isEjbQl, fieldOrEntity, set);
                sql.append(" OR ");
                appendNull(sql, isEjbQl, fieldOrEntity);
                sql.append(")");

            } else {
                appendNull(sql, isEjbQl, fieldOrEntity);
            }

        } else {
            sql.append(" ");
            appendSet(sql, isEjbQl, fieldOrEntity, set);
        }
    }

    private static void appendSet(final SQLBuilder sql, final boolean isEjbQl, final String fieldOrEntity,
            final EntityIdSet<?> set) {
        sql.append(fieldOrEntity);
        if (isEjbQl) {
            sql.append(".id IN (");
        } else {
            sql.append(" IN (");
        }
        if (set.size() > 0) {
            for (final Object item : set) {
                if (item != null) {
                    if (item instanceof Number) {
                        sql.append(((Number) item).longValue());
                    } else {
                        sql.append(((BaseEntity) item).getId());
                    }
                    sql.append(",");
                }
            }
            // Remove the last comma.
            sql.setLength(sql.length() - 1);
        }
        sql.append(")");
    }

    private static void appendNull(final SQLBuilder sql, final boolean isEjbQl, final String fieldOrEntity) {
        sql.append(fieldOrEntity);
        if (isEjbQl) {
            sql.append(" IS NULL");
        } else {
            sql.append(" IS NULL");
        }
    }

    /**
     * <p>
     * Add a value query like A = ?.
     * </p>
     */
    public static void appendValueQuery(final SQLBuilder sql, final String field, final Object value) {
        if (value != null) {
            sql.append(" AND ");
            sql.append(field);
            sql.append(" = ");
            if (value instanceof HasPrimitiveValue) {
                sql.arg(((HasPrimitiveValue) value).getPrimitiveValue());
            } else {
                sql.arg(value);
            }
        }
    }

    public static void appendValueQuery(final SQLBuilder sql, final String field, final StringCriteria value) {
        if (value != null) {
            sql.append(" AND ");
            if (!appendValue(sql, field, value)) {
                // Trim off AND.
                sql.setLength(sql.length() - 5);
            }
        }
    }

    public static void appendValuesQuery(final SQLBuilder sql, final String field, final List<StringCriteria> values) {
        if (values != null && values.size() > 0) {
            sql.append(" AND (");

            boolean added = false;
            for (final StringCriteria value : values) {
                if (appendValue(sql, field, value)) {
                    sql.append(" OR ");
                    added = true;
                }
            }

            if (added) {
                // Trim off last OR.
                sql.setLength(sql.length() - 4);
                sql.append(")");
            } else {
                // Trim off AND.
                sql.setLength(sql.length() - 6);
            }
        }
    }

    private static boolean appendValue(final SQLBuilder sql, final String field, final StringCriteria value) {
        if (value != null) {
            if (value.getMatchNull() != null) {
                if (Boolean.TRUE.equals(value.getMatchNull())) {
                    sql.append(field);
                    sql.append(" IS NULL");
                } else if (Boolean.FALSE.equals(value.getMatchNull())) {
                    sql.append(field);
                    sql.append(" IS NOT NULL");
                }
                return true;
            } else if (value.getString() != null) {
                if (value.getMatchStyle() == null) {
                    sql.append(field);
                    sql.append(" = ");
                    sql.arg(value.getString());
                    return true;
                } else {
                    if (value.isCaseInsensitive()) {
                        sql.append("UPPER(");
                    }
                    sql.append(field);
                    if (value.isCaseInsensitive()) {
                        sql.append(")");
                    }
                    sql.append(" LIKE ");
                    sql.arg(value.getMatchString());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Append a range query.
     */
    public static void appendRangeQuery(final SQLBuilder sql, final String field, final Range<?> range) {
        if (range != null) {
            final Number size = range.size();
            // Exact range
            if (size != null && size.longValue() == 1) {
                sql.append(" AND ");
                sql.append(field);
                sql.append(" = ");
                sql.arg(range.getFrom());
            } else {
                if (range.getFrom() != null) {
                    sql.append(" AND ");
                    sql.append(field);
                    sql.append(" >= ");
                    sql.arg(range.getFrom());
                }
                if (range.getTo() != null) {
                    sql.append(" AND ");
                    sql.append(field);
                    sql.append(" < ");
                    sql.arg(range.getTo());
                }
                if (range.isMatchNull()) {
                    sql.append(" AND ");
                    sql.append(field);
                    sql.append(" IS NULL");
                }
            }
        }
    }

    public static void appendLongRangeQuery(final SQLBuilder sql, final String field, final Range<Long> range) {
        if (range != null) {
            final Number size = range.size();
            // Exact range
            if (size != null && size.longValue() == 1) {
                sql.append(" AND ");
                sql.append(field);
                sql.append(" = ");
                sql.append(range.getFrom());
            } else {
                if (range.getFrom() != null) {
                    sql.append(" AND ");
                    sql.append(field);
                    sql.append(" >= ");
                    sql.append(range.getFrom());
                }
                if (range.getTo() != null) {
                    sql.append(" AND ");
                    sql.append(field);
                    sql.append(" < ");
                    sql.append(range.getTo());
                }
                if (range.isMatchNull()) {
                    sql.append(" AND ");
                    sql.append(field);
                    sql.append(" IS NULL");
                }
            }
        }
    }

    /**
     * Append a range query.
     */
    public static void appendTimeStampRangeQuery(final SQLBuilder sql, final String field, final Period range) {
        if (range != null) {
            final Number size = range.size();
            // Exact range
            if (size != null && size.longValue() == 1) {
                sql.append(" AND ");
                sql.append(field);
                sql.append(" = ");
                sql.arg(range.getFrom());
            } else {
                if (range.getFrom() != null) {
                    sql.append(" AND ");
                    sql.append(field);
                    sql.append(" >= ");
                    sql.arg(new Date(range.getFrom()));
                }
                if (range.getTo() != null) {
                    sql.append(" AND ");
                    sql.append(field);
                    sql.append(" < ");
                    sql.arg(new Date(range.getTo()));
                }
                if (range.isMatchNull()) {
                    sql.append(" AND ");
                    sql.append(field);
                    sql.append(" IS NULL");
                }
            }
        }
    }

    public static void appendCondition(final SQLBuilder sql, final String prefix, final String field,
            final String condition, final Object value) {
        if (value != null) {
            sql.append(" AND ");
            sql.append(prefix);
            sql.append(field);
            sql.append(" ");
            sql.append(condition);
            sql.append(" ");
            sql.arg(value);
        }
    }


    public static void join(final SQLBuilder sql, final String table, final String alias, final String aliasLeft, final String fieldLeft, final String aliasRight, final String fieldRight) {
        join(sql, " JOIN ", table, alias, aliasLeft, fieldLeft, aliasRight, fieldRight);
    }

    public static void leftOuterJoin(final SQLBuilder sql, final String table, final String alias, final String aliasLeft, final String fieldLeft, final String aliasRight, final String fieldRight) {
        join(sql, " LEFT OUTER JOIN ", table, alias, aliasLeft, fieldLeft, aliasRight, fieldRight);
    }

    public static void rightOuterJoin(final SQLBuilder sql, final String table, final String alias, final String aliasLeft, final String fieldLeft, final String aliasRight, final String fieldRight) {
        join(sql, " RIGHT OUTER JOIN ", table, alias, aliasLeft, fieldLeft, aliasRight, fieldRight);
    }

    private static void join(final SQLBuilder sql, final String joinType, final String table, final String alias, final String aliasLeft, final String fieldLeft, final String aliasRight, final String fieldRight) {
        sql.append(joinType);
        sql.append(table);
        sql.append(" ");
        sql.append(alias);
        sql.append(" ON ");
        sql.append("(");
        sql.append(aliasLeft);
        sql.append(".");
        sql.append(fieldLeft);
        sql.append(" = ");
        sql.append(aliasRight);
        sql.append(".");
        sql.append(fieldRight);
        sql.append(")");
    }

    public static final String buildSQLTrace(final String sql, final List<Object> args) {
        final StringBuilder sqlString = new StringBuilder();
        int arg = 0;
        for (int i = 0; i < sql.length(); i++) {
            final char c = sql.charAt(i);
            if (c == '?') {
                sqlString.append(args.get(arg++));
            } else {
                sqlString.append(c);
            }
        }
        return sqlString.toString();
    }

    /**
     * Prime a query with the list are parameters by index shard.
     */
    public static void setParameters(final Query query, final SQLBuilder sql) {
        if (sql != null && sql.getArgs() != null) {
            int pos = 1;
            for (final Object arg : sql.getArgs()) {
                query.setParameter(pos++, arg);
            }
        }
    }

    public static Long getLong(final ResultSet resultSet, final int pos) throws SQLException {
        final long number = resultSet.getLong(pos);
        if (resultSet.wasNull()) {
            return null;
        }
        return number;
    }

    public static Long getLong(final Object[] row, final int pos) {
        final Number number = (Number) row[pos];
        if (number == null) {
            return null;
        }
        return number.longValue();
    }
}
