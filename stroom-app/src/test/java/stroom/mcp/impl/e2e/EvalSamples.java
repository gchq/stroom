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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// The evaluation set's sample files too large to keep in a case (a case's `files: [{generate: {kind, ...}}]`):
/// each made from its seed, the same bytes every run, so that they match the SHA-256 the set pins for each
/// (`generated-sha256.json`, checked by `TestEvalSamples`), the bytes the cases' expectations were written for
/// ([SeededRandom] draws as Python's `random` does, in the order of each generator's draws, and JSON is written as
/// Python's `json.dumps` writes it).
final class EvalSamples {

    private static final List<String> USERS = List.of("alice", "bob", "carol", "dave", "erin", "frank", "grace",
            "heidi", "ivan", "judy");
    private static final List<List<String>> SITES = List.of(
            List.of("https://intranet.example.org/", "business"),
            List.of("https://news.example.com/world", "news"),
            List.of("https://mail.example.org/inbox", "webmail"),
            List.of("https://files.example.net/share/q3.xlsx", "file-sharing"),
            List.of("https://cdn.example.com/lib.js", "technology"),
            List.of("http://198.51.100.40/update.bin", "uncategorised"),
            List.of("https://social.example.com/feed", "social-media"),
            List.of("https://search.example.com/?q=stroom", "search"));
    private static final List<String> METHODS = concat(Collections.nCopies(8, "GET"), List.of("POST", "CONNECT"));
    private static final List<Integer> STATUSES = concat(Collections.nCopies(12, 200),
            List.of(204, 301, 302, 304, 403, 404, 407, 502));
    private static final List<String> PAN_TRAFFIC = List.of("future_use1", "receive_time", "serial", "type",
            "subtype", "future_use2", "generated_time", "src", "dst", "natsrc", "natdst", "rule", "srcuser",
            "dstuser", "app", "vsys", "from", "to", "inbound_if", "outbound_if", "logset", "future_use3",
            "sessionid", "repeatcnt", "sport", "dport", "natsport", "natdport", "flags", "proto", "action", "bytes",
            "bytes_sent", "bytes_received", "packets", "start", "elapsed", "category", "future_use4", "seqno",
            "actionflags", "srcloc", "dstloc", "future_use5", "pkts_sent", "pkts_received", "session_end_reason");
    private static final Map<Integer, String> DAMAGED_AT = Map.of(4, "cut", 120, "garbage", 333, "quote",
            512, "cut");
    private static final LocalDateTime OCT_11 = LocalDateTime.of(2026, 10, 11, 8, 0, 0);

    private EvalSamples() {
        // Static only.
    }

    /// Makes a generated sample file.
    ///
    /// @param spec The file's `generate`: its `kind` (the generator) and the generator's arguments.
    /// @param case35Sample Case 35's sample, whose records [the Secret Server export][#secretserverAudit] is made
    ///                     of.
    /// @return Its text.
    static String generate(final Map<String, Object> spec, final String case35Sample) {
        final String kind = (String) spec.get("kind");
        final int records = ((Number) spec.get("records")).intValue();
        final int seed = ((Number) spec.get("seed")).intValue();
        return switch (kind) {
            case "proxy_access" -> proxyAccess((String) spec.get("day"), records, seed);
            case "secretserver_audit" -> secretserverAudit(case35Sample, records, seed, intOr(spec, "plain_head",
                    400));
            case "cef_gateway" -> cefGateway(records, seed, intOr(spec, "plain_head", 3000));
            case "json_alerts_one_line" -> jsonAlertsOneLine(records, seed, intOr(spec, "plain_head", 1500));
            case "bluecoat_access" -> bluecoatAccess(records, seed, intOr(spec, "restart_at", 2000));
            case "cloudtrail" -> cloudtrail(records, seed, intOr(spec, "plain_head", 500));
            case "java_app_log" -> javaAppLog(records, seed, intOr(spec, "plain_head", 1200));
            case "ticket_csv" -> ticketCsv(records, seed);
            case "palo_alto_traffic" -> paloAltoTraffic(records, seed, intOr(spec, "plain_head", 1500));
            case "tilde_audit" -> tildeAudit(records, seed);
            case "gelf_app" -> gelfApp(records, seed);
            case "ecs_json" -> ecsJson(records, seed);
            case "fortios_native" -> fortiosNative(records, seed);
            case "damaged_jsonl" -> damagedJsonl(records, seed);
            case "soap_gateway" -> soapGateway(records, seed);
            case "mail_gateway" -> mailGateway(records, seed);
            case "unicode_app" -> unicodeApp(records, seed);
            case "postgres_log" -> postgresLog(records, seed);
            case "cri_container_log" -> criContainerLog(records, seed);
            default -> throw new IllegalArgumentException("No generator " + kind);
        };
    }

    /// Python's `json.dumps(value)` (or with `indent`), its defaults: `", "` and `": "` between items (`","`
    /// when indented), and everything not ASCII escaped.
    ///
    /// @param value  Maps (in their order), lists, strings, numbers, booleans and null.
    /// @param indent The indent, or 0 for none.
    /// @return The JSON.
    static String dumps(final Object value, final int indent) {
        final StringBuilder sb = new StringBuilder();
        write(sb, value, indent, 0);
        return sb.toString();
    }

    // --------------------------------------------------------------------------------
    // The generators.

