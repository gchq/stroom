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

import stroom.mcp.impl.e2e.InsiderDataset.Act;
import stroom.mcp.impl.e2e.InsiderDataset.Person;
import stroom.mcp.impl.e2e.InsiderDataset.Source;
import stroom.util.json.JsonUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// The insider-risk dataset's events ([InsiderDataset]) as each consumer takes them: an Elasticsearch document per
/// event (ECS names, the person id as `user.id`, the account normalised as `user.name`: lower case, the domain
/// stripped), an Events XML event (the account as the source wrote it, the person id and `Organisation` under
/// `EventSource/User/UserDetails`), and the raw files of the dev node's pipelines: an invented endpoint agent format,
/// the web proxy as Netskope CSV, the VPN as NetScaler syslog, the cloud audit log as Azure (Office 365) audit JSON,
/// privileged sessions as Delinea Secret Server event XML.
final class InsiderRecords {

    /// The raw files' folder under the dev node's data.
    static final String DEV_FOLDER = "insider-dataset";
    /// The sample whose header the proxy's CSV takes.
    static final String NETSKOPE_SAMPLE = "netskope-stress/netskope_stress_one_part.csv";
    /// The columns of [#flat], each with its type as an eval case's `fields` give it.
    static final Map<String, String> FLAT_COLUMNS = linked("EventTime", "date", "Source", "text", "PersonId",
            "text", "UserName", "text", "Account", "text", "HostName", "text", "EventDetail", "text", "Action",
            "text", "EventCode", "text", "Process", "text", "WindowTitle", "text", "WindowId", "text", "Object",
            "text", "Destination", "text", "Category", "text", "BytesOut", "long", "SessionId", "text", "Country",
            "text", "Site", "text");

