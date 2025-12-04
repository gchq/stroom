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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class CoreSqlBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreSqlBuilder.class);

    private static final boolean VALIDATE = true;

    final List<Object> args;
    private final StringBuilder sql;

    CoreSqlBuilder() {
        args = new ArrayList<>();
        sql = new StringBuilder();
    }

    CoreSqlBuilder(final String prepared, final Object... args) {
        sql = new StringBuilder(prepared);
        this.args = Arrays.asList(args);
    }

    public CoreSqlBuilder append(final CoreSqlBuilder sqlBuilder) {
        final String string = sqlBuilder.toString();
        sql.append(string);
        for (final Object arg : sqlBuilder.getArgs()) {
            this.args.add(arg);
        }
        return this;
    }

    public CoreSqlBuilder append(final String string) {
        checkStaticStringAppend(string);
        sql.append(string);
        return this;
    }

    public CoreSqlBuilder append(final long l, final boolean check) {
        if (check) {
            checkValueAppend("long");
        }
        sql.append(l);
        return this;
    }

    public CoreSqlBuilder append(final long l) {
        return append(l, true);
    }

    public CoreSqlBuilder append(final byte b) {
        checkValueAppend("byte");
        sql.append(b);
        return this;
    }

    public void join(final String table,
                     final String alias,
                     final String aliasLeft,
                     final String fieldLeft,
                     final String aliasRight,
                     final String fieldRight) {
        join(" JOIN ", table, alias, aliasLeft, fieldLeft, aliasRight, fieldRight);
    }

    public void leftOuterJoin(final String table,
                              final String alias,
                              final String aliasLeft,
                              final String fieldLeft,
                              final String aliasRight,
                              final String fieldRight) {
        join(" LEFT OUTER JOIN ", table, alias, aliasLeft, fieldLeft, aliasRight, fieldRight);
    }

    public void rightOuterJoin(final String table,
                               final String alias,
                               final String aliasLeft,
                               final String fieldLeft,
                               final String aliasRight,
                               final String fieldRight) {
        join(" RIGHT OUTER JOIN ", table, alias, aliasLeft, fieldLeft, aliasRight, fieldRight);
    }

    private void join(final String joinType,
                      final String table,
                      final String alias,
                      final String aliasLeft,
                      final String fieldLeft,
                      final String aliasRight,
                      final String fieldRight) {
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

    public int length() {
        return sql.length();
    }

    public void setLength(final int newLength) {
        sql.setLength(newLength);
    }

    public CoreSqlBuilder arg(final Object arg) {
        args.add(arg);
        final String parameter = createParameter();
        checkStringAppend(parameter);
        sql.append(parameter);

        return this;
    }

    String createParameter() {
        return "?";
    }

    public Object get(final int pos) {
        if (pos >= 1 && args.size() >= pos) {
            return args.get(pos - 1);
        }

        return null;
    }

    public String toTraceString() {
        final StringBuilder trace = new StringBuilder(sql.length());
        int pos = 0;
        for (int i = 0; i < sql.length(); i++) {
            final char c = sql.charAt(i);
            if (c == '?') {
                trace.append(args.get(pos++));
            } else {
                trace.append(c);
            }
        }
        return trace.toString();
    }

    @Override
    public String toString() {
        return sql.toString();
    }

    public Iterable<Object> getArgs() {
        return args;
    }

    public int getArgCount() {
        return args.size();
    }

    private void checkValueAppend(final String type) {
        if (VALIDATE) {
            try {
                if (sql.length() == 0) {
                    throw new MalformedSqlException("Attempt to append " + type + " to empty expression");
                } else {
                    final char c = sql.charAt(sql.length() - 1);
                    if (c != ' ' && c != '(' && c != ',') {
                        throw new MalformedSqlException(
                                "Previous character should be space, bracket or comma - " + info(""));
                    }
                }
            } catch (final MalformedSqlException e) {
                LOGGER.warn(e.getMessage(), e);
            }
        }
    }

    private void checkStaticStringAppend(final String string) {
        if (VALIDATE) {
            try {
                if (string.contains("?")) {
                    throw new MalformedSqlException("Unexpected arg in statement - " + info(string));
                }
                checkStringAppend(string);
            } catch (final MalformedSqlException e) {
                LOGGER.warn(e.getMessage(), e);
            }
        }
    }

    private void checkStringAppend(final String string) {
        if (VALIDATE) {
            try {
                if (string.length() > 0) {
                    if (string.contains("  ")) {
                        throw new MalformedSqlException("Too many spaces in statement - " + info(string));
                    }
                    if (sql.length() > 0) {
                        final char c1 = sql.charAt(sql.length() - 1);
                        final char c2 = string.charAt(0);

                        if (c2 == ',' || c2 == '.') {
                            if (c1 != '\''
                                    && c1 != ')'
                                    && c1 != '?'
                                    && !Character.isDigit(c1)
                                    && !Character.isLetter(c1)) {
                                throw new MalformedSqlException(
                                        "Previous character should be alphanumeric - " + info("v" + string));
                            }
                        } else if (c1 != ' ' && c1 != '(' && c1 != '.' && c1 != ',' && c2 != ' ' && c2 != ')') {
                            throw new MalformedSqlException(
                                    "Previous character should be space, bracket or dot - " +
                                            info("v" + string));
                        }

                        if (c1 == ' ' && c2 == ' ') {
                            throw new MalformedSqlException("Too many spaces in statement - " + info(string));
                        }
                    }
                }
            } catch (final MalformedSqlException e) {
                LOGGER.warn(e.getMessage(), e);
            }
        }
    }

    private String info(final String append) {
        return sql.toString() + append;
    }
}
