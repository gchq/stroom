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

package stroom.ai.impl.tools.draft;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/// A domain controller's Security log, as Windows Event Forwarding sends it (XML events) and as Winlogbeat sends it
/// (JSON lines with the rendered message), for testing what onboarding Active Directory's events needs of the tools
/// in general (the work package's E1-E6): many kinds (about 40 event ids), several users in one record (the subject
/// acting, the target acted on), a code whose meaning is a table (logon types, status codes), a change written as
/// two records tied by a correlation id (5136's value deleted and value added), rendered text whose keys repeat under
/// headings, and times to seven digits of a second. Noise kinds (4662, 5156) are most of the records, as on a real
/// controller.
///
/// Test data only: the tools hold no knowledge of Windows (the user's rule); this is a source the tests onboard. The
/// same text every run (seeded).
final class DomainControllerLog {

    /// The Security log's provider.
    static final String PROVIDER = "Microsoft-Windows-Security-Auditing";
    /// The event ids written, noise first.
    static final List<Integer> IDS = List.of(4662, 5156, 4624, 4625, 4634, 4647, 4648, 4672, 4688, 4689, 4720,
            4722, 4723, 4724, 4725, 4726, 4728, 4729, 4732, 4733, 4738, 4740, 4741, 4742, 4743, 4756, 4757, 4767,
            4768, 4769, 4770, 4771, 4776, 4778, 4779, 4781, 4798, 4799, 5136, 5137, 5141, 1102, 4719);

