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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The timeline cases' records (eval cases 113 to 116, `generate: {kind: timeline_scenes, scene, seed}`,
/// [EvalQuestionRecords#generate]): one person's events from several products in one flat index, each record a
/// product's event with only the fields that product writes, beside another person's, made from a seed so that the
/// same records come every run. Times are UTC; the seed jitters them by a few minutes, never past what the case's
/// answer rests on.
///
/// * `physical_access` (case 113, akhan, 16 to 20 November 2026): badge enter and leave events logged separately
///   (`EventCode` `badge.enter`, `badge.leave`) at two sites, each zone's doors different on the way in and out;
///   workstation logons and logoffs (`Element` Authenticate, `Action` Logon, Logoff) with web requests while
///   logged on; and VPN connects and disconnects from a laptop (Authenticate Connect, Disconnect, by `SessionId`).
///   Planted: Wednesday badged in at HQ with no logon all day; Thursday a VPN session while on site at the depot;
///   Tuesday an early badge leave at HQ with no enter (its start not seen); Friday a badge enter at HQ with no leave
///   (its end not seen).
/// * `property_sites` (case 114, lchen, 18 November 2026): web requests (`EventCode` `web.get`, with the proxy's
///   `Category`) to three property listing sites, interleaved over an afternoon, all of category Real Estate; and
///   document work (`doc.edit` on a budget workbook and a board report) in the morning and late afternoon.
/// * `message_command_runs` (case 115, dmorgan, 19 November 2026): a chat thread (`chat.message`, its text in
///   `Text`, its thread in `Thread`) whose first six messages are about the weekend on-call rota and the next six
///   about renewing the VPN certificate, with no gap between the topics; and a PowerShell session's commands
///   (`process.start`, each command line in `Command`, no `Text`, its session in `Thread`) that list directory
///   users, export them to a CSV, zip it and copy the zip to a share.
/// * `background_noise` (case 116, sward, 17 November 2026): a telemetry beacon every 30 seconds through the
///   working day to one URL with no page (`web.get`, the same `Object` each time), and a scheduled script run every
///   5 minutes by a scheduler on the person's workstation (`process.start`, the same `Object` and `Command`, its
///   `AccountType` `service`); beside them the person's real activity: research on a supplier's site, and edits of a
///   tender document.
/// * `empty_sources_web` (case 117, rpatel, 20 November 2026): web requests to a supplier's site in the morning
///   and edits of a purchase order in the afternoon (`web.get`, `doc.edit`, `Object` the URL or document), beside
///   another person's; and `empty_sources_badges`, the same day's badge events (`badge.enter`, `badge.leave`) of
///   other people only, so their index holds documents in the period but none of rpatel's.
final class EvalTimelineScenes {

    /// The scene of case 113.
    static final String PHYSICAL_ACCESS = "physical_access";
    /// The scene of case 114.
    static final String PROPERTY_SITES = "property_sites";
    /// The scene of case 115.
    static final String MESSAGE_COMMAND_RUNS = "message_command_runs";
    /// The scene of case 116.
    static final String BACKGROUND_NOISE = "background_noise";
    /// The scene of case 117's index holding the person's events.
    static final String EMPTY_SOURCES_WEB = "empty_sources_web";
    /// The scene of case 117's index holding other people's events only.
    static final String EMPTY_SOURCES_BADGES = "empty_sources_badges";

    private static final DateTimeFormatter ISO_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final String HQ = "HQ";
    private static final String HQ_ZONE = "Level 2";
    private static final String DEPOT = "Depot";
    private static final String DEPOT_ZONE = "Warehouse";
    private static final List<String> REAL_ESTATE = List.of("https://www.homes.example.com.au/listing/",
            "https://propertyfinder.example.net/buy/", "https://rentals.example.org/property/");
    private static final List<String> ROTA = List.of("Can you cover the weekend on-call rota?",
            "I'm on call Saturday already, can swap Sunday", "Rota updated: Sam Saturday, me Sunday",
            "Who has the on-call phone handover?", "Handover is Friday 5pm at the desk", "Thanks, rota sorted");
    private static final List<String> CERTIFICATE = List.of("The VPN certificate expires next week",
            "Do we renew the VPN certificate through the internal CA?", "Yes, raise a CSR for vpn-gw-01",
            "CSR for the VPN certificate submitted", "New VPN certificate issued, installing tonight",
            "VPN certificate installed and tested");
    private static final List<String> COMMANDS = List.of(
            "Get-ADUser -Filter * -Properties mail | Select-Object Name,mail",
            "Get-ADUser -Filter * -Properties mail | Export-Csv C:\\Temp\\users.csv -NoTypeInformation",
            "Compress-Archive -Path C:\\Temp\\users.csv -DestinationPath C:\\Temp\\users.zip",
            "Copy-Item C:\\Temp\\users.zip \\\\nas01\\transfer\\");

