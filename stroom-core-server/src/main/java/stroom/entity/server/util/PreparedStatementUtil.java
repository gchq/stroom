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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;

/**
 * Utility Class
 */
public class PreparedStatementUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(PreparedStatementUtil.class);

    public static void setArguments(final PreparedStatement ps, final Iterable<Object> args) throws SQLException {
        if (args != null) {
            int pos = 1;
            for (final Object arg : args) {
                try {
                    if (arg instanceof Number) {
                        ps.setLong(pos++, ((Number) arg).longValue());
                    } else if (arg instanceof String) {
                        ps.setString(pos++, ((String) arg));
                    } else if (arg instanceof Boolean) {
                        ps.setBoolean(pos++, ((Boolean) arg));
                    } else {
                        ps.setObject(pos++, arg);
                    }
                } catch (final SQLSyntaxErrorException syntaxError) {
                    throw new SQLSyntaxErrorException("Unable to set arg " + (pos - 1) + " (" + arg + ") in arg list " + args);
                }
            }
        }
    }

    static ResultSet createCloseStatementResultSet(final PreparedStatement statement) throws SQLException {
        final ResultSet resultSet = statement.executeQuery();
        return (ResultSet) Proxy.newProxyInstance(SqlUtil.class.getClassLoader(), new Class[]{ResultSet.class},
                (proxy, method, args) -> {
                    try {
                        final Object r = method.invoke(resultSet, args);
                        if (method.getName().equals("close")) {
                            statement.close();
                        }
                        return r;
                    } catch (final Throwable th) {
                        LOGGER.error(th.getMessage(), th);
                        throw th;
                    }
                });
    }
}
