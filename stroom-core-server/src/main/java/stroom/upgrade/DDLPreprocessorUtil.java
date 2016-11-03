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

package stroom.upgrade;

public class DDLPreprocessorUtil {
    public static String processSQL(String sql) {
        if (sql.toUpperCase().contains("/*MYSQL*/")) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        int depth = 0;
        int lastToken = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
                if (depth == 1) {
                    builder.append(processToken(sql.substring(lastToken, i)));
                    lastToken = i;
                }
            }
            if (c == ')') {
                depth--;
                if (depth == 0) {
                    builder.append(processToken(sql.substring(lastToken, i)));
                    lastToken = i;
                }
            }
            if (c == ',') {
                if (depth == 1) {
                    builder.append(processToken(sql.substring(lastToken, i)));
                    lastToken = i;
                }
            }
        }
        builder.append(processToken(sql.substring(lastToken)));
        return builder.toString();
    }

    public static String processToken(String part) {
        StringBuilder buffer = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
            }
            if (!inQuote) {
                if (c == '\n') {
                    c = ' ';
                }
                if (c == '\t') {
                    c = ' ';
                }
                c = Character.toUpperCase(c);
            }
            buffer.append(c);
        }
        part = buffer.toString();
        part = part.trim();
        boolean startsWithComma = part.startsWith(",");
        if (startsWithComma) {
            part = part.substring(1).trim();
        }
        part = part.replace("AUTO_INCREMENT", "IDENTITY");
        part = part.replace("LONGTEXT", "CLOB");
        part = part.replace("LONGBLOB", "BLOB");

        part = part.replaceFirst("CONSTRAINT [^ ]+ ", "");
        part = part.replaceFirst("ENGINE=[^ ]+ DEFAULT", "");
        part = part.replaceFirst("CHARSET=[^ ]+", "");
        part = part.replaceFirst("INT\\([^)]+\\)", "INT");

        part = part.trim();
        if (part.equals(",") || part.equals("")) {
            return "";
        }
        if (startsWithComma) {
            return ",\n" + part;
        } else {
            return part;
        }
    }

}
