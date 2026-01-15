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

package stroom.statistics.impl.sql;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;

/**
 * Utility Class
 */
public class PreparedStatementUtil {

    public static void setArguments(final PreparedStatement ps, final Iterable<Object> args) throws SQLException {
        if (args != null) {
            int index = 1;
            for (final Object o : args) {
                try {
                    if (o instanceof Long) {
                        ps.setLong(index, (Long) o);
                    } else if (o instanceof Integer) {
                        ps.setInt(index, (Integer) o);
                    } else if (o instanceof Double) {
                        ps.setDouble(index, (Double) o);
                    } else if (o instanceof Byte) {
                        ps.setByte(index, (Byte) o);
                    } else if (o instanceof String) {
                        ps.setString(index, ((String) o));
                    } else if (o instanceof Boolean) {
                        ps.setBoolean(index, ((Boolean) o));
                    } else if (o instanceof byte[]) {
                        ps.setBytes(index, ((byte[]) o));
                    } else {
                        ps.setObject(index, o);
                    }
                } catch (final SQLSyntaxErrorException syntaxError) {
                    throw new SQLSyntaxErrorException(
                            "Unable to set arg " + index + " (" + o + ") in arg list " + args);
                }
                index++;
            }
        }
    }
}