    /// The fields every source's documents have, by their Elasticsearch type.
    private static final Map<String, String> COMMON = linked(
            "@timestamp", "date",
            "user.id", "keyword",
            "user.name", "keyword",
            "host.name", "keyword",
            "event.code", "keyword",
            "event.action", "keyword",
            "event.dataset", "keyword",
            "StreamId", "long",
            "EventId", "long");
    private static final Map<Source, Map<String, String>> FIELDS = Map.of(
            Source.ENDPOINT, linked("process.pid", "long", "process.command_line", "keyword", "process.name",
                    "keyword", "window.title", "keyword", "window.id", "keyword", "file.path", "keyword", "file.name",
                    "keyword", "file.size", "long", "file.hash.sha256", "keyword", "device.name", "keyword"),
            Source.PROXY, linked("destination.domain", "keyword", "destination.ip", "ip", "url.original", "keyword",
                    "url.category", "keyword", "http.request.method", "keyword", "source.bytes", "long",
                    "destination.bytes", "long", "process.name", "keyword", "network.access", "keyword"),
            Source.VPN, linked("session.id", "keyword", "source.ip", "ip", "source.geo.country_iso_code", "keyword",
                    "source.bytes", "long", "destination.bytes", "long"),
            Source.CLOUD, linked("source.ip", "ip", "source.geo.country_iso_code", "keyword", "file.name", "keyword",
                    "file.size", "long", "cloud.site", "keyword", "record.id", "keyword", "cloud.app", "keyword"),
            Source.PAM, linked("session.id", "keyword", "destination.domain", "keyword", "network.protocol",
                    "keyword", "secret.name", "keyword"));
    private static final DateTimeFormatter NETSCALER = DateTimeFormatter.ofPattern("MM/dd/yyyy:HH:mm:ss 'GMT'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DELINEA = DateTimeFormatter.ofPattern("MMM dd yyyy HH:mm:ss",
            Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DELINEA_SYSTEM_TIME = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FLAT_TIME = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AZURE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter NETSKOPE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter NETSKOPE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(InsiderDataset.ZONE);
    private static final DateTimeFormatter ENDPOINT_TIME = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(InsiderDataset.ZONE);
    private static final Set<String> NOT_DATA = Set.of("admin");

    private InsiderRecords() {
        // Static only.
    }

    // ------------------------------------------------------------------------------------------------
    // Elasticsearch

    /// @param source A source.
    /// @return Its index's fields, each with its Elasticsearch type, the common fields first.
    static Map<String, String> mapping(final Source source) {
        final Map<String, String> fields = new LinkedHashMap<>(COMMON);
        fields.putAll(FIELDS.get(source));
        return fields;
    }

    /// @param source A source.
    /// @return Its index's settings and mappings, as `PUT <index>` takes them.
    static String indexBody(final Source source) {
        final Map<String, Object> properties = new LinkedHashMap<>();
        mapping(source).forEach((field, type) -> properties.put(field, Map.of("type", type)));
        return JsonUtil.writeValueAsString(Map.of("settings", Map.of("number_of_shards", 1, "number_of_replicas", 0),
                "mappings", Map.of("properties", properties)), false);
    }

    /// @param act    An event.
    /// @param stream The Events stream its XML is in.
    /// @return Its document: its fields by name, `_id` its number.
    static Map<String, Object> document(final Act act, final long stream) {
        final Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("_id", String.valueOf(act.number()));
        doc.put("@timestamp", Instant.ofEpochMilli(act.time()).toString());
        doc.put("user.id", act.personId());
        doc.put("user.name", normalised(act.account()));
        if (act.host() != null) {
            doc.put("host.name", act.host());
        }
        doc.put("event.code", act.type());
        doc.put("event.action", action(act));
        doc.put("event.dataset", act.source().key());
        doc.put("StreamId", stream);
        doc.put("EventId", act.number());
        switch (act.source()) {
            case ENDPOINT -> {
                putLong(doc, "process.pid", act.field("pid"));
                put(doc, "process.command_line", act.field("command"));
                put(doc, "process.name", act.field("application") != null
                        ? act.field("application")
                        : baseName(act.field("command")));
                put(doc, "window.title", act.field("title"));
                put(doc, "window.id", act.field("window"));
                put(doc, "file.path", act.field("path"));
                put(doc, "file.name", act.field("name"));
                putLong(doc, "file.size", act.field("size"));
                put(doc, "file.hash.sha256", act.field("hash"));
                put(doc, "device.name", act.field("device"));
            }
            case PROXY -> {
                put(doc, "destination.domain", act.field("domain"));
                put(doc, "destination.ip", act.field("ip"));
                put(doc, "url.original", act.field("url"));
                put(doc, "url.category", act.field("category"));
                put(doc, "http.request.method", act.field("method"));
                putLong(doc, "source.bytes", act.field("bytes_out"));
                putLong(doc, "destination.bytes", act.field("bytes_in"));
                put(doc, "process.name", act.field("process"));
                put(doc, "network.access", act.field("access"));
            }
            case VPN -> {
                put(doc, "session.id", act.field("session"));
                put(doc, "source.ip", act.field("ip"));
                put(doc, "source.geo.country_iso_code", act.field("country"));
                putLong(doc, "source.bytes", act.field("bytes_out"));
                putLong(doc, "destination.bytes", act.field("bytes_in"));
            }
            case CLOUD -> {
                put(doc, "source.ip", act.field("ip"));
                put(doc, "source.geo.country_iso_code", act.field("country"));
                put(doc, "file.name", act.field("file"));
                putLong(doc, "file.size", act.field("size"));
                put(doc, "cloud.site", act.field("site"));
                put(doc, "record.id", act.field("record"));
                put(doc, "cloud.app", act.field("app"));
            }
            case PAM -> {
                put(doc, "session.id", act.field("session"));
                put(doc, "destination.domain", act.field("target"));
                put(doc, "network.protocol", act.field("protocol"));
                put(doc, "secret.name", act.field("secret"));
            }
        }
        return doc;
    }

    /// @param account An account as a source writes it.
    /// @return It as the indexing normalises it: lower case, the domain stripped (`CORP\dwhitfield` is
    /// `dwhitfield`, `dana.whitfield@corp.example` is `dana.whitfield`).
    static String normalised(final String account) {
        String user = account.substring(account.lastIndexOf('\\') + 1);
        final int at = user.indexOf('@');
        if (at > 0) {
            user = user.substring(0, at);
        }
        return user.toLowerCase(Locale.ROOT);
    }

    /// An event as one flat record of every source's (an eval question's one index): its time, source, person id,
    /// account (as written and normalised), host, EventDetail element and Action, code, and what it touched.
    ///
    /// @param act An event.
    /// @return Its values by column ([#FLAT_COLUMNS]), those it has only; `EventTime` in UTC to the millisecond.
    static Map<String, String> flat(final Act act) {
        final Map<String, String> record = new LinkedHashMap<>();
        record.put("EventTime", FLAT_TIME.format(Instant.ofEpochMilli(act.time())));
        record.put("Source", act.source().key());
        record.put("PersonId", act.personId());
        record.put("UserName", normalised(act.account()));
        record.put("Account", act.account());
        putText(record, "HostName", act.host());
        record.put("EventDetail", element(act.type()));
        record.put("Action", action(act));
        record.put("EventCode", act.type());
        putText(record, "Process", act.field("application") != null
                ? act.field("application")
                : baseName(act.field("command")));
        putText(record, "WindowTitle", act.field("title"));
        putText(record, "WindowId", act.field("window"));
        putText(record, "Object", firstOf(act.field("url"), act.field("name"), act.field("file"),
                act.field("record"), act.field("secret"), act.field("device"), act.field("log")));
        putText(record, "Destination", firstOf(act.field("domain"), act.field("target")));
        putText(record, "Category", act.field("category"));
        putText(record, "BytesOut", act.field("bytes_out"));
        putText(record, "SessionId", act.field("session"));
        putText(record, "Country", act.field("country"));
        putText(record, "Site", act.field("site"));
        return record;
    }

    /// @param type An event's type.
    /// @return The EventDetail element it is written as.
    static String element(final String type) {
        return switch (type) {
            case "logon", "logoff", "lock", "unlock", "signin", "vpn.login", "vpn.logout", "pam.session.start",
                 "pam.session.end" -> "Authenticate";
            case "proc.start", "proc.stop" -> "Process";
            case "file.write" -> "Update";
            case "file.archive", "usb.insert" -> "Create";
            case "usb.copy" -> "Copy";
            case "log.clear" -> "Delete";
            case "web" -> "Network";
            case "download" -> "Export";
            default -> "View";
        };
    }

    // ------------------------------------------------------------------------------------------------
    // Events XML

    /// @param act An event.
    /// @return Its `Event` element, as the dev node's translation would write it.
    static String eventXml(final Act act) {
        final StringBuilder sb = new StringBuilder("<Event><EventTime><TimeCreated>")
                .append(Instant.ofEpochMilli(act.time())).append("</TimeCreated></EventTime><EventSource><System>"
                                                                  + "<Name>").append(systemName(act.source()))
                .append("</Name><Environment>Production</Environment></System><Generator>insider-dataset"
                        + "</Generator>");
        if (act.host() != null) {
            sb.append("<Device><HostName>").append(escape(act.host())).append("</HostName></Device>");
        }
        if (act.field("ip") != null && act.source() != Source.PROXY) {
            sb.append("<Client><IPAddress>").append(act.field("ip")).append("</IPAddress></Client>");
        }
        sb.append("<User><Id>").append(escape(act.account())).append("</Id><UserDetails><Id>").append(act.personId())
                .append("</Id><Organisation>").append(escape(act.organisation()))
                .append("</Organisation></UserDetails></User></EventSource><EventDetail><TypeId>").append(act.type())
                .append("</TypeId>");
        sb.append(detail(act));
        return sb.append("</EventDetail></Event>").toString();
    }

    // The action element of an event, by its type.
    private static String detail(final Act act) {
        return switch (act.type()) {
            case "logon", "signin" -> authenticate("Logon", act, List.of("country", "app"));
            case "logoff" -> authenticate("Logoff", act, List.of());
            case "lock" -> authenticate("ScreenLock", act, List.of());
            case "unlock" -> authenticate("ScreenUnlock", act, List.of());
            case "vpn.login" -> authenticate("Connect", act, List.of("session", "country"));
            case "vpn.logout" -> authenticate("Disconnect", act, List.of("session", "bytes_out", "bytes_in"));
            case "pam.session.start", "pam.session.end" -> "<Authenticate>" + data(act, List.of("session", "target",
                    "protocol", "seconds")) + "</Authenticate>";
            case "proc.start", "proc.stop" -> "<Process><Action>" + ("proc.start".equals(act.type())
                    ? "Execute"
                    : "Terminate") + "</Action><Command>" + escape(act.field("command")) + "</Command><ProcessId>"
                                              + act.field("pid") + "</ProcessId></Process>";
            case "focus.in", "focus.out" -> "<View>" + (act.field("title") == null
                    ? ""
                    : "<Document><Title>" + escape(act.field("title")) + "</Title></Document>")
                                            + data(act, List.of("window", "application")) + "</View>";
            case "file.read" -> "<View>" + file(act) + "</View>";
            case "file.write" -> "<Update><After>" + file(act) + "</After></Update>";
            case "file.archive" -> "<Create>" + file(act) + data(act, List.of("files")) + "</Create>";
            case "usb.insert" -> "<Create><Object><Type>Removable media</Type><Name>" + escape(act.field("device"))
                                 + "</Name></Object>" + data(act, List.of("device_id")) + "</Create>";
            case "usb.copy" -> "<Copy><Source><File><Path>" + escape(act.field("path")) + "</Path><Name>"
                               + escape(act.field("name")) + "</Name><Size>" + act.field("size")
                               + "</Size></File></Source><Destination><File><Path>" + escape(act.field("to"))
                               + "</Path></File></Destination></Copy>";
            case "log.clear" -> "<Delete><Object><Type>Event log</Type><Name>" + escape(act.field("log"))
                                + "</Name></Object></Delete>";
            case "web" -> "<Network><Open><Source><Device><HostName>" + escape(act.host())
                          + "</HostName></Device></Source><Destination><Device><HostName>"
                          + escape(act.field("domain")) + "</HostName><IPAddress>" + act.field("ip")
                          + "</IPAddress></Device><Port>443</Port></Destination>"
                          + data(act, List.of("url", "category", "method", "bytes_out", "bytes_in", "access"))
                          + "</Open></Network>";
            case "download" -> "<Export><File><Path>" + escape(act.field("site") + "/" + act.field("file"))
                               + "</Path><Name>" + escape(act.field("file")) + "</Name><Size>" + act.field("size")
                               + "</Size></File>" + data(act, List.of("site")) + "</Export>";
            case "record.view" -> "<View><Resource><Name>" + act.field("record") + "</Name><Type>Customer record"
                                  + "</Type></Resource>" + data(act, List.of("app")) + "</View>";
            case "pam.checkout" -> "<View><Object><Type>Secret</Type><Name>" + escape(act.field("secret"))
                                   + "</Name></Object>" + data(act, List.of("session")) + "</View>";
            default -> throw new IllegalArgumentException("No XML for " + act.type());
        };
    }

    private static String authenticate(final String action, final Act act, final List<String> data) {
        return "<Authenticate><Action>" + action + "</Action><User><Id>" + escape(act.account()) + "</Id></User>"
               + data(act, data) + "</Authenticate>";
    }

    private static String file(final Act act) {
        return "<File><Path>" + escape(act.field("path")) + "</Path><Name>" + escape(act.field("name"))
               + "</Name><Size>" + act.field("size") + "</Size></File>"
               + (act.field("hash") == null
                ? ""
                : "<Data Name=\"Sha256\" Value=\"" + act.field("hash") + "\"/>");
    }

    // Data entries named as the schema's style: SessionId, Window...
    private static String data(final Act act, final List<String> names) {
        final StringBuilder sb = new StringBuilder();
        for (final String name : names) {
            final String value = act.field(name);
            if (value != null && !NOT_DATA.contains(name)) {
                sb.append("<Data Name=\"").append(dataName(name)).append("\" Value=\"").append(escapeAttribute(value))
                        .append("\"/>");
            }
        }
        return sb.toString();
    }

    private static String dataName(final String field) {
        return switch (field) {
            case "session" -> "SessionId";
            case "window" -> "WindowHandle";
            case "bytes_out" -> "BytesOut";
            case "bytes_in" -> "BytesIn";
            case "device_id" -> "DeviceId";
            case "url" -> "URL";
            default -> Character.toUpperCase(field.charAt(0)) + field.substring(1);
        };
    }

    // ------------------------------------------------------------------------------------------------
    // The dev node's raw files

    /// Writes the dev node's raw files, one folder per source and a file per month, the reference data and the known
    /// answer. Made a month at a time (each person-day has its own draw, so the months are the whole's), so the whole
    /// year is never held at once; events are numbered on across the months.
    ///
    /// @param options  What to make; its days are left out, every day of the year made.
    /// @param dir      Where (made if missing).
    /// @param netskope The Netskope sample whose header the proxy's CSV takes.
    /// @return Each file written, by its path, with its size in bytes and its events.
    @SuppressWarnings("unchecked")
    static Map<Path, long[]> writeDevFiles(final InsiderDataset.Options options, final Path dir, final Path netskope) {
        try {
            Files.createDirectories(dir);
            final Map<Path, long[]> written = new LinkedHashMap<>();
            final List<String> header;
            try (final BufferedReader in = Files.newBufferedReader(netskope, StandardCharsets.UTF_8)) {
                header = List.of(in.readLine().split(","));
            }
            final Map<Source, Integer> offsets = new EnumMap<>(Source.class);
            final Map<String, Act> logins = new LinkedHashMap<>();
            Map<String, Object> answer = null;
            InsiderDataset last = null;
            for (LocalDate month = InsiderDataset.FIRST_DAY; month.isBefore(InsiderDataset.END_DAY);
                    month = month.plusMonths(1)) {
                final InsiderDataset dataset = InsiderDataset.generate(new InsiderDataset.Options(options.seed(),
                        options.background(), options.scale(), List.of(new InsiderDataset.Period(month,
                        month.plusMonths(1).minusDays(1)))));
                for (final Source source : Source.values()) {
                    final Path folder = dir.resolve(source.key());
                    Files.createDirectories(folder);
                    final Path path = folder.resolve(source.key() + "-" + month.toString().substring(0, 7)
                                                     + extension(source));
                    final int offset = offsets.getOrDefault(source, 0);
                    final List<Act> acts = dataset.acts(source);
                    try (final BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                        if (source == Source.PROXY) {
                            out.write(String.join(",", header));
                            out.write('\n');
                        }
                        for (final Act each : acts) {
                            final Act act = each.numbered(offset + each.number());
                            out.write(switch (source) {
                                case ENDPOINT -> endpointLine(act);
                                case PROXY -> netskopeRow(act, header, dataset.person(act.personId()));
                                case VPN -> netscalerLine(act, logins);
                                case CLOUD -> azureJson(act);
                                case PAM -> delineaXml(act, dataset.person(act.personId()));
                            });
                            out.write('\n');
                        }
                    }
                    offsets.put(source, offset + acts.size());
                    written.put(path, new long[]{Files.size(path), acts.size()});
                }
                answer = merged(answer, dataset.knownAnswer());
                last = dataset;
            }
            final Path reference = dir.resolve("reference-accounts.csv");
            Files.write(reference, last.reference().stream().map(row -> String.join(",", row
                    .stream().map(InsiderRecords::csv).toList())).toList(), StandardCharsets.UTF_8);
            written.put(reference, new long[]{Files.size(reference), last.reference().size() - 1});
            final Path answerPath = dir.resolve("known-answer.json");
            Files.writeString(answerPath, JsonUtil.writeValueAsString(answer, true));
            written.put(answerPath, new long[]{Files.size(answerPath), 0});
            return written;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // One month's known answer merged into the months' before: the same people, plants and decoys; the tasks by day
    // and the events' counts added.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> merged(final Map<String, Object> before, final Map<String, Object> month) {
        if (before == null) {
            final Map<String, Object> first = new LinkedHashMap<>(month);
            final Map<String, Object> tasks = new LinkedHashMap<>();
            ((Map<String, Map<String, Object>>) month.get("tasks")).forEach((person, days) ->
                    tasks.put(person, new LinkedHashMap<>(days)));
            first.put("tasks", tasks);
            first.put("events", new LinkedHashMap<>((Map<String, Object>) month.get("events")));
            return first;
        }
        final Map<String, Object> tasks = (Map<String, Object>) before.get("tasks");
        ((Map<String, Map<String, Object>>) month.get("tasks")).forEach((person, days) ->
                ((Map<String, Object>) tasks.computeIfAbsent(person, p -> new LinkedHashMap<>())).putAll(days));
        final Map<String, Object> events = (Map<String, Object>) before.get("events");
        ((Map<String, Object>) month.get("events")).forEach((source, count) -> events.merge(source, count,
                (a, b) -> ((Number) a).longValue() + ((Number) b).longValue()));
        return before;
    }

    private static String extension(final Source source) {
        return switch (source) {
            case ENDPOINT, VPN -> ".log";
            case PROXY -> ".csv";
            case CLOUD -> ".json";
            case PAM -> ".xml";
        };
    }

    /// The endpoint agent's own format (invented): a time with its offset, the host, the agent, then `key=value`
    /// pairs, a value quoted where it holds a space.
    ///
    /// @param act An endpoint event.
    /// @return Its line.
    static String endpointLine(final Act act) {
        final StringBuilder sb = new StringBuilder(ENDPOINT_TIME.format(Instant.ofEpochMilli(act.time())))
                .append(' ').append(act.host()).append(" deskwatch[1180]: evt=").append(act.type()).append(" user=")
                .append(quoted(act.account()));
        act.fields().forEach((name, value) -> {
            if (!NOT_DATA.contains(name)) {
                sb.append(' ').append(switch (name) {
                    case "window" -> "hwnd";
                    case "application" -> "app";
                    case "command" -> "image";
                    case "hash" -> "sha256";
                    default -> name;
                }).append('=').append(quoted(value));
            }
        });
        return sb.toString();
    }

    private static String netskopeRow(final Act act, final List<String> header, final Person person) {
        final Instant time = Instant.ofEpochMilli(act.time());
        final String url = act.field("url");
        final String path = url.contains("://") && url.indexOf('/', url.indexOf("://") + 3) > 0
                ? url.substring(url.indexOf('/', url.indexOf("://") + 3))
                : "/";
        final Map<String, String> values = new LinkedHashMap<>();
        values.put("x-cs-access-method", "VPN".equals(act.field("access"))
                ? "Client"
                : "NSClient");
        values.put("x-cs-src-ip", "10.1." + (Math.floorMod(person.id().hashCode(), 200) + 1) + ".20");
        values.put("x-cs-dst-ip", act.field("ip"));
        values.put("x-cs-dst-port", "443");
        values.put("cs-bytes", act.field("bytes_out"));
        values.put("sc-bytes", act.field("bytes_in"));
        values.put("bytes", String.valueOf(Long.parseLong(act.field("bytes_out")) + Long.parseLong(act.field(
                "bytes_in"))));
        values.put("x-c-hostname", act.host().toUpperCase(Locale.ROOT));
        values.put("x-c-device", "Laptop");
        values.put("x-c-browser", "Chrome");
        values.put("x-cs-process", act.field("process"));
        values.put("sc-status", "200");
        values.put("x-c-timezone", "+" + InsiderDataset.ZONE.getRules().getOffset(time).toString()
                .replace("+", ""));
        values.put("x-c-local-time", LOCAL.format(time));
        values.put("x-c-country", "Australia");
        values.put("x-cs-url", url);
        values.put("x-cs-site", act.field("domain"));
        values.put("cs-method", act.field("method"));
        values.put("cs-uri-scheme", "https");
        values.put("cs-host", act.field("domain"));
        values.put("cs-uri-port", "443");
        values.put("x-cs-uri-path", path);
        values.put("date", NETSKOPE_DATE.format(time));
        values.put("time", NETSKOPE_TIME.format(time));
        values.put("x-cs-timestamp", String.valueOf(time.getEpochSecond()));
        values.put("x-action", "allow");
        values.put("x-type", "http_transaction");
        values.put("x-transaction-id", String.valueOf(100_000_000_000L + act.number()));
        values.put("x-c-authn-user", act.account());
        values.put("cs-username", act.account());
        values.put("x-category", act.field("category"));
        values.put("x-other-category", act.field("category"));
        values.put("x-cs-app-activity", "POST".equals(act.field("method"))
                ? "Upload"
                : "View");
        final List<String> cells = new ArrayList<>(header.size());
        for (final String column : header) {
            cells.add(csv(values.getOrDefault(column, "-")));
        }
        return String.join(",", cells);
    }

    private static String netscalerLine(final Act act, final Map<String, Act> logins) {
        final Instant time = Instant.ofEpochMilli(act.time());
        final String gateway = act.host();
        final String session = act.field("session");
        final StringBuilder sb = new StringBuilder(time.toString().replace("Z", "+00:00")).append(' ')
                .append(gateway).append("  ").append(NETSCALER.format(time)).append(' ').append(gateway)
                .append(" 0-PPE-0 : default SSLVPN ");
        if ("vpn.login".equals(act.type())) {
            logins.put(session, act);
            sb.append("LOGIN ").append(act.number()).append(" 0 : Context ").append(act.account()).append('@')
                    .append(act.field("ip")).append(" - SessionId: ").append(session).append(" - User ")
                    .append(act.account()).append(" - Client_ip ").append(act.field("ip"))
                    .append(" - Nat_ip \"Mapped Ip\" - Vserver 192.0.2.10:443 - Browser_type \"Mozilla/5.0 (Windows"
                            + " NT 10.0; Win64; x64)\" - SSLVPN_client_type Agent - Group(s) \"Staff\"");
        } else {
            final Act login = logins.remove(session);
            final Instant started = login == null
                    ? time
                    : Instant.ofEpochMilli(login.time());
            final Duration duration = Duration.between(started, time);
            final String ip = login == null
                    ? act.field("ip")
                    : login.field("ip");
            sb.append("LOGOUT ").append(act.number()).append(" 0 : Context ").append(act.account()).append('@')
                    .append(ip).append(" - SessionId: ").append(session).append("- User ").append(act.account())
                    .append(" - Client_ip ").append(ip).append(" - Nat_ip \"Mapped Ip\" - Vserver 192.0.2.10:443 - "
                                                               + "Start_time \"")
                    .append(NETSCALER.format(started)).append("\" - End_time \"").append(NETSCALER.format(time))
                    .append("\" - Duration ").append("%02d:%02d:%02d".formatted(duration.toHours(),
                            duration.toMinutesPart(), duration.toSecondsPart()))
                    .append("  - Http_resources_accessed 120 - NonHttp_services_accessed 0 - Total_TCP_connections "
                            + "200 - Total_UDP_flows 0 - Total_policies_allowed 55 - Total_policies_denied 0 - "
                            + "Total_bytes_send ").append(act.field("bytes_out")).append(" - Total_bytes_recv ")
                    .append(act.field("bytes_in")).append(" - Total_compressedbytes_send 0 - "
                                                          + "Total_compressedbytes_recv 0 - Compression_ratio_send "
                                                          + "0.00% - Compression_ratio_recv 0.00% - LogoutMethod "
                                                          + "\"Explicit\" - Group(s) \"Staff\"");
        }
        return sb.toString();
    }

    private static String azureJson(final Act act) {
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("CreationTime", AZURE.format(Instant.ofEpochMilli(act.time())));
        record.put("Id", UUID.nameUUIDFromBytes((act.number() + "/" + act.time()).getBytes(StandardCharsets.UTF_8))
                .toString());
        final Map<String, Object> event = new LinkedHashMap<>();
        switch (act.type()) {
            case "signin" -> {
                record.put("Operation", "UserLoggedIn");
                record.put("RecordType", 15);
                record.put("Workload", "AzureActiveDirectory");
                event.put("Country", act.field("country"));
                event.put("Application", act.field("app"));
            }
            case "download" -> {
                record.put("Operation", "FileDownloaded");
                record.put("RecordType", 6);
                record.put("Workload", "SharePoint");
                event.put("SiteUrl", "https://corp.sharepoint.example/" + act.field("site"));
                event.put("SourceFileName", act.field("file"));
                event.put("SourceRelativeUrl", "Shared Documents");
                event.put("FileSizeBytes", Long.parseLong(act.field("size")));
            }
            case "record.view" -> {
                record.put("Operation", "RecordViewed");
                record.put("RecordType", 21);
                record.put("Workload", "CRM");
                event.put("EntityName", "account");
                event.put("RecordId", act.field("record"));
            }
            default -> throw new IllegalArgumentException("No cloud record for " + act.type());
        }
        record.put("OrganizationId", "11111111-2222-3333-4444-555555555555");
        record.put("UserId", act.account());
        if (act.field("ip") != null) {
            record.put("ClientIP", act.field("ip"));
        }
        record.put("event", event);
        record.put("UserType", 0);
        record.put("Version", 1);
        return JsonUtil.writeValueAsString(record, false);
    }

    private static String delineaXml(final Act act, final Person person) {
        final Instant time = Instant.ofEpochMilli(act.time());
        final StringBuilder data = new StringBuilder(DELINEA.format(time)).append(" - User: ")
                .append(escape(act.account().substring(0, act.account().indexOf('\\') + 1) + person.last() + ", "
                               + person.first())).append(" - [[SecretServer]] Event: ");
        final String by = " By User: " + escape(act.account()) + " (Item Id: " + act.number() + ")";
        switch (act.type()) {
            case "pam.checkout" -> data.append("[Secret] Action: [Checkout]").append(by).append(" | Secret: ")
                    .append(escape(act.field("secret"))).append(" | Secret Id: ").append(10_000 + Math.floorMod(
                            act.field("secret").hashCode(), 900)).append(" | Folder: Servers | Checkout Id: ")
                    .append(80_000 + act.number()).append(" | Reason: Maintenance | Expiration: 1 hours");
            case "pam.session.start" -> data.append("[Session] Action: [Start]").append(by).append(" - Session Id: ")
                    .append(act.field("session")).append(" - Target Host: ").append(act.field("target"))
                    .append(" - Protocol: ").append(act.field("protocol")).append(" - Source IP: 192.0.2.45 - "
                                                                                  + "Server Node: ss-web-01");
            case "pam.session.end" -> data.append("[Session] Action: [End]").append(by).append(" - Session Id: ")
                    .append(act.field("session")).append(" - Target Host: ").append(act.field("target"))
                    .append(" - Protocol: RDP - Source IP: 192.0.2.45 - Duration Seconds: ")
                    .append(act.field("seconds")).append(" - Termination Reason: User Disconnected");
            default -> throw new IllegalArgumentException("No privileged session record for " + act.type());
        }
        return "<Event xmlns=\"http://schemas.microsoft.com/win/2004/08/events/event\"><System><Provider "
               + "Name=\"SecretServer\" /><EventID>1000</EventID><Level>4</Level><Task>0</Task><Opcode>0</Opcode>"
               + "<Keywords>0x80000000000000</Keywords><TimeCreated SystemTime=\""
               + DELINEA_SYSTEM_TIME.format(time) + "\" /><EventRecordID>" + act.number()
               + "</EventRecordID><Channel>Application</Channel><Computer>SecretServer.corp.example</Computer>"
               + "</System><EventData><Data>" + data + "</Data></EventData></Event>";
    }

    // ------------------------------------------------------------------------------------------------

    /// @param act An event.
    /// @return Its Action, else its type.
    static String action(final Act act) {
        return switch (act.type()) {
            case "logon", "signin" -> "Logon";
            case "logoff" -> "Logoff";
            case "lock" -> "ScreenLock";
            case "unlock" -> "ScreenUnlock";
            case "vpn.login" -> "Connect";
            case "vpn.logout" -> "Disconnect";
            case "proc.start" -> "Execute";
            case "proc.stop" -> "Terminate";
            default -> act.type();
        };
    }

    private static String systemName(final Source source) {
        return switch (source) {
            case ENDPOINT -> "DeskWatch";
            case PROXY -> "Netskope";
            case VPN -> "NetScaler";
            case CLOUD -> "Office 365";
            case PAM -> "Secret Server";
        };
    }

    private static String baseName(final String command) {
        if (command == null) {
            return null;
        }
        final String first = command.split(" -")[0].strip();
        return first.substring(first.lastIndexOf('\\') + 1);
    }

    private static void putText(final Map<String, String> record, final String column, final String value) {
        if (value != null) {
            record.put(column, value);
        }
    }

    private static String firstOf(final String... values) {
        for (final String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static void put(final Map<String, Object> doc, final String field, final String value) {
        if (value != null) {
            doc.put(field, value);
        }
    }

    private static void putLong(final Map<String, Object> doc, final String field, final String value) {
        if (value != null) {
            doc.put(field, Long.parseLong(value));
        }
    }

    private static String quoted(final String value) {
        return value.contains(" ") || value.contains("\"")
                ? "\"" + value.replace("\"", "\\\"") + "\""
                : value;
    }

    private static String csv(final String value) {
        return value.contains(",") || value.contains("\"")
                ? "\"" + value.replace("\"", "\"\"") + "\""
                : value;
    }

    private static String escape(final String text) {
        return text == null
                ? ""
                : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttribute(final String text) {
        return escape(text).replace("\"", "&quot;");
    }

    private static Map<String, String> linked(final String... pairs) {
        final Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
