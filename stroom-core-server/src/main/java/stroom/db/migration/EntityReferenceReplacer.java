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

package stroom.db.migration;

import stroom.util.logging.StroomLogger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EntityReferenceReplacer {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(EntityReferenceReplacer.class);

    private static final String[] CONTAINERS = {"entity", "pipeline", "feed", "streamType", "dataSource", "extractionPipeline", "visualisation"};

    public String replaceEntityReferences(final Connection connection, final String data) {
        String newData = data;

        for (final String container : CONTAINERS) {
            Pattern pattern = Pattern.compile("<" + container + ">\\s*<type>.*?</type>.*?</" + container + ">", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(data);
            while (matcher.find()) {
                try {
                    final String ref = data.substring(matcher.start(), matcher.end());
                    final String type = getString(ref, "type");
                    final String id = getString(ref, "id");
                    final String name = getString(ref, "name");
                    final String path = getString(ref, "path");

                    if (type != null) {
                        final StringBuilder sb = new StringBuilder();

                        switch (type) {
                            case "TextConverter":
                                appendReference(connection, container, "TXT_CONV", id, type, name, path, sb);
                                break;
                            case "XSLT":
                                appendReference(connection, container, "XSLT", id, type, name, path, sb);
                                break;
                            case "Feed":
                                appendReference(connection, container, "FD", id, type, name, path, sb);
                                break;
                            case "StreamType":
                                if (container.equals("streamType")) {
                                    sb.append("<");
                                    sb.append(container);
                                    sb.append(">");

                                    if (name != null) {
                                        sb.append(name);
                                    } else {
                                        sb.append(path);
                                    }

                                    sb.append("</");
                                    sb.append(container);
                                    sb.append(">");

                                } else {
                                    sb.append("<string>");
                                    if (name != null) {
                                        sb.append(name);
                                    } else {
                                        sb.append(path);
                                    }
                                    sb.append("</string>");
                                }

                                break;
                            case "Pipeline":
                                appendReference(connection, container, "PIPE", id, type, name, path, sb);
                                break;
                            case "Index":
                                appendReference(connection, container, "IDX", id, type, name, path, sb);
                                break;
                            case "Visualisation":
                                appendReference(connection, container, "VIS", id, type, name, path, sb);
                                break;
                            case "StatisticStore":
                                appendReference(connection, container, "STAT_DAT_SRC", id, "StatisticStore", name, path, sb);
                                break;
                            case "StatisticsDataSource":
                                appendReference(connection, container, "STAT_DAT_SRC", id, "StatisticStore", name, path, sb);
                                break;
                            default:
                                LOGGER.error("Unable to perform entity replacement for unknown type '" + type + "'");
                        }

                        newData = newData.replace(ref, sb.toString());
                    }
                } catch (final Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
        }

        return newData;
    }

    private String getString(final String data, final String elem) {
        Pattern pattern = Pattern.compile("<" + elem + ">(.*?)</" + elem + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(data);
        if (matcher.find()) {
            return data.substring(matcher.start(1), matcher.end(1));
        }
        return null;
    }

    private void appendReference(final Connection connection, final String container, final String table, final String id, final String type, final String name, final String path, final StringBuilder sb) {
        sb.append("<");
        sb.append(container);
        sb.append(">");

        sb.append("<type>");
        sb.append(type);
        sb.append("</type>");
        String uuid = getUUID(connection, table, id);
        if (uuid != null) {
            sb.append("<uuid>");
            sb.append(uuid);
            sb.append("</uuid>");
        } else {
            LOGGER.warn("Unable to resolve reference to " + type + " with id=" + id + ", name=" + name);
        }

        if (name != null) {
            sb.append("<name>");
            sb.append(name);
            sb.append("</name>");
        } else if (path != null) {
            final String[] parts = path.split("/");
            sb.append("<name>");
            sb.append(parts[parts.length - 1]);
            sb.append("</name>");
        }

        sb.append("</");
        sb.append(container);
        sb.append(">");
    }

    String getUUID(final Connection connection, final String table, final String id) {
        String uuid = null;

        if (id != null) {
            try {
                try (final Statement statement = connection.createStatement()) {
                    try (final ResultSet resultSet = statement.executeQuery("SELECT UUID FROM " + table + " WHERE ID = " + id)) {
                        while (resultSet.next() && uuid == null) {
                            uuid = resultSet.getString(1);
                        }
                    }
                }
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        return uuid;
    }
}
