/*
 * Copyright 2026 Crown Copyright
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

package stroom.mcp.impl.e2e;

import stroom.ai.impl.tools.sample.ToolJson;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Synthetic records shaped like those seen in external testing (2026-11-26): an aggregated feed of JSON lines, each
/// record's `event` a JSON string of an application's log record naming its logger (`logger_name`: a security
/// logger, an alerts logger, Tomcat's and other operational loggers), its appender (`appender_ref`) and its
/// `message`, and for the security logger's records an `eventType`. Some records' `event` is longer than the
/// JSONParser's `stringTruncateLength` (10,000 characters by default): a long stack trace in the message.
public final class LoggerFeed {

    /// The security logger.
    public static final String SECURITY = "SecurityEventLogger";
    /// The alerts logger.
    public static final String ALERTS = "DSM Alerts";
    /// Tomcat's servlet wrapper logger.
    public static final String VALVE = "org.apache.catalina.core.StandardWrapperValve";
    /// Tomcat's HTTP connector logger.
    public static final String COYOTE = "org.apache.coyote.http11.Http11Processor";
    /// An application's database logger.
    public static final String DATABASE = "com.example.platform.data.DatabaseImpl";
    /// The loggers the user keeps.
    public static final List<String> KEPT = List.of(SECURITY, ALERTS);
    /// The appenders, by the loggers writing to them.
    public static final Map<String, String> APPENDERS = Map.of(SECURITY, "SECURITY", ALERTS, "ALERT", VALVE,
            "CATALINA", COYOTE, "CATALINA", DATABASE, "APPLICATION");

    private LoggerFeed() {
        // Static only.
    }

    /// @param records How many records.
    /// @param offset  The first record's number (records of a later stream go on from those of an earlier one).
    /// @return The records, JSON lines: a sixth each of logons and logoffs, password changes, alerts, servlet
    /// errors (every other one with a stack trace past the cut), HTTP connector errors (every other one past the
    /// cut) and database messages; every 24th logon's message past the cut too.
    public static String lines(final int records, final int offset) {
        return lines(records, offset, true);
    }

    /// @param records    How many records.
    /// @param offset     The first record's number.
    /// @param longLogons Whether every 24th logon's message is past the cut ([#lines(int, int)]); else only the
    ///                   operational loggers' records are.
    /// @return The records, JSON lines.
    public static String lines(final int records, final int offset, final boolean longLogons) {
        final StringBuilder out = new StringBuilder();
        for (int i = offset; i < offset + records; i++) {
            out.append(ToolJson.dumps(record(i, longLogons || i % 6 != 0))).append('\n');
        }
        return out.toString();
    }

    /// @param i A record's number.
    /// @return Whether that record's `event` is longer than 10,000 characters.
    public static boolean isLong(final int i) {
        return switch (i % 6) {
            case 0 -> i % 24 == 0;
            case 3, 5 -> i % 12 >= 6;
            default -> false;
        };
    }

    /// @param i A record's number.
    /// @return Its logger.
    public static String loggerOf(final int i) {
        return switch (i % 6) {
            case 0, 1 -> SECURITY;
            case 2 -> ALERTS;
            case 3 -> VALVE;
            case 5 -> COYOTE;
            default -> DATABASE;
        };
    }

    private static Map<String, Object> record(final int i, final boolean longAllowed) {
        final String logger = loggerOf(i);
        final String time = "2026-11-26T09:%02d:%02d.%03dZ".formatted(i / 3600 % 60, i / 60 % 60, i % 60 * 7);
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("@timestamp", time);
        event.put("logger_name", logger);
        event.put("appender_ref", APPENDERS.get(logger));
        event.put("level", switch (i % 6) {
            case 3, 5 -> "ERROR";
            case 2 -> "WARN";
            default -> "INFO";
        });
        event.put("thread_name", "http-nio-8443-exec-" + i % 17);
        switch (i % 6) {
            case 0 -> {
                event.put("eventType", i % 12 == 0
                        ? "Login"
                        : "Logout");
                event.put("userid", "user" + i % 7);
                event.put("message", (i % 12 == 0
                        ? "Operator user" + i % 7 + " logged in"
                        : "Operator user" + i % 7 + " logged out") + (longAllowed && isLong(i)
                        ? " with assertion " + "A".repeat(12_000)
                        : ""));
            }
            case 1 -> {
                event.put("eventType", "Password change");
                event.put("userid", "user" + i % 7);
                event.put("message", "Password change by user" + i % 7);
            }
            case 2 -> {
                event.put("alert_id", "ALERT00" + (10 + i % 5));
                event.put("duration_ms", 1000 + i);
                event.put("message", alertMessage(i));
            }
            case 3 -> {
                event.put("servlet", "Web" + i % 3);
                event.put("message", "Servlet.service() for servlet [Web] threw exception" + (isLong(i)
                        ? stackTrace(i)
                        : ""));
            }
            case 5 -> {
                event.put("remote_addr", "10.1.0." + i % 50);
                event.put("message", "Error parsing HTTP request header" + (isLong(i)
                        ? stackTrace(i)
                        : ""));
            }
            default -> {
                event.put("pool", "db-pool-" + i % 2);
                event.put("message", databaseMessage(i));
            }
        }
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("@timestamp", time);
        record.put("host", "app-web-0" + i % 3);
        record.put("event", ToolJson.dumps(event));
        return record;
    }

    private static String alertMessage(final int i) {
        return switch (i / 6 % 3) {
            case 0 -> "ALERT0010 alert: interaction took " + (1000 + i) + "ms";
            case 1 -> "Connector call to " + "svc" + i % 4 + " exceeded its threshold of " + i + " ms";
            default -> "Queue depth " + i + " over the limit for agent batch" + i % 3;
        };
    }

    private static String databaseMessage(final int i) {
        return switch (i / 6 % 3) {
            case 0 -> "Database query took " + (10 + i % 40) + "ms";
            case 1 -> "Connection pool grew to " + (5 + i % 20) + " connections";
            default -> "Statement cache cleared after " + i + " entries";
        };
    }

    // A Java stack trace longer than 10,000 characters.
    private static String stackTrace(final int i) {
        final StringBuilder out = new StringBuilder("\njava.lang.IllegalStateException: request " + i + " failed");
        for (int frame = 0; out.length() < 12_000; frame++) {
            out.append("\n\tat com.example.platform.web.Handler").append(frame % 23).append(".handle(Handler")
                    .append(frame % 23).append(".java:").append(100 + frame).append(')');
        }
        return out.toString();
    }
}