    private static String proxyAccess(final String day, final int records, final int seed) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = LocalDateTime.parse(day + "T00:00:00");
        final List<String> lines = new ArrayList<>(List.of("time,client_ip,user,method,url,status,bytes,category,"
                                                           + "action"));
        for (int n = 0; n < records; n++) {
            // timedelta(seconds=n * 86400 / records): only whole seconds are written.
            final LocalDateTime at = start.plusSeconds((long) n * 86400 / records);
            final List<String> site = rng.choice(SITES);
            final int status = rng.choice(STATUSES);
            final String action = status == 403 || status == 407
                    ? "blocked"
                    : "allowed";
            lines.add(format(at, "yyyy-MM-dd'T'HH:mm:ss") + "Z,10." + rng.randint(0, 3) + "." + rng.randint(0, 254)
                      + "." + rng.randint(1, 254) + "," + rng.choice(USERS) + "," + rng.choice(METHODS) + ","
                      + site.get(0) + "," + status + "," + rng.randint(200, 900000) + "," + site.get(1) + ","
                      + action);
        }
        return lines(lines);
    }

    private static String secretserverAudit(final String case35Sample,
                                            final int records,
                                            final int seed,
                                            final int plainHead) {
        final List<String> templates = case35Sample.lines().toList();
        final Pattern headKind = Pattern.compile("Event: \\[(User|Secret)\\] Action: \\[(Login|Login Failed|Logout|"
                                                 + "View)\\]");
        final List<String> headKinds = templates.stream().filter(t -> headKind.matcher(t).find()).toList();
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = LocalDateTime.of(2026, 9, 27, 23, 48, 10);
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final String template = n < plainHead
                    ? rng.choice(headKinds)
                    : templates.get((n - plainHead) % templates.size());
            final LocalDateTime at = start.plusSeconds(97L * n);
            String line = sub("SystemTime=\"[^\"]*\"", "SystemTime=\"" + format(at, "yyyy-MM-dd'T'HH:mm:ss")
                                                         + ".0000000Z\"", template);
            line = sub("<EventRecordID>\\d+</EventRecordID>", "<EventRecordID>" + (12001 + n) + "</EventRecordID>",
                    line);
            line = sub("<Data>[A-Z][a-z]{2} \\d{2} \\d{4} \\d{2}:\\d{2}:\\d{2}", "<Data>"
                                                                              + format(at, "MMM dd yyyy HH:mm:ss"),
                    line);
            lines.add(sub("\\(Item Id: \\d+\\)", "(Item Id: " + (219 + n) + ")", line));
        }
        return lines(lines);
    }

    private static String cefGateway(final int records, final int seed, final int plainHead) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = LocalDateTime.of(2026, 10, 2, 9, 0, 0);
        final List<String> users = List.of("alice", "bob", "carol", "dave", "erin");
        final List<List<Object>> sites = List.of(
                List.of("https://example.com/report.pdf", "93.184.216.34", 443, "business"),
                List.of("http://198.51.100.40/update.bin", "198.51.100.40", 80, "malware"),
                List.of("https://news.example.org/today", "203.0.113.80", 443, "news"));
        final List<String> kinds = List.of("1001", "1002", "1003", "2001", "2002", "3001");
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final LocalDateTime at = start.plusSeconds(7L * n);
            final String kind = n < plainHead
                    ? rng.choice(List.of("1001", "2001"))
                    : kinds.get(n % 6);
            final String user = rng.choice(users);
            final String src = "10.1." + rng.randint(0, 9) + "." + rng.randint(2, 250);
            final List<Object> site = rng.choice(sites);
            final Object url = site.get(0);
            final Object dst = site.get(1);
            final Object port = site.get(2);
            final Object cat = site.get(3);
            // The whole dict is built before it is indexed: 1002's address is drawn for every line.
            final String failedSrc = "203.0.113." + rng.randint(2, 250);
            final String[] chosen = switch (kind) {
                case "1001" -> new String[]{"User authenticated", "3", "suser=" + user + " src=" + src
                                                                      + " act=login outcome=success msg=SAML login "
                                                                      + "from the VPN"};
                case "1002" -> new String[]{"Authentication failed", "6", "suser=" + user + " src=" + failedSrc
                                                                          + " act=login outcome=failure msg=Bad "
                                                                          + "password"};
                case "1003" -> new String[]{"User logged out", "1", "suser=" + user + " src=" + src
                                                                   + " act=logout msg=Session ended"};
                case "2001" -> new String[]{"Web request allowed", "1", "suser=" + user + " src=" + src + " dst="
                                                                       + dst + " dpt=" + port + " request=" + url
                                                                       + " act=allow cat=" + cat};
                case "2002" -> new String[]{"Web request blocked", "5", "suser=" + user + " src=" + src + " dst="
                                                                       + dst + " dpt=" + port + " request=" + url
                                                                       + " act=block cat=malware"};
                default -> new String[]{"Malware detected", "9", "suser=" + user + " src=" + src
                                                                + " fname=update.bin "
                                                                + "fileHash=44d88612fea8a8f36de82e1278abb02f "
                                                                + "act=quarantine msg=EICAR test file"};
            };
            final String host = "3001".equals(kind)
                    ? "swg02"
                    : "swg01";
            lines.add("<134>" + format(at, "MMM") + " " + day(at) + " " + format(at, "HH:mm:ss") + " " + host
                      + " CEF:0|Acme|SecureGateway|5.2|" + kind + "|" + chosen[0] + "|" + chosen[1] + "|"
                      + chosen[2]);
        }
        return lines(lines);
    }

    private static String jsonAlertsOneLine(final int records, final int seed, final int plainHead) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = LocalDateTime.of(2026, 9, 28, 10, 0, 0);
        final List<List<Object>> sigs = List.of(List.of("ET SCAN Nmap Scripting Engine", 2),
                List.of("ET POLICY curl User-Agent", 3));
        final List<List<Object>> late = concat(sigs, List.of(List.of("ET EXPLOIT Possible SQL injection", 1),
                List.of("ET MALWARE Known C2 beacon", 1)));
        final List<Object> alerts = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final List<Object> sig = n < plainHead
                    ? rng.choice(sigs)
                    : late.get(n % late.size());
            final Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("ts", format(start.plusSeconds(13L * n), "yyyy-MM-dd'T'HH:mm:ss") + "Z");
            alert.put("sensor", "ids-" + (1 + n % 2));
            alert.put("sig", sig.get(0));
            alert.put("sev", sig.get(1));
            alert.put("src", "10.1." + rng.randint(0, 9) + "." + rng.randint(2, 250));
            alert.put("dst", rng.choice(List.of("10.1.1.20", "93.184.216.34", "10.1.2.10")));
            alert.put("dport", rng.choice(List.of(22, 80, 443)));
            alerts.add(alert);
        }
        return dumps(alerts, 0) + "\n";
    }

    private static String bluecoatAccess(final int records, final int seed, final int restartAt) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<String> first = List.of(("date time time-taken c-ip cs-username cs-auth-group x-exception-id "
                                            + "sc-filter-result cs-categories cs(Referer) sc-status s-action "
                                            + "cs-method rs(Content-Type) cs-uri-scheme cs-host cs-uri-port "
                                            + "cs-uri-path cs-uri-query cs(User-Agent) s-ip sc-bytes cs-bytes")
                .split(" "));
        final List<String> second = List.of("date", "time", "c-ip", "cs-username", "cs-auth-group",
                "sc-filter-result", "x-exception-id", "cs-categories", "sc-status", "s-action", "cs-method",
                "cs-uri-scheme", "cs-host", "cs-uri-port", "cs-uri-path", "cs-uri-query", "cs(User-Agent)",
                "x-virus-id", "s-ip", "sc-bytes", "cs-bytes", "time-taken", "cs(Referer)", "rs(Content-Type)");
        final List<List<String>> users = List.of(List.of("jsmith", "Employees"), List.of("admin", "Administrators"),
                List.of("guest", "Guests"), List.of("jbloggs", "Employees"));
        final List<List<String>> sites = List.of(
                List.of("www.example.com", "/index.html", "\"Business/Economy\"", "text/html"),
                List.of("api.example.com", "/v1/status", "\"Technology/Internet\"", "application/json"),
                List.of("malware.example.net", "/payload.exe", "\"Malicious Sources\"", "application/octet-stream"),
                List.of("news.example.org", "/today", "\"News/Media\"", "text/html"));
        final List<String> lines = new ArrayList<>(bluecoatHeader("SGOS 6.7.5.12", first, start));
        List<String> columns = first;
        for (int n = 0; n < records; n++) {
            final LocalDateTime at = start.plusSeconds(3L * n);
            if (n == restartAt) {
                lines.add("");
                lines.addAll(bluecoatHeader("SGOS 7.3.1", second, at));
                columns = second;
            }
            final List<String> userGroup = rng.choice(users);
            final String user = userGroup.get(0);
            final List<String> site = rng.choice(sites);
            final String host = site.get(0);
            final String category = site.get(2);
            final boolean denied = category.contains("Malicious") || ("guest".equals(user) && rng.random() < 0.5);
            final Map<String, String> row = new LinkedHashMap<>();
            row.put("date", format(at, "yyyy-MM-dd"));
            row.put("time", format(at, "HH:mm:ss"));
            row.put("time-taken", String.valueOf(rng.randint(1, 900)));
            row.put("c-ip", "192.168.1." + (100 + rng.randint(0, 50)));
            row.put("cs-username", user);
            row.put("cs-auth-group", userGroup.get(1));
            row.put("x-exception-id", denied
                    ? "policy_denied"
                    : "-");
            row.put("sc-filter-result", denied
                    ? "DENIED"
                    : "OBSERVED");
            row.put("cs-categories", category);
            row.put("cs(Referer)", "-");
            row.put("sc-status", denied
                    ? "403"
                    : "200");
            row.put("s-action", denied
                    ? "TCP_DENIED"
                    : rng.choice(List.of("TCP_NC_MISS", "TCP_HIT")));
            row.put("cs-method", rng.choice(List.of("GET", "GET", "POST")));
            row.put("rs(Content-Type)", site.get(3));
            row.put("cs-uri-scheme", host.contains("malware")
                    ? "http"
                    : "https");
            row.put("cs-host", host);
            row.put("cs-uri-port", host.contains("malware")
                    ? "80"
                    : "443");
            row.put("cs-uri-path", site.get(1));
            row.put("cs-uri-query", "-");
            row.put("cs(User-Agent)", rng.choice(List.of("\"Mozilla/5.0 (Windows NT 10.0; Win64; x64)\"",
                    "\"curl/8.4.0\"")));
            row.put("x-virus-id", host.contains("malware")
                    ? "EICAR-Test-File"
                    : "-");
            row.put("s-ip", "192.168.1.10");
            row.put("sc-bytes", String.valueOf(rng.randint(200, 90000)));
            row.put("cs-bytes", String.valueOf(rng.randint(200, 3000)));
            lines.add(columns.stream().map(row::get).collect(Collectors.joining(" ")));
        }
        return lines(lines);
    }

    private static List<String> bluecoatHeader(final String software,
                                               final List<String> columns,
                                               final LocalDateTime at) {
        return List.of("#Software: " + software, "#Version: 1.0",
                "#Start-Date: " + format(at, "yyyy-MM-dd HH:mm:ss"), "#Date: " + format(at, "yyyy-MM-dd HH:mm:ss"),
                "#Fields: " + String.join(" ", columns), "#Remark: 4111140007 \"proxy01\" \"192.168.1.10\" \"main\"");
    }

    private static String cloudtrail(final int records, final int seed, final int plainHead) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<String> users = List.of("alice", "bob", "carol");
        final List<String> early = List.of("ConsoleLogin", "GetObject");
        final List<String> late = concat(early, List.of("DeleteObject", "CreateUser", "PutBucketPolicy",
                "GetObject"));
        final List<Object> out = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final String name = n < plainHead
                    ? rng.choice(early)
                    : late.get(n % late.size());
            final String user = rng.choice(users);
            final Map<String, Object> identity = new LinkedHashMap<>();
            identity.put("type", "IAMUser");
            identity.put("principalId", String.format("AIDA%08d", n));
            identity.put("userName", user);
            identity.put("arn", "arn:aws:iam::123456789012:user/" + user);
            identity.put("accountId", "123456789012");
            final Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventVersion", "1.08");
            event.put("userIdentity", identity);
            event.put("eventTime", format(start.plusSeconds(11L * n), "yyyy-MM-dd'T'HH:mm:ss") + "Z");
            event.put("eventSource", switch (name) {
                case "ConsoleLogin" -> "signin.amazonaws.com";
                case "CreateUser" -> "iam.amazonaws.com";
                default -> "s3.amazonaws.com";
            });
            event.put("eventName", name);
            event.put("awsRegion", "us-east-1");
            event.put("sourceIPAddress", !"PutBucketPolicy".equals(name)
                    ? rng.choice(List.of("203.0.113.5", "198.51.100.7"))
                    : "cloudformation.amazonaws.com");
            event.put("userAgent", "aws-cli/2.15.0");
            event.put("eventID", String.format("0f%030d", n));
            event.put("eventType", "AwsApiCall");
            event.put("recipientAccountId", "123456789012");
            if ("ConsoleLogin".equals(name)) {
                event.put("eventType", "AwsConsoleSignIn");
                event.put("responseElements", Map.of("ConsoleLogin", n % 9 == 0
                        ? "Failure"
                        : "Success"));
            } else if (List.of("GetObject", "DeleteObject", "PutBucketPolicy").contains(name)) {
                final Map<String, Object> parameters = new LinkedHashMap<>();
                parameters.put("bucketName", rng.choice(List.of("finance", "hr")));
                parameters.put("key", "reports/q" + (n % 4 + 1) + ".xlsx");
                event.put("requestParameters", parameters);
            } else if ("CreateUser".equals(name)) {
                event.put("requestParameters", Map.of("userName", "svc-" + n));
            }
            if (n >= plainHead && n % 13 == 0) {
                event.put("errorCode", "AccessDenied");
                event.put("errorMessage", "User is not authorized to perform this action");
            }
            out.add(event);
        }
        return dumps(Map.of("Records", out), 2) + "\n";
    }

    private static String javaAppLog(final int records, final int seed, final int plainHead) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<String> users = List.of("alice", "bob", "carol", "dave");
        final List<String> early = List.of("login", "invoice", "logout");
        final List<String> late = concat(early, List.of("failed", "payment", "config"));
        final List<String> frames = List.of("java.base/java.net.SocketInputStream.socketRead0(Native Method)",
                "java.base/java.net.SocketInputStream.read(SocketInputStream.java:168)",
                "com.acme.billing.PaymentGateway.charge(PaymentGateway.java:88)",
                "com.acme.billing.InvoiceService.settle(InvoiceService.java:142)",
                "org.springframework.aop.framework.ReflectiveMethodInvocation.proceed("
                + "ReflectiveMethodInvocation.java:186)");
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final LocalDateTime at = start.plus(Duration.ofMillis(2731L * n));
            final String stamp = format(at, "yyyy-MM-dd HH:mm:ss") + "," + String.format("%03d", millis(at));
            final String kind = n < plainHead
                    ? rng.choice(early)
                    : late.get(n % late.size());
            final String user = rng.choice(users);
            final String thread = "http-nio-8080-exec-" + rng.randint(1, 9);
            switch (kind) {
                case "login" -> lines.add(stamp + " INFO  [" + thread + "] com.acme.auth.LoginController - User "
                                          + user + " logged in from 10.0." + rng.randint(0, 9) + "."
                                          + rng.randint(2, 250));
                case "logout" -> lines.add(stamp + " INFO  [" + thread + "] com.acme.auth.LoginController - User "
                                           + user + " logged out");
                case "invoice" -> lines.add(stamp + " INFO  [" + thread + "] com.acme.billing.InvoiceService - "
                                            + "Invoice " + (1000 + n) + " created for customer " + user);
                case "failed" -> lines.add(stamp + " WARN  [" + thread + "] com.acme.auth.LoginController - Login "
                                           + "failed for user " + user + " from 203.0.113." + rng.randint(2, 250)
                                           + ": bad credentials");
                case "config" -> lines.add(stamp + " INFO  [main] com.acme.admin.ConfigService - Setting "
                                           + "session.timeout changed from " + rng.choice(List.of(15, 20))
                                           + " to 30 by admin");
                default -> {
                    lines.add(stamp + " ERROR [" + thread + "] com.acme.billing.PaymentGateway - Payment failed for "
                              + "invoice " + (1000 + n) + " (customer " + user + ")");
                    lines.add("java.net.SocketTimeoutException: Read timed out");
                    frames.subList(0, rng.randint(3, 5)).forEach(frame -> lines.add("\tat " + frame));
                    lines.add("Caused by: java.io.IOException: Connection reset by peer");
                    frames.subList(0, rng.randint(1, 3)).forEach(frame -> lines.add("\tat " + frame));
                    lines.add("\t... " + rng.randint(10, 60) + " more");
                }
            }
        }
        return lines(lines);
    }

    private static String ticketCsv(final int records, final int seed) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<String> users = List.of("alice", "bob", "carol", "dave");
        final List<String> lines = new ArrayList<>(List.of("time,ticket,user,action,priority,comment"));
        for (int n = 0; n < records; n++) {
            final String action = n >= records / 3
                    ? List.of("create", "update", "close", "reopen").get(n % 4)
                    : rng.choice(List.of("create", "update"));
            final String comment = rng.choice(List.of("", "printer offline, again",
                    "user said \"\"it worked yesterday\"\"", "restarted the spooler\nand cleared the queue",
                    "escalated to network\nsee INC-2201\nwaiting"));
            final String quoted = !comment.isEmpty() && (comment.contains(",") || comment.contains("\"")
                                                         || comment.contains("\n"))
                    ? "\"" + comment + "\""
                    : comment;
            lines.add(format(start.plusSeconds(37L * n), "yyyy-MM-dd'T'HH:mm:ss") + "Z,TCK-" + (1000 + n % 300) + ","
                      + rng.choice(users) + "," + action + "," + rng.choice(List.of("P1", "P2", "P3")) + ","
                      + quoted);
        }
        return lines(lines);
    }

    private static String paloAltoTraffic(final int records, final int seed, final int plainHead) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<String> users = List.of("corp\\alice", "corp\\bob", "corp\\carol", "");
        final List<List<String>> later = List.of(List.of("end", "allow"), List.of("deny", "deny"),
                List.of("drop", "drop"), List.of("end", "reset-both"));
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final LocalDateTime at = start.plusSeconds(5L * n);
            final String stamp = format(at, "yyyy/MM/dd HH:mm:ss");
            final List<String> subtypeAction = n < plainHead
                    ? List.of("end", "allow")
                    : later.get(n % 4);
            final String action = subtypeAction.get(1);
            final int dport = rng.choice(List.of(443, 80, 22, 3389));
            final Map<String, String> row = new LinkedHashMap<>();
            row.put("future_use1", "1");
            row.put("receive_time", stamp);
            row.put("serial", "012801000001");
            row.put("type", "TRAFFIC");
            row.put("subtype", subtypeAction.get(0));
            row.put("future_use2", "2561");
            row.put("generated_time", stamp);
            row.put("src", "10.0." + rng.randint(0, 9) + "." + rng.randint(2, 250));
            row.put("dst", rng.choice(List.of("93.184.216.34", "198.51.100.40", "203.0.113.80")));
            row.put("natsrc", "203.0.113.1");
            row.put("natdst", "0.0.0.0");
            row.put("rule", "allow".equals(action)
                    ? "allow-web"
                    : "block-risky");
            row.put("srcuser", rng.choice(users));
            row.put("dstuser", "");
            row.put("app", switch (dport) {
                case 443 -> "ssl";
                case 80 -> "web-browsing";
                case 22 -> "ssh";
                default -> "ms-rdp";
            });
            row.put("vsys", "vsys1");
            row.put("from", "trust");
            row.put("to", "untrust");
            row.put("inbound_if", "ethernet1/2");
            row.put("outbound_if", "ethernet1/1");
            row.put("logset", "log-fwd");
            row.put("future_use3", stamp);
            row.put("sessionid", String.valueOf(40000 + n));
            row.put("repeatcnt", "1");
            row.put("sport", String.valueOf(rng.randint(40000, 65000)));
            row.put("dport", String.valueOf(dport));
            row.put("natsport", "0");
            row.put("natdport", "0");
            row.put("flags", "0x400000");
            row.put("proto", "tcp");
            row.put("action", action);
            row.put("bytes", String.valueOf(rng.randint(0, 90000)));
            row.put("bytes_sent", String.valueOf(rng.randint(0, 9000)));
            row.put("bytes_received", String.valueOf(rng.randint(0, 80000)));
            row.put("packets", String.valueOf(rng.randint(1, 90)));
            row.put("start", stamp);
            row.put("elapsed", String.valueOf(rng.randint(0, 600)));
            row.put("category", rng.choice(List.of("any", "news", "malware")));
            row.put("future_use4", "0");
            row.put("seqno", String.valueOf(1000 + n));
            row.put("actionflags", "0x0");
            row.put("srcloc", "10.0.0.0-10.255.255.255");
            row.put("dstloc", "United States");
            row.put("future_use5", "0");
            row.put("pkts_sent", String.valueOf(rng.randint(1, 40)));
            row.put("pkts_received", String.valueOf(rng.randint(0, 50)));
            row.put("session_end_reason", switch (action) {
                case "allow" -> "tcp-fin";
                case "deny", "drop" -> "policy-deny";
                default -> "threat";
            });
            lines.add("<14>" + format(at, "MMM") + " " + day(at) + " " + format(at, "HH:mm:ss") + " pa-fw01 "
                      + PAN_TRAFFIC.stream().map(row::get).collect(Collectors.joining(",")));
        }
        return lines(lines);
    }

    private static String tildeAudit(final int records, final int seed) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final String action = rng.choice(List.of("LOGIN", "LOGIN", "LOGOUT", "PASSWORD_CHANGE"));
            final String result = "LOGIN".equals(action) && rng.random() < 0.2
                    ? "FAIL"
                    : "OK";
            lines.add(format(start.plusSeconds(19L * n), "yyyy-MM-dd HH:mm:ss") + " ~ "
                      + rng.choice(List.of("alice", "bob", "carol")) + " ~ " + action + " ~ " + result + " ~ 10.0."
                      + rng.randint(0, 9) + "." + rng.randint(2, 250));
        }
        return lines(lines);
    }

    private static String gelfApp(final int records, final int seed) {
        final SeededRandom rng = new SeededRandom(seed);
        final double base = 1791676800.0;
        final List<String> out = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final String kind = rng.choice(List.of("login", "logout", "disk"));
            final Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("version", "1.1");
            msg.put("host", rng.choice(List.of("app01", "app02")));
            // round(base + 3.7 * n, 3): the exact binary value rounded half to even.
            msg.put("timestamp", new BigDecimal(base + 3.7 * n).setScale(3, RoundingMode.HALF_EVEN).doubleValue());
            final String user = rng.choice(List.of("alice", "bob", "carol"));
            switch (kind) {
                case "login" -> {
                    msg.put("short_message", "User login");
                    msg.put("level", 6);
                    msg.put("_user", user);
                    msg.put("_action", "login");
                    msg.put("_src_ip", "10.0.0." + rng.randint(2, 250));
                }
                case "logout" -> {
                    msg.put("short_message", "User logout");
                    msg.put("full_message", "User " + user + " logged out\nsession " + n);
                    msg.put("level", 6);
                    msg.put("_user", user);
                    msg.put("_action", "logout");
                }
                default -> {
                    msg.put("short_message", "Disk almost full");
                    msg.put("level", 4);
                    msg.put("_disk", "/var");
                    msg.put("_pct", rng.randint(85, 99));
                    msg.put("_action", "disk_alert");
                }
            }
            out.add(dumps(msg, 0));
        }
        return lines(out);
    }

    private static String ecsJson(final int records, final int seed) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<String> out = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final String kind = rng.choice(List.of("login", "login", "file-delete"));
            final Map<String, Object> event = new LinkedHashMap<>();
            event.put("@timestamp", format(start.plusSeconds(13L * n), "yyyy-MM-dd'T'HH:mm:ss") + ".000Z");
            event.put("ecs", Map.of("version", "8.11.0"));
            event.put("host", Map.of("name", rng.choice(List.of("ws01", "fs01"))));
            event.put("user", Map.of("name", rng.choice(List.of("alice", "bob"))));
            final Map<String, Object> what = new LinkedHashMap<>();
            if ("login".equals(kind)) {
                final boolean ok = rng.random() > 0.2;
                what.put("category", List.of("authentication"));
                what.put("type", List.of("start"));
                what.put("action", "user-login");
                what.put("outcome", ok
                        ? "success"
                        : "failure");
                event.put("event", what);
                event.put("source", Map.of("ip", "10.0.0." + rng.randint(2, 250)));
            } else {
                what.put("category", List.of("file"));
                what.put("type", List.of("deletion"));
                what.put("action", "file-delete");
                what.put("outcome", "success");
                event.put("event", what);
                final Map<String, Object> file = new LinkedHashMap<>();
                file.put("path", "/srv/finance/q" + (n % 4 + 1) + ".xlsx");
                file.put("name", "q" + (n % 4 + 1) + ".xlsx");
                event.put("file", file);
            }
            out.add(dumps(event, 0));
        }
        return lines(out);
    }

    private static String fortiosNative(final int records, final int seed) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final LocalDateTime at = start.plusSeconds(9L * n);
            final String head = "date=" + format(at, "yyyy-MM-dd") + " time=" + format(at, "HH:mm:ss")
                                + " devname=\"FGT60F\" devid=\"FGT60FTK2000001\" eventtime="
                                + (at.toEpochSecond(ZoneOffset.UTC) * 1_000_000_000L + n) + " tz=\"+0000\"";
            final String kind = rng.choice(List.of("traffic", "traffic", "admin", "ips"));
            switch (kind) {
                case "traffic" -> lines.add(head + " logid=\"0000000013\" type=\"traffic\" subtype=\"forward\" "
                                            + "level=\"notice\" vd=\"root\" srcip=10.0.0." + rng.randint(2, 250)
                                            + " srcport=" + rng.randint(40000, 65000) + " dstip=93.184.216.34 "
                                            + "dstport=443 action=\"" + rng.choice(List.of("accept", "deny"))
                                            + "\" policyid=1 service=\"HTTPS\" user=\""
                                            + rng.choice(List.of("alice", "bob")) + "\"");
                case "admin" -> {
                    final boolean ok = rng.random() > 0.3;
                    lines.add(head + " logid=\"0100032001\" type=\"event\" subtype=\"system\" level=\"information\" "
                              + "vd=\"root\" logdesc=\"Admin login " + (ok
                            ? "successful"
                            : "failed") + "\" user=\"admin\" ui=\"https(10.0.0.9)\" action=\"login\" status=\""
                              + (ok
                            ? "success"
                            : "failed") + "\" msg=\"Administrator admin " + (ok
                            ? "logged in successfully"
                            : "login failed") + " from https(10.0.0.9)\"");
                }
                default -> lines.add(head + " logid=\"0419016384\" type=\"utm\" subtype=\"ips\" "
                                     + "eventtype=\"signature\" level=\"alert\" vd=\"root\" severity=\"high\" "
                                     + "srcip=203.0.113." + rng.randint(2, 250) + " dstip=10.0.0.20 "
                                     + "action=\"dropped\" attack=\"SQL.Injection\"");
            }
        }
        return lines(lines);
    }

    private static String damagedJsonl(final int records, final int seed) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final Map<String, Object> event = new LinkedHashMap<>();
            event.put("time", format(start.plusSeconds(17L * n), "yyyy-MM-dd'T'HH:mm:ss") + "Z");
            event.put("user", rng.choice(List.of("alice", "bob", "carol")));
            event.put("action", rng.choice(List.of("login", "login", "logout", "password_change")));
            event.put("result", rng.choice(List.of("success", "success", "failure")));
            event.put("ip", "10.0." + rng.randint(0, 9) + "." + rng.randint(2, 250));
            String line = dumps(event, 0);
            final String damage = DAMAGED_AT.get(n);
            final String user = (String) event.get("user");
            if ("cut".equals(damage)) {
                line = line.substring(0, line.length() / 2);
            } else if ("garbage".equals(damage)) {
                line = "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space";
            } else if ("quote".equals(damage)) {
                line = line.replace("\"user\": \"" + user + "\"", "\"user\": \"" + user.charAt(0) + "\""
                                                                  + user.substring(1) + "\"");
            }
            lines.add(line);
        }
        return lines(lines);
    }

    private static String soapGateway(final int records, final int seed) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final String operation = rng.choice(List.of("Login", "Login", "GetAccount", "UpdateAccount"));
            final String user = rng.choice(List.of("alice", "bob", "carol"));
            final boolean ok = rng.random() > 0.15;
            final String body = "<acme:" + operation + " xmlns:acme=\"urn:acme:accounts:v2\"><acme:User>" + user
                                + "</acme:User>" + (!"Login".equals(operation)
                    ? "<acme:Account id=\"ACC-" + (1000 + n % 50) + "\"/>"
                    : "") + "<acme:Result>" + (ok
                    ? "success"
                    : "failure") + "</acme:Result></acme:" + operation + ">";
            final String payload = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                                   + "<soap:Header/><soap:Body>" + body + "</soap:Body></soap:Envelope>";
            final Map<String, Object> line = new LinkedHashMap<>();
            line.put("time", format(start.plusSeconds(23L * n), "yyyy-MM-dd'T'HH:mm:ss") + "Z");
            line.put("client_ip", "10.0." + rng.randint(0, 9) + "." + rng.randint(2, 250));
            line.put("service", "accounts");
            line.put("operation", operation);
            line.put("status", ok
                    ? 200
                    : 403);
            line.put("payload", payload);
            lines.add(dumps(line, 0));
        }
        return lines(lines);
    }

    private static String mailGateway(final int records, final int seed) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<String> people = List.of("bob@example.com", "carol@example.com", "dave@example.org",
                "erin@example.net");
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final int count = rng.randint(1, 3);
            final List<String> recipients = rng.sample(people, count);
            final String action = rng.choice(List.of("deliver", "deliver", "deliver", "quarantine", "reject"));
            lines.add("time=" + format(start.plusSeconds(29L * n), "yyyy-MM-dd'T'HH:mm:ss") + "Z host=mx01 msgid=<"
                      + n + "@mail.example.com> from=" + rng.choice(List.of("alice@example.com",
                    "frank@example.com")) + " " + recipients.stream()
                              .map(r -> "rcpt=" + r)
                              .collect(Collectors.joining(" "))
                      + " subject=\"" + rng.choice(List.of("Q3 report", "Lunch?", "Invoice 4471")) + "\" size="
                      + rng.randint(800, 900000) + " action=" + action);
        }
        return lines(lines);
    }

    private static String unicodeApp(final int records, final int seed) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<List<String>> users = List.of(List.of("jalvarez", "José Álvarez"), List.of("zoe", "Zoë Brontë"),
                List.of("tyamada", "山田太郎"), List.of("eleni", "Ελένη Παππά"));
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final List<String> uidName = rng.choice(users);
            final String name = uidName.get(1);
            final boolean ok = rng.random() > 0.2;
            final String colour = ok
                    ? "\u001b[32m"
                    : "\u001b[31m";
            final String word = ok
                    ? "OK"
                    : "FAILED";
            lines.add("time=" + format(start.plusSeconds(31L * n), "yyyy-MM-dd'T'HH:mm:ss") + "Z user="
                      + uidName.get(0) + " name=\"" + name + "\" action=" + rng.choice(List.of("login", "logout"))
                      + " result=" + (ok
                    ? "success"
                    : "failure") + " msg=\"" + colour + word + "\u001b[0m for " + name + "\"");
        }
        return "﻿" + lines(lines);
    }

    private static String postgresLog(final int records, final int seed) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final List<String> users = List.of("alice", "bob", "svc_report");
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final LocalDateTime at = start.plus(Duration.ofMillis(4137L * n));
            final String stamp = format(at, "yyyy-MM-dd HH:mm:ss") + "." + String.format("%03d", millis(at)) + " UTC";
            final String user = rng.choice(users);
            final int pid = 4000 + rng.randint(0, 50);
            final String kind = rng.choice(List.of("connect", "select", "select", "update", "delete", "disconnect"));
            final String head = stamp + " [" + pid + "] " + user + "@finance";
            switch (kind) {
                case "connect" -> lines.add(head + " LOG:  connection authorized: user=" + user
                                            + " database=finance application_name=psql");
                case "disconnect" -> lines.add(head + " LOG:  disconnection: session time: 0:0" + rng.randint(1, 9)
                                               + ":12.345 user=" + user + " database=finance host=10.0.0."
                                               + rng.randint(2, 250));
                case "select" -> lines.addAll(List.of(head + " LOG:  statement: SELECT a.id, a.name, sum(t.amount)",
                        "\tFROM accounts a", "\tJOIN transactions t ON t.account_id = a.id",
                        "\tWHERE a.region = 'north' AND t.day = '2026-10-1" + (n % 9) + "'",
                        "\tGROUP BY a.id, a.name;"));
                case "update" -> {
                    lines.add(head + " LOG:  statement: UPDATE accounts");
                    lines.add("\tSET credit_limit = " + rng.randint(1, 9) + "000");
                    lines.add("\tWHERE id = " + rng.randint(100, 999) + ";");
                }
                default -> {
                    lines.add(head + " LOG:  statement: DELETE FROM transactions");
                    lines.add("\tWHERE id = " + rng.randint(1000, 9999) + ";");
                }
            }
        }
        return lines(lines);
    }

    private static String criContainerLog(final int records, final int seed) {
        final SeededRandom rng = new SeededRandom(seed);
        final LocalDateTime start = OCT_11;
        final DateTimeFormatter stampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                Locale.ENGLISH);
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < records; n++) {
            final LocalDateTime at = start.plusSeconds(7L * n);
            final String action = rng.choice(List.of("login", "logout", "export"));
            String message = "user=" + rng.choice(List.of("alice", "bob", "carol")) + " action=" + action
                             + " result=" + rng.choice(List.of("success", "success", "failure")) + " pod=api-7d9f-"
                             + (n % 5);
            if ("export".equals(action)) {
                message += " detail=" + "x".repeat(rng.randint(40, 120)) + " rows=" + rng.randint(1, 900);
            }
            final String stream = message.contains("failure")
                    ? "stderr"
                    : "stdout";
            final List<String> parts = new ArrayList<>();
            String rest = message;
            while ("export".equals(action) && rest.length() > 50) {
                final int cut = rng.randint(20, 50);
                parts.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            parts.add(rest);
            for (int i = 0; i < parts.size(); i++) {
                lines.add(at.plusNanos(i * 1000L).format(stampFormat) + " " + stream + " " + (i == parts.size() - 1
                        ? "F"
                        : "P") + " " + parts.get(i));
            }
        }
        return lines(lines);
    }

    // --------------------------------------------------------------------------------
    // Python's ways

    // '\n'.join(lines) + '\n'
    private static String lines(final List<String> lines) {
        return String.join("\n", lines) + "\n";
    }

    private static String format(final LocalDateTime at, final String pattern) {
        return at.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }

    // {at.day:2d}
    private static String day(final LocalDateTime at) {
        return String.format("%2d", at.getDayOfMonth());
    }

    private static int millis(final LocalDateTime at) {
        return at.getNano() / 1_000_000;
    }

    // re.sub, the replacement taken literally.
    private static String sub(final String regex, final String replacement, final String text) {
        return Pattern.compile(regex).matcher(text).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static int intOr(final Map<String, Object> spec, final String key, final int otherwise) {
        return spec.get(key) instanceof final Number number
                ? number.intValue()
                : otherwise;
    }

    private static <T> List<T> concat(final List<T> first, final List<T> second) {
        final List<T> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }

    private static void write(final StringBuilder sb, final Object value, final int indent, final int depth) {
        switch (value) {
            case null -> sb.append("null");
            case final String text -> writeString(sb, text);
            case final Boolean bool -> sb.append(bool
                    ? "true"
                    : "false");
            case final Double number -> sb.append(pyFloat(number));
            case final Number number -> sb.append(number);
            case final Map<?, ?> map -> {
                if (map.isEmpty()) {
                    sb.append("{}");
                    return;
                }
                sb.append('{');
                boolean first = true;
                for (final Map.Entry<?, ?> entry : map.entrySet()) {
                    separate(sb, first, indent, depth + 1);
                    first = false;
                    writeString(sb, String.valueOf(entry.getKey()));
                    sb.append(": ");
                    write(sb, entry.getValue(), indent, depth + 1);
                }
                close(sb, indent, depth, '}');
            }
            case final List<?> list -> {
                if (list.isEmpty()) {
                    sb.append("[]");
                    return;
                }
                sb.append('[');
                boolean first = true;
                for (final Object item : list) {
                    separate(sb, first, indent, depth + 1);
                    first = false;
                    write(sb, item, indent, depth + 1);
                }
                close(sb, indent, depth, ']');
            }
            default -> throw new IllegalArgumentException("Not JSON: " + value.getClass());
        }
    }

    private static void separate(final StringBuilder sb, final boolean first, final int indent, final int depth) {
        if (indent > 0) {
            if (!first) {
                sb.append(',');
            }
            sb.append('\n').append(" ".repeat(indent * depth));
        } else if (!first) {
            sb.append(", ");
        }
    }

    private static void close(final StringBuilder sb, final int indent, final int depth, final char bracket) {
        if (indent > 0) {
            sb.append('\n').append(" ".repeat(indent * depth));
        }
        sb.append(bracket);
    }

    // ensure_ascii: escapes as json.dumps writes them.
    private static void writeString(final StringBuilder sb, final String text) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // repr(float), for the values the generators write (no exponent).
    private static String pyFloat(final double value) {
        final String plain = new BigDecimal(Double.toString(value)).toPlainString();
        return plain.contains(".")
                ? plain
                : plain + ".0";
    }
}
