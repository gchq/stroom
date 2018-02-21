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

package stroom.entity.util;

import javax.persistence.Query;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public final class SqlUtil {
    private SqlUtil() {
        // Utility
    }

    public static String buildSQLTrace(final String sql, final List<Object> args) {
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
     * Prime a query with the list of parameters.
     */
    static void setParameters(final Query query, final CoreSqlBuilder sql) {
        if (sql != null) {
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