    private EvalTimelineScenes() {
        // Static only.
    }

    /// Makes a scene's records.
    ///
    /// @param scene  The scene: [#PHYSICAL_ACCESS], [#PROPERTY_SITES], [#MESSAGE_COMMAND_RUNS],
    ///               [#BACKGROUND_NOISE], [#EMPTY_SOURCES_WEB] or [#EMPTY_SOURCES_BADGES].
    /// @param random The seeded random numbers.
    /// @return The records, each its values by column, in time order.
    static List<Map<String, String>> generate(final String scene, final SeededRandom random) {
        final List<Map<String, String>> records = switch (scene) {
            case PHYSICAL_ACCESS -> physicalAccess(random);
            case PROPERTY_SITES -> propertySites(random);
            case MESSAGE_COMMAND_RUNS -> messageCommandRuns(random);
            case BACKGROUND_NOISE -> backgroundNoise(random);
            case EMPTY_SOURCES_WEB -> emptySourcesWeb(random);
            case EMPTY_SOURCES_BADGES -> emptySourcesBadges(random);
            default -> throw new IllegalArgumentException("No timeline scene " + scene);
        };
        records.sort(Comparator.comparing((Map<String, String> record) -> record.get("EventTime"))
                .thenComparing(record -> record.get("UserName")));
        return records;
    }

    // ------------------------------------------------------------------------------------------------
    // Case 113

    private static List<Map<String, String>> physicalAccess(final SeededRandom random) {
        final List<Map<String, String>> out = new ArrayList<>();
        final String user = "akhan";
        final String desk = "ws-akhan-01";
        final String laptop = "lt-akhan-02";
        // Monday: at HQ, badged in by the north door and out by the south, logged on between.
        badge(out, user, at("2026-11-16", 8, 20 + random.randint(0, 5)), true, HQ, HQ_ZONE, "HQ-L2-North");
        logonDay(out, random, user, desk, "2026-11-16", 8, 35, 17, 0);
        badge(out, user, at("2026-11-16", 17, 10 + random.randint(0, 5)), false, HQ, HQ_ZONE, "HQ-L2-South");
        // Tuesday: an early leave at HQ with no enter logged (its start not seen), then at home over the VPN twice.
        badge(out, user, at("2026-11-17", 7, 50 + random.randint(0, 5)), false, HQ, HQ_ZONE, "HQ-L2-South");
        vpn(out, user, laptop, at("2026-11-17", 9, random.randint(0, 5)), "71001");
        web(out, random, user, laptop, at("2026-11-17", 9, 10), 12);
        vpn(out, user, laptop, at("2026-11-17", 12, 30 + random.randint(0, 5)), "71001");
        vpn(out, user, laptop, at("2026-11-17", 13, 30 + random.randint(0, 5)), "71002");
        web(out, random, user, laptop, at("2026-11-17", 13, 45), 10);
        vpn(out, user, laptop, at("2026-11-17", 17, random.randint(0, 5)), "71002");
        // Wednesday: badged in at HQ and out again, with no logon all day.
        badge(out, user, at("2026-11-18", 8, 40 + random.randint(0, 5)), true, HQ, HQ_ZONE, "HQ-L2-North");
        badge(out, user, at("2026-11-18", 16, 50 + random.randint(0, 5)), false, HQ, HQ_ZONE, "HQ-L2-East");
        // Thursday: at the depot, logged on at its workstation, and a VPN session from the laptop while there.
        badge(out, user, at("2026-11-19", 8, 30 + random.randint(0, 5)), true, DEPOT, DEPOT_ZONE, "DEP-W1");
        logonDay(out, random, user, "ws-depot-07", "2026-11-19", 8, 45, 15, 50);
        vpn(out, user, laptop, at("2026-11-19", 10, 15 + random.randint(0, 5)), "71003");
        vpn(out, user, laptop, at("2026-11-19", 11, 45 + random.randint(0, 5)), "71003");
        badge(out, user, at("2026-11-19", 16, random.randint(0, 5)), false, DEPOT, DEPOT_ZONE, "DEP-W3");
        // Friday: badged in at HQ, the leave never logged (its end not seen); logged on as usual.
        badge(out, user, at("2026-11-20", 9, random.randint(0, 5)), true, HQ, HQ_ZONE, "HQ-L2-North");
        logonDay(out, random, user, desk, "2026-11-20", 9, 10, 16, 30);
        // Another person at HQ on the same days.
        for (final String day : List.of("2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19", "2026-11-20")) {
            badge(out, "bjones", at(day, 8, 50 + random.randint(0, 9)), true, HQ, HQ_ZONE, "HQ-L2-North");
            logonDay(out, random, "bjones", "ws-bjones-01", day, 9, 5, 17, 20);
            badge(out, "bjones", at(day, 17, 30 + random.randint(0, 9)), false, HQ, HQ_ZONE, "HQ-L2-North");
        }
        return out;
    }