    private static final String NS = "http://schemas.microsoft.com/win/2004/08/events/event";
    private static final Instant START = Instant.parse("2026-11-06T09:00:00Z");
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneOffset.UTC);
    private static final List<String> USERS = List.of("alice", "bob", "carol", "dave", "erin", "svc_backup",
            "svc_sql");
    private static final List<String> HOSTS = List.of("WS0142", "WS0177", "WS0203", "SRV-FS01", "SRV-SQL02");
    private static final List<String> GROUPS = List.of("Domain Admins", "Finance", "VPN Users", "Backup Operators");
    private static final String DOMAIN = "CORP";
    private static final String DC = "DC01.corp.example.com";

    private DomainControllerLog() {
        // Static only.
    }

    /// @param records How many events.
    /// @param seed    The seed.
    /// @return The events as Windows Event Forwarding sends them: one XML `Event` a line.
    static String wef(final int records, final long seed) {
        final StringBuilder out = new StringBuilder();
        for (final Record record : records(records, seed)) {
            out.append("<Event xmlns=\"").append(NS).append("\"><System><Provider Name=\"").append(PROVIDER)
                    .append("\"/><EventID>").append(record.id()).append("</EventID><Keywords>")
                    .append(record.success()
                            ? "0x8020000000000000"
                            : "0x8010000000000000")
                    .append("</Keywords><TimeCreated SystemTime=\"").append(record.time())
                    .append("\"/><EventRecordID>").append(record.serial())
                    .append("</EventRecordID><Correlation/><Channel>Security</Channel><Computer>").append(DC)
                    .append("</Computer></System><EventData>");
            record.data().forEach((name, value) -> out.append("<Data Name=\"").append(name).append("\">")
                    .append(escape(value)).append("</Data>"));
            out.append("</EventData></Event>\n");
        }
        return out.toString();
    }

    /// @param records How many events.
    /// @param seed    The seed.
    /// @return The events as Winlogbeat sends them: one JSON object a line, the rendered message among them.
    static String winlogbeat(final int records, final long seed) {
        final StringBuilder out = new StringBuilder();
        for (final Record record : records(records, seed)) {
            out.append("{\"@timestamp\":\"").append(record.time(), 0, 23).append("Z\",\"event\":{\"code\":\"")
                    .append(record.id()).append("\",\"provider\":\"").append(PROVIDER).append("\",\"outcome\":\"")
                    .append(record.success()
                            ? "success"
                            : "failure")
                    .append("\"},\"host\":{\"name\":\"").append(DC).append("\"},\"winlog\":{\"record_id\":")
                    .append(record.serial()).append(",\"channel\":\"Security\",\"event_data\":{");
            final List<String> pairs = new ArrayList<>();
            record.data().forEach((name, value) -> pairs.add("\"" + name + "\":\"" + json(value) + "\""));
            out.append(String.join(",", pairs)).append("}},\"message\":\"").append(json(record.message()))
                    .append("\"}\n");
        }
        return out.toString();
    }

    // The events: noise kinds most of them, each other kind at least once.
    private static List<Record> records(final int count, final long seed) {
        final Random random = new Random(seed);
        final List<Record> out = new ArrayList<>();
        Instant time = START;
        long serial = 9_000_000L;
        for (int i = 0; i < count; i++) {
            time = time.plusNanos(100L * (1 + random.nextInt(20_000_000)));
            final int id = i < IDS.size()
                    ? IDS.get(i)
                    : random.nextInt(10) < 6
                            ? IDS.get(random.nextInt(2))
                            : IDS.get(2 + random.nextInt(IDS.size() - 2));
            // FILETIME's 100 ns: seven digits of a second.
            final String stamp = SECONDS.format(time) + "." + String.format("%07d", time.getNano() / 100) + "Z";
            if (id == 5136) {
                // A change is two records with one correlation id: the value deleted, then the value added.
                final String correlation = "{" + guid(random) + "}";
                final String object = "CN=" + pick(random, USERS) + ",OU=Staff,DC=corp,DC=example,DC=com";
                out.add(change(random, stamp, serial++, correlation, object, "%%14675", "old-" + random.nextInt(99)));
                out.add(change(random, stamp, serial++, correlation, object, "%%14674", "new-" + random.nextInt(99)));
                continue;
            }
            out.add(event(random, id, stamp, serial++));
        }
        return out;
    }

    private static Record change(final Random random,
                                 final String stamp,
                                 final long serial,
                                 final String correlation,
                                 final String object,
                                 final String operation,
                                 final String value) {
        final Map<String, String> data = subject(random);
        data.put("OpCorrelationID", correlation);
        data.put("DSName", "corp.example.com");
        data.put("ObjectDN", object);
        data.put("ObjectClass", "user");
        data.put("AttributeLDAPDisplayName", "description");
        data.put("AttributeValue", value);
        data.put("OperationType", operation);
        return new Record(5136, stamp, serial, true, data, rendered("A directory service object was modified.",
                data, List.of("Subject", "Object", "Attribute", "Operation")));
    }

    private static Record event(final Random random, final int id, final String stamp, final long serial) {
        final Map<String, String> data = subject(random);
        boolean success = true;
        final List<String> headings = new ArrayList<>(List.of("Subject"));
        switch (id) {
            case 4624, 4625, 4648 -> {
                success = id != 4625;
                target(random, data);
                data.put("LogonType", pick(random, List.of("2", "3", "10")));
                data.put("WorkstationName", pick(random, HOSTS));
                data.put("IpAddress", "10.1." + random.nextInt(20) + "." + (1 + random.nextInt(250)));
                data.put("IpPort", String.valueOf(49152 + random.nextInt(16000)));
                if (!success) {
                    data.put("Status", "0xc000006d");
                    data.put("SubStatus", pick(random, List.of("0xc000006a", "0xc0000064", "0xc0000072")));
                }
                headings.addAll(List.of("New Logon", "Network Information"));
            }
            case 4720, 4722, 4723, 4724, 4725, 4726, 4738, 4740, 4767, 4781, 4798 -> {
                target(random, data);
                headings.add("Target Account");
                if (id == 4738) {
                    // An account's last logon and password set: an object's times, as FILETIME.
                    data.put("PasswordLastSet", String.valueOf(133_900_000_000_000_000L + random.nextInt(1_000_000)));
                }
            }
            case 4728, 4729, 4732, 4733, 4756, 4757, 4799 -> {
                data.put("MemberName", "CN=" + pick(random, USERS) + ",OU=Staff,DC=corp,DC=example,DC=com");
                data.put("TargetUserName", pick(random, GROUPS));
                data.put("TargetDomainName", DOMAIN);
                headings.addAll(List.of("Member", "Group"));
            }
            case 4768, 4769, 4770, 4771, 4776 -> {
                success = id != 4771;
                data.put("TargetUserName", pick(random, USERS));
                data.put("ServiceName", pick(random, List.of("krbtgt", "cifs/SRV-FS01", "MSSQLSvc/SRV-SQL02")));
                data.put("TicketEncryptionType", "0x12");
                data.put("Status", success
                        ? "0x0"
                        : "0x18");
                data.put("IpAddress", "::ffff:10.1." + random.nextInt(20) + "." + (1 + random.nextInt(250)));
                headings.addAll(List.of("Account Information", "Service Information", "Network Information"));
            }
            case 4688, 4689 -> {
                data.put("NewProcessName", pick(random, List.of("C:\\Windows\\System32\\cmd.exe",
                        "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe")));
                data.put("CommandLine", "powershell -enc " + guid(random).substring(0, 12));
                headings.add("Process Information");
            }
            case 4662 -> {
                data.put("ObjectServer", "DS");
                data.put("ObjectType", "%{19195a5b-6da0-11d0-afd3-00c04fd930c9}");
                data.put("AccessMask", "0x100");
                headings.addAll(List.of("Object", "Operation"));
            }
            case 5156 -> {
                data.put("Application", "\\device\\harddiskvolume2\\windows\\system32\\lsass.exe");
                data.put("SourceAddress", "10.0.0.10");
                data.put("DestAddress", "10.1." + random.nextInt(20) + "." + (1 + random.nextInt(250)));
                data.put("DestPort", pick(random, List.of("88", "389", "445")));
                data.put("Protocol", "6");
                headings.addAll(List.of("Application Information", "Network Information"));
            }
            default -> data.put("ObjectName", pick(random, List.of("Default Domain Policy", "Audit Policy")));
        }
        return new Record(id, stamp, serial, success, data, rendered("Event " + id + ".", data, headings));
    }

    // The subject: who did it.
    private static Map<String, String> subject(final Random random) {
        final Map<String, String> data = new LinkedHashMap<>();
        final String user = pick(random, USERS);
        data.put("SubjectUserSid", "S-1-5-21-1004336348-1177238915-682003330-" + (1100 + USERS.indexOf(user)));
        data.put("SubjectUserName", user);
        data.put("SubjectDomainName", DOMAIN);
        data.put("SubjectLogonId", "0x" + Integer.toHexString(0x3e7 + random.nextInt(0xfffff)));
        return data;
    }

    // The target: who it was done to.
    private static void target(final Random random, final Map<String, String> data) {
        final String user = pick(random, USERS);
        data.put("TargetUserSid", "S-1-5-21-1004336348-1177238915-682003330-" + (1100 + USERS.indexOf(user)));
        data.put("TargetUserName", user);
        data.put("TargetDomainName", DOMAIN);
    }

    // The rendered message: a line, then each heading with its keys beneath, the same keys (Security ID, Account
    // Name...) under more than one heading.
    private static String rendered(final String first, final Map<String, String> data, final List<String> headings) {
        final StringBuilder out = new StringBuilder(first).append("\n");
        for (final String heading : headings) {
            out.append("\n").append(heading).append(":\n");
            final boolean subject = "Subject".equals(heading);
            data.forEach((name, value) -> {
                final boolean own = subject
                        ? name.startsWith("Subject")
                        : !name.startsWith("Subject");
                if (own) {
                    out.append("\t").append(label(name)).append(":\t\t").append(value).append("\n");
                }
            });
        }
        return out.toString();
    }

    // SubjectUserName -> Account Name, as the rendered text labels it.
    private static String label(final String name) {
        final String bare = name.replaceFirst("^(Subject|Target)", "");
        return switch (bare) {
            case "UserSid" -> "Security ID";
            case "UserName" -> "Account Name";
            case "DomainName" -> "Account Domain";
            case "LogonId" -> "Logon ID";
            default -> bare.replaceAll("([a-z])([A-Z])", "$1 $2");
        };
    }

    private static String guid(final Random random) {
        return String.format("%08x-%04x-%04x-%04x-%012x", random.nextInt(), random.nextInt(0xffff),
                random.nextInt(0xffff), random.nextInt(0xffff), random.nextLong() & 0xffffffffffffL);
    }

    private static String pick(final Random random, final List<String> from) {
        return from.get(random.nextInt(from.size()));
    }

    private static String escape(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String json(final String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t");
    }


    // --------------------------------------------------------------------------------


    // One event.
    private record Record(int id, String time, long serial, boolean success, Map<String, String> data,
                          String message) {

    }
}
