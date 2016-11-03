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

import stroom.util.logging.StroomLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SQLBuilder {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(SQLBuilder.class);
    private static final boolean VALIDATE = true;

    private final List<Object> args;
    private final StringBuilder sql;

    public SQLBuilder() {
        this(true);
    }

    public SQLBuilder(final boolean useArgs) {
        if (useArgs) {
            args = new ArrayList<Object>();
        } else {
            args = null;
        }
        sql = new StringBuilder();
    }

    public SQLBuilder(final String prepared, final Object... args) {
        sql = new StringBuilder(prepared);
        this.args = Arrays.asList(args);
    }

    public SQLBuilder append(final String string) {
        checkStaticStringAppend(string);
        sql.append(string);
        return this;
    }

    public SQLBuilder append(final long l, final boolean check) {
        if (check) {
            checkValueAppend("long");
        }
        sql.append(l);
        return this;
    }

    public SQLBuilder append(final long l) {
        return append(l, true);
    }

    public SQLBuilder append(final byte b) {
        checkValueAppend("byte");
        sql.append(b);
        return this;
    }

    public int length() {
        return sql.length();
    }

    public void setLength(final int newLength) {
        sql.setLength(newLength);
    }

    public SQLBuilder arg(final Object arg) {
        if (args != null) {
            args.add(arg);
            checkStringAppend("?");
            sql.append("?");
            sql.append(args.size());

        } else {
            checkStaticStringAppend(arg.toString());
            sql.append(arg);
        }

        return this;
    }

    public Object get(final int pos) {
        if (args != null && pos >= 1 && args.size() >= pos) {
            return args.get(pos - 1);
        }

        return null;
    }

    public String toTraceString() {
        if (args == null) {
            return toString();
        }

        final StringBuilder trace = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < sql.length(); i++) {
            final char c = sql.charAt(i);
            if (c == '?') {
                trace.append(args.get(pos++));
                i++;
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

    private void checkValueAppend(final String type) {
        if (VALIDATE) {
            try {
                if (sql.length() == 0) {
                    throw new MalformedSQLException("Attempt to append " + type + " to empty expression");
                } else {
                    final char c = sql.charAt(sql.length() - 1);
                    if (c != ' ' && c != '(' && c != ',') {
                        throw new MalformedSQLException(
                                "Previous character should be space, bracket or comma - " + info(""));
                    }
                }
            } catch (final MalformedSQLException e) {
                LOGGER.warn(e.getMessage(), e);
            }
        }
    }

    private void checkStaticStringAppend(final String string) {
        if (VALIDATE) {
            try {
                if (string.indexOf("?") != -1) {
                    throw new MalformedSQLException("Unexpected arg in statement - " + info(string));
                }
                checkStringAppend(string);
            } catch (final MalformedSQLException e) {
                LOGGER.warn(e.getMessage(), e);
            }
        }
    }

    private void checkStringAppend(final String string) {
        if (VALIDATE) {
            try {
                if (string.length() > 0) {
                    if (string.indexOf("  ") != -1) {
                        throw new MalformedSQLException("Too many spaces in statement - " + info(string));
                    }
                    if (sql.length() > 0) {
                        final char c1 = sql.charAt(sql.length() - 1);
                        final char c2 = string.charAt(0);

                        if (c2 == ',' || c2 == '.') {
                            if (c1 != '\'' && c1 != ')' && !Character.isDigit(c1) && !Character.isLetter(c1)) {
                                throw new MalformedSQLException(
                                        "Previous character should be alphanumeric - " + info("v" + string));
                            }
                        } else if (c1 != ' ' && c1 != '(' && c1 != '.' && c1 != ',' && c2 != ' ' && c2 != ')') {
                            throw new MalformedSQLException(
                                    "Previous character should be space, bracket or dot - " + info("v" + string));
                        }

                        if (c1 == ' ' && c2 == ' ') {
                            throw new MalformedSQLException("Too many spaces in statement - " + info(string));
                        }
                    }
                }
            } catch (final MalformedSQLException e) {
                LOGGER.warn(e.getMessage(), e);
            }
        }
    }

    private String info(final String append) {
        return sql.toString() + append;
    }
}