    private static void badge(final List<Map<String, String>> out,
                              final String user,
                              final long time,
                              final boolean enter,
                              final String site,
                              final String zone,
                              final String door) {
        final Map<String, String> record = record(time, "badge", user, "pacs-01");
        record.put("EventCode", enter
                ? "badge.enter"
                : "badge.leave");
        record.put("Site", site);
        record.put("Zone", zone);
        record.put("Door", door);
        out.add(record);
    }

    // A logon at the start, web requests through the day (a block every hour), a logoff at the end.
    private static void logonDay(final List<Map<String, String>> out,
                                 final SeededRandom random,
                                 final String user,
                                 final String host,
                                 final String day,
                                 final int fromHour,
                                 final int fromMinute,
                                 final int toHour,
                                 final int toMinute) {
        final long from = at(day, fromHour, fromMinute + random.randint(0, 4));
        final long to = at(day, toHour, toMinute + random.randint(0, 4));
        out.add(authenticate(from, "windows", user, host, "Logon", null));
        for (long block = from + 10 * 60_000L; block + 30 * 60_000L < to; block += 60 * 60_000L) {
            web(out, random, user, host, block, 6);
        }
        out.add(authenticate(to, "windows", user, host, "Logoff", null));
    }

    private static void vpn(final List<Map<String, String>> out,
                            final String user,
                            final String host,
                            final long time,
                            final String session) {
        final boolean connected = out.stream().anyMatch(record -> session.equals(record.get("SessionId")));
        out.add(authenticate(time, "vpn", user, host, connected
                ? "Disconnect"
                : "Connect", session));
    }

    private static Map<String, String> authenticate(final long time,
                                                    final String source,
                                                    final String user,
                                                    final String host,
                                                    final String action,
                                                    final String session) {
        final Map<String, String> record = record(time, source, user, host);
        record.put("Element", "Authenticate");
        record.put("Action", action);
        if (session != null) {
            record.put("SessionId", session);
        }
        return record;
    }

    // A few web requests to the intranet, a minute or two apart.
    private static void web(final List<Map<String, String>> out,
                            final SeededRandom random,
                            final String user,
                            final String host,
                            final long from,
                            final int requests) {
        long time = from;
        for (int i = 0; i < requests; i++) {
            final Map<String, String> record = record(time, "proxy", user, host);
            record.put("EventCode", "web.get");
            record.put("Object", "https://intranet.example.local/page/" + random.randint(1, 400));
            out.add(record);
            time += random.randint(60, 150) * 1_000L;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Case 114

    private static List<Map<String, String>> propertySites(final SeededRandom random) {
        final List<Map<String, String>> out = new ArrayList<>();
        final String user = "lchen";
        final String host = "ws-lchen-01";
        // The morning's document work: the budget workbook and the board report, by turns.
        long time = at("2026-11-18", 9, random.randint(0, 5));
        for (int i = 0; i < 12; i++) {
            doc(out, user, host, time, i % 4 < 2
                    ? "\\\\fs01\\finance\\Q4 budget forecast.xlsx"
                    : "\\\\fs01\\board\\November board report.docx");
            time += random.randint(6, 9) * 60_000L;
        }
        // The afternoon: three property listing sites, visited by turns, a visit a few requests.
        time = at("2026-11-18", 13, random.randint(0, 5));
        for (int visit = 0; visit < 9; visit++) {
            final String site = REAL_ESTATE.get(visit % REAL_ESTATE.size());
            for (int i = 0; i < 3; i++) {
                final Map<String, String> record = record(time, "proxy", user, host);
                record.put("EventCode", "web.get");
                record.put("Object", site + (1_000 + random.randint(0, 8_999)));
                record.put("Category", "Real Estate");
                out.add(record);
                time += random.randint(40, 90) * 1_000L;
            }
            time += random.randint(2, 4) * 60_000L;
        }
        // Late afternoon: the board report again.
        time = at("2026-11-18", 15, 30 + random.randint(0, 5));
        for (int i = 0; i < 5; i++) {
            doc(out, user, host, time, "\\\\fs01\\board\\November board report.docx");
            time += random.randint(6, 9) * 60_000L;
        }
        // Another person's browsing of a listing site.
        for (int i = 0; i < 4; i++) {
            final Map<String, String> record = record(at("2026-11-18", 13, 10 + 3 * i), "proxy", "mreed",
                    "ws-mreed-01");
            record.put("EventCode", "web.get");
            record.put("Object", REAL_ESTATE.getFirst() + (1_000 + random.randint(0, 8_999)));
            record.put("Category", "Real Estate");
            out.add(record);
        }
        return out;
    }

    private static void doc(final List<Map<String, String>> out,
                            final String user,
                            final String host,
                            final long time,
                            final String path) {
        final Map<String, String> record = record(time, "endpoint", user, host);
        record.put("EventCode", "doc.edit");
        record.put("Object", path);
        out.add(record);
    }

    // ------------------------------------------------------------------------------------------------
    // Case 115

    private static List<Map<String, String>> messageCommandRuns(final SeededRandom random) {
        final List<Map<String, String>> out = new ArrayList<>();
        final String user = "dmorgan";
        final String host = "ws-dmorgan-01";
        long time = at("2026-11-19", 10, random.randint(0, 3));
        final List<String> thread = new ArrayList<>(ROTA);
        thread.addAll(CERTIFICATE);
        for (final String text : thread) {
            final Map<String, String> record = record(time, "messages", user, host);
            record.put("EventCode", "chat.message");
            record.put("Thread", "chat:ops-team");
            record.put("Text", text);
            out.add(record);
            time += random.randint(90, 200) * 1_000L;
        }
        time = at("2026-11-19", 14, random.randint(0, 3));
        for (final String command : COMMANDS) {
            final Map<String, String> record = record(time, "endpoint", user, host);
            record.put("EventCode", "process.start");
            record.put("Thread", "pwsh:5120");
            record.put("Process", "powershell.exe");
            record.put("Command", command);
            out.add(record);
            time += random.randint(60, 180) * 1_000L;
        }
        // Another person's messages in another thread.
        for (int i = 0; i < 4; i++) {
            final Map<String, String> record = record(at("2026-11-19", 11, 5 * i), "messages", "kpatel",
                    "ws-kpatel-01");
            record.put("EventCode", "chat.message");
            record.put("Thread", "chat:finance");
            record.put("Text", "Invoice batch " + random.randint(100, 999) + " approved");
            out.add(record);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------
    // Case 116

    private static List<Map<String, String>> backgroundNoise(final SeededRandom random) {
        final List<Map<String, String>> out = new ArrayList<>();
        final String user = "sward";
        final String host = "ws-sward-01";
        // The beacon: every 30 seconds from 08:00 to 17:00, always the same URL, no page.
        for (long time = at("2026-11-17", 8, 0); time < at("2026-11-17", 17, 0); time += 30_000L) {
            final Map<String, String> record = record(time + random.randint(0, 2) * 1_000L, "proxy", user, host);
            record.put("EventCode", "web.get");
            record.put("Object", "https://telemetry.example.net/v1/beacon");
            out.add(record);
        }
        // The scheduled script: every 5 minutes, by the scheduler's service account.
        for (long time = at("2026-11-17", 8, 2); time < at("2026-11-17", 17, 0); time += 5 * 60_000L) {
            final Map<String, String> record = record(time, "endpoint", user, host);
            record.put("EventCode", "process.start");
            record.put("Process", "powershell.exe");
            record.put("Object", "C:\\ProgramData\\Agent\\inventory.ps1");
            record.put("Command", "powershell.exe -File C:\\ProgramData\\Agent\\inventory.ps1");
            record.put("AccountType", "service");
            out.add(record);
        }
        // The person's own work: research on a supplier's site, then the tender document.
        long time = at("2026-11-17", 9, 30 + random.randint(0, 5));
        for (int i = 0; i < 10; i++) {
            final Map<String, String> record = record(time, "proxy", user, host);
            record.put("EventCode", "web.get");
            record.put("Object", "https://www.supplier.example.com/products/" + random.randint(100, 999));
            out.add(record);
            time += random.randint(60, 150) * 1_000L;
        }
        time = at("2026-11-17", 13, 10 + random.randint(0, 5));
        for (int i = 0; i < 8; i++) {
            doc(out, user, host, time, "\\\\fs01\\bids\\Tender response 2027.docx");
            time += random.randint(5, 9) * 60_000L;
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------
    // Case 117

    private static List<Map<String, String>> emptySourcesWeb(final SeededRandom random) {
        final List<Map<String, String>> out = new ArrayList<>();
        final String user = "rpatel";
        final String host = "ws-rpatel-01";
        long time = at("2026-11-20", 9, random.randint(0, 5));
        for (int i = 0; i < 8; i++) {
            final Map<String, String> record = record(time, "proxy", user, host);
            record.put("EventCode", "web.get");
            record.put("Object", "https://www.supplier.example.com/catalogue/" + random.randint(100, 999));
            out.add(record);
            time += random.randint(60, 150) * 1_000L;
        }
        time = at("2026-11-20", 13, random.randint(0, 5));
        for (int i = 0; i < 6; i++) {
            doc(out, user, host, time, "\\\\fs01\\purchasing\\PO-2291 office chairs.xlsx");
            time += random.randint(5, 9) * 60_000L;
        }
        // Another person's browsing.
        for (int i = 0; i < 3; i++) {
            final Map<String, String> record = record(at("2026-11-20", 10, 4 * i), "proxy", "jlee", "ws-jlee-01");
            record.put("EventCode", "web.get");
            record.put("Object", "https://news.example.org/story/" + random.randint(100, 999));
            out.add(record);
        }
        return out;
    }

    private static List<Map<String, String>> emptySourcesBadges(final SeededRandom random) {
        final List<Map<String, String>> out = new ArrayList<>();
        for (final String user : List.of("jlee", "tnguyen")) {
            final Map<String, String> enter = record(at("2026-11-20", 8, random.randint(0, 20)), "badge", user, "");
            enter.remove("Host");
            enter.put("EventCode", "badge.enter");
            enter.put("Site", HQ);
            out.add(enter);
            final Map<String, String> leave = record(at("2026-11-20", 17, random.randint(0, 20)), "badge", user, "");
            leave.remove("Host");
            leave.put("EventCode", "badge.leave");
            leave.put("Site", HQ);
            out.add(leave);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------

    private static Map<String, String> record(final long time,
                                              final String source,
                                              final String user,
                                              final String host) {
        final Map<String, String> record = new LinkedHashMap<>();
        record.put("EventTime", ISO_MILLIS.format(Instant.ofEpochMilli(time)));
        record.put("Source", source);
        record.put("UserName", user);
        record.put("Host", host);
        return record;
    }

    // A day's time, UTC, in epoch ms; a minute past 59 carries into the next hour.
    private static long at(final String day, final int hour, final int minute) {
        return LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
               + (hour * 60L + minute) * 60_000L;
    }
}
