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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/// Action elements the sample's own values show, as mapping rules.
///
/// An agent left with EventDetail/Unknown placeholders and a schema it found awkward kept giving up: it marked
/// firewall traffic and administrator logons Unknown and asked the user to agree. Some kinds are plain from their
/// values: connections allowed or denied between addresses and ports are Network/Permit and Network/Deny; logons
/// and logoffs are Authenticate; configuration changes are Update; a service started or stopped is Process; a
/// device's health and state (CPU over a threshold, a VPN tunnel down) is Alert. For those, the draft writes the
/// rules, and a rule that keeps them Unknown is given the rules to use instead.
///
/// Records are maps of field name to value; rules and their fields are maps as the mapping's JSON writes them.
public final class ActionRules {

    private static final Pattern PERMIT = ignoringCase("^(allow(ed)?|accept(ed)?|permit(ted)?|pass(ed)?)$");
    private static final Pattern DENY = ignoringCase("^(deny|denied|drop(ped)?|block(ed)?|reject(ed)?|refused?)$");
    private static final Pattern CONNECT = ignoringCase("^(connect(ed|ion)?|establish(ed)?|open(ed)?)$");
    private static final Pattern CLOSE = ignoringCase("^(close[d]?|disconnect(ed)?|teardown|terminated)$");
    private static final Pattern AUTH = ignoringCase(
            "(login|logon|signin|sign_in|authenticat|logoff|logout|signout|sign_out)");
    private static final Pattern LOGOFF = ignoringCase("(logoff|logout|signout|sign_out)");
    private static final Pattern FAILED = ignoringCase("(fail|denied|invalid|bad|reject|refused)");
    private static final Pattern SUCCEEDED = ignoringCase("(success|succeeded|ok\\b|accepted|allowed)");
    private static final Pattern CONFIG = ignoringCase("(config|setting|policy)");
    private static final Pattern PROCESS = ignoringCase(
            "^(service_?)?(start(ed|ing|up)?|stop(ped|ping)?|shut_?down|restart(ed)?|boot(ed)?|reboot(ed)?)$");
    private static final Pattern STOPPED = ignoringCase("(stop|shut)");
    private static final Pattern ALERT = ignoringCase(
            "(cpu|memory|disk|threshold|utili[sz]|overload|alarm|alert|tunnel|vpn|link|interface|exceed|"
            + "(^|_)(down|up|fail(ed|ure)?|error|critical|high|low)($|_))");
    private static final Pattern NETWORK_ALERT = ignoringCase("(tunnel|vpn|link|interface|bgp|ospf|failover)");
    private static final Pattern ERROR_ALERT = ignoringCase("(fail|error|critical)");
    private static final Pattern SEVERITY = ignoringCase("^(severity|sev|level|log_?level|priority|pri)$");
    private static final Map<String, Pattern> SEVERITIES = severities();
    private static final List<String> SEVERITY_WORDS = List.of("debug", "info", "notice", "warning", "warn", "error",
            "err", "critical", "crit", "alert", "emergency", "fatal");
    private static final Pattern SPLITTER = ignoringCase("^(action|act|result|outcome|operation|op|activity|subtype|"
                                                         + "sub_?type|status|event_?action|verdict|disposition)$");
    private static final Map<String, Pattern> FIELDS = fields();
    private static final List<String> TRANSPORT = List.of("TCP", "UDP", "ICMP", "IGMP");
    private static final int MAX_VALUES = 12;
    private static final String PATH = "path";
    private static final String FIELD = "field";
    private static final String VALUE = "value";
    private static final String MAP = "map";
    private static final String WHEN = "when";
    private static final String ONE_OF = "one_of";
    private static final String EQUALS = "equals";
    private static final String SRC_IP = "src_ip";
    private static final String DST_IP = "dst_ip";

    private ActionRules() {
        // Static only.
    }

    private static Map<String, Object> dict(final Object... keysAndValues) {
        return JsonValues.dict(keysAndValues);
    }

    private static Pattern ignoringCase(final String regex) {
        return ToolText.ignoringCase(regex);
    }

    private static Map<String, Pattern> severities() {
        final Map<String, Pattern> out = new LinkedHashMap<>();
        out.put("Info", ignoringCase("^(info(rmational)?|notice|debug|low)$"));
        out.put("Minor", ignoringCase("^(warn(ing)?|minor|medium)$"));
        out.put("Major", ignoringCase("^(err(or)?|major|high)$"));
        out.put("Critical", ignoringCase("^(crit(ical)?|alert|emerg(ency)?|fatal|severe)$"));
        return out;
    }

    private static Map<String, Pattern> fields() {
        final Map<String, Pattern> out = new LinkedHashMap<>();
        out.put(SRC_IP, ignoringCase("^(src_?ip|source_?ip|srcaddr|src_?addr|client_?ip|client_?addr(ess)?|"
                                     + "source_?address|clientip|src|source)$"));
        out.put("src_port", ignoringCase("^(src_?port|source_?port|client_?port|sport|spt)$"));
        out.put(DST_IP, ignoringCase("^(dst_?ip|dest_?ip|destination_?ip|dstaddr|dst_?addr|server_?ip|"
                                     + "server_?addr(ess)?|target_?ip|destination_?address|dst|dest|destination)$"));
        out.put("dst_port", ignoringCase("^(dst_?port|dest_?port|destination_?port|server_?port|dport|"
                                         + "service_?port|dpt)$"));
        out.put("protocol", ignoringCase("^(proto|protocol|transport|ip_?proto|l4_?proto)$"));
        return out;
    }

    /// The input entry for a field, or for several read in turn (`any_of`): each record shape's user, say.
    ///
    /// @param source A field name, or a list of field names.
    /// @return `{"any_of": [...]}` for a list, else `{"field": source}`.
    public static Map<String, Object> read(final Object source) {
        if (source instanceof final Collection<?> several) {
            return dict("any_of", new ArrayList<>(several));
        }
        return dict(FIELD, source);
    }

    /// The rules for records of one kind: a rule per action the values show, each with conditions `base` plus the
    /// splitter's values. Records that aren't maps (XML) give none.
    ///
    /// @param records     The kind's records.
    /// @param names       The records' field names.
    /// @param base        The conditions every rule starts with (the kind's own).
    /// @param prefix      The rules' names' prefix ("" for none).
    /// @param user        The user's field (or fields read in turn), or null.
    /// @param description The description's field (or fields read in turn), or null.
    /// @param skip        Fields not to split on.
    /// @param split       The field that names the action, or null to find one.
    /// @return The rules, the values left over, and the field that splits them.
    public static Found actionRules(final List<?> records,
                                    final List<String> names,
                                    final List<Map<String, Object>> base,
                                    final String prefix,
                                    final Object user,
                                    final Object description,
                                    final Set<String> skip,
                                    final String split) {
        final List<Map<String, Object>> held = JsonValues.maps(records);
        if (held.isEmpty()) {
            return new Found(List.of(), List.of(), null);
        }
        // Given: the kind's own field names the action.
        final String splitField = split != null && !split.isEmpty()
                ? split
                : splitter(held, names, skip == null
                        ? Set.of()
                        : skip);
        if (splitField == null) {
            return new Found(List.of(), List.of(), null);
        }
        return new RuleMaker(held, names, base, prefix, splitField).make(user, description);
    }

    /// Alert/Severity from the record's severity field, when every value it holds is one the schema has a word
    /// for.
    ///
    /// @param records The records.
    /// @param names   Their field names.
    /// @return The Severity entry, or an empty list.
    public static List<Map<String, Object>> severityFields(final List<Map<String, Object>> records,
                                                           final List<String> names) {
        final String field = names.stream()
                .filter(n -> ToolText.match(SEVERITY, ToolText.lastPart(n, '.')))
                .findFirst()
                .orElse(null);
        if (field == null) {
            return List.of();
        }
        final List<String> seen = values(records, field);
        final Map<String, Object> mapped = new LinkedHashMap<>();
        for (final String value : seen) {
            final String word = severityWord(value);
            if (word == null) {
                return List.of();
            }
            mapped.put(value, word);
        }
        // The other usual words too, written as the sample writes them, so a CRITICAL the sample lacks still
        // validates.
        final Function<String, String> casing;
        if (seen.stream().allMatch(ToolText::isUpper)) {
            casing = word -> word.toUpperCase(Locale.ROOT);
        } else if (seen.stream().allMatch(ToolText::isLower)) {
            casing = word -> word.toLowerCase(Locale.ROOT);
        } else {
            casing = null;
        }
        if (casing != null) {
            for (final String word : SEVERITY_WORDS) {
                mapped.putIfAbsent(casing.apply(word), severityWord(word));
            }
        }
        return mapped.isEmpty()
                ? List.of()
                : List.of(dict(PATH, "EventDetail/Alert/Severity", FIELD, field, MAP, mapped));
    }

    private static String severityWord(final String value) {
        return SEVERITIES.entrySet().stream()
                .filter(entry -> ToolText.match(entry.getValue(), value))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /// Network/<side> (Permit, Deny, Open...) with its source and destination, the protocol mapped to the values
    /// TransportProtocol takes.
    ///
    /// @param side    The Network action element.
    /// @param net     The records' network fields by role (`src_ip`, `src_port`, `dst_ip`, `dst_port`, `protocol`).
    /// @param records The records.
    /// @return The entries.
    public static List<Map<String, Object>> networkFields(final String side,
                                                          final Map<String, String> net,
                                                          final List<Map<String, Object>> records) {
        final String at = "EventDetail/Network/" + side;
        final List<Map<String, Object>> fields = new ArrayList<>();
        if (net.containsKey(SRC_IP)) {
            fields.add(dict(PATH, at + "/Source/Device/IPAddress", FIELD, net.get(SRC_IP)));
        }
        if (net.containsKey("src_port")) {
            fields.add(dict(PATH, at + "/Source/Port", FIELD, net.get("src_port")));
        }
        if (net.containsKey("protocol")) {
            final Map<String, Object> mapped = new LinkedHashMap<>();
            for (final String value : values(records, net.get("protocol"))) {
                final String upper = value.toUpperCase(Locale.ROOT);
                if (TRANSPORT.contains(upper)) {
                    mapped.put(value, upper);
                }
            }
            fields.add(dict(PATH, at + "/Source/TransportProtocol", FIELD, net.get("protocol"), MAP, mapped,
                    "default", "Other"));
        }
        if (net.containsKey(DST_IP)) {
            fields.add(dict(PATH, at + "/Destination/Device/IPAddress", FIELD, net.get(DST_IP)));
        }
        if (net.containsKey("dst_port")) {
            fields.add(dict(PATH, at + "/Destination/Port", FIELD, net.get("dst_port")));
        }
        return fields;
    }

    /// What the rules say, in a line: `Network/Permit for action ALLOW; Network/Deny for action DENY`.
    ///
    /// @param rules Rules (`when` and `fields`).
    /// @return The line.
    public static String described(final List<Map<String, Object>> rules) {
        final List<String> out = new ArrayList<>();
        for (final Map<String, Object> rule : rules) {
            String element = "?";
            for (final Map<String, Object> field : JsonValues.maps(rule.get("fields"))) {
                final String path = (String) field.get(PATH);
                if (path.startsWith("EventDetail/")) {
                    final String[] parts = path.split("/", -1);
                    element = "Network".equals(parts[1])
                            ? parts[1] + "/" + parts[2]
                            : parts[1];
                    break;
                }
            }
            final List<Map<String, Object>> when = JsonValues.maps(rule.get(WHEN));
            final Map<String, Object> condition = when.getLast();
            out.add(element + " for " + condition.get(FIELD) + " " + String.join(", ", conditionValues(condition)));
        }
        return String.join("; ", out);
    }

    /// @param condition A condition (`equals` or `one_of`).
    /// @return The values it takes.
    @SuppressWarnings("unchecked")
    static List<String> conditionValues(final Map<String, Object> condition) {
        if (JsonValues.truthy(condition.get(ONE_OF))) {
            return (List<String>) condition.get(ONE_OF);
        }
        final List<String> one = new ArrayList<>();
        one.add((String) condition.get(EQUALS));
        return one;
    }

    private static Map<String, String> networkRoles(final List<String> names) {
        final Map<String, String> out = new LinkedHashMap<>();
        FIELDS.forEach((role, pattern) -> names.stream()
                .filter(n -> ToolText.match(pattern, ToolText.lastPart(n, '.')))
                .findFirst()
                .ifPresent(found -> out.put(role, found)));
        return out;
    }

    // Seen: the records' distinct values of a field, stripped, in the order first seen.
    private static List<String> values(final List<Map<String, Object>> records, final String field) {
        final List<String> seen = new ArrayList<>();
        for (final Map<String, Object> record : records) {
            final String value = ToolText.strip(JsonValues.text(record.get(field)));
            if (!value.isEmpty() && !seen.contains(value)) {
                seen.add(value);
            }
        }
        return seen;
    }

    // The field that tells this kind's actions apart: an action-like name with a handful of values, one of them
    // recognised.
    private static String splitter(final List<Map<String, Object>> records,
                                   final List<String> names,
                                   final Set<String> skip) {
        for (final String name : names) {
            if (skip.contains(name) || !ToolText.match(SPLITTER, ToolText.lastPart(name, '.'))) {
                continue;
            }
            final List<String> values = values(records, name);
            if (!values.isEmpty() && values.size() <= MAX_VALUES && values.stream().anyMatch(ActionRules::recognised)) {
                return name;
            }
        }
        return null;
    }

    private static boolean recognised(final String value) {
        return ToolText.match(PERMIT, value) || ToolText.match(DENY, value) || ToolText.match(CONNECT, value)
               || ToolText.match(CLOSE, value) || ToolText.search(AUTH, value) || ToolText.search(CONFIG, value)
               || ToolText.match(PROCESS, value) || ToolText.search(ALERT, value);
    }


    // --------------------------------------------------------------------------------


    /// The rules for one kind's records, the values left over, and the field that splits them.
    ///
    /// @param rules The rules, each `name`, `when` and `fields`.
    /// @param left  The splitter's values no rule takes.
    /// @param split The field that splits the kind's records, or null when none does.
    public record Found(List<Map<String, Object>> rules, List<String> left, String split) {

    }

    // The rules for one kind's records, split on one field: a rule per action, each taking the values no rule
    // before it took.
    private static final class RuleMaker {

        private final List<Map<String, Object>> records;
        private final List<String> names;
        private final List<Map<String, Object>> base;
        private final String prefix;
        private final String split;
        private final List<String> values;
        private final List<Map<String, Object>> rules = new ArrayList<>();
        private final Set<String> used = new LinkedHashSet<>();

        /// @param records The kind's records.
        /// @param names   Their field names.
        /// @param base    The conditions every rule starts with.
        /// @param prefix  The rules' names' prefix.
        /// @param split   The field that splits them.
        RuleMaker(final List<Map<String, Object>> records,
                  final List<String> names,
                  final List<Map<String, Object>> base,
                  final String prefix,
                  final String split) {
            this.records = records;
            this.names = names;
            this.base = base;
            this.prefix = prefix;
            this.split = split;
            this.values = values(records, split);
        }

        /// @param user        The user's field (or fields read in turn), or null.
        /// @param description The description's field (or fields read in turn), or null.
        /// @return The rules, the values left over, and the split field.
        Found make(final Object user, final Object description) {
            final Map<String, String> net = networkRoles(names);
            if (net.containsKey(SRC_IP) && net.containsKey(DST_IP)) {
                network("Permit", "permitted", PERMIT, net);
                network("Deny", "denied", DENY, net);
                network("Connect", "connected", CONNECT, net);
                network("Close", "closed", CLOSE, net);
            }
            logons(user);
            final List<String> logoffs = unused(v -> ToolText.search(LOGOFF, v));
            final List<Map<String, Object>> logoffFields = new ArrayList<>();
            logoffFields.add(dict(PATH, "EventDetail/Authenticate/Action", VALUE, "Logoff"));
            if (JsonValues.truthy(user)) {
                logoffFields.add(withRead("EventDetail/Authenticate/User/Id", user));
            }
            rule("logoff", logoffs, logoffFields);
            final List<String> changes = unused(v -> ToolText.search(CONFIG, v));
            final List<Map<String, Object>> changeFields = new ArrayList<>();
            changeFields.add(dict(PATH, "EventDetail/Update/After/Configuration/Type", FIELD, split));
            if (JsonValues.truthy(description)) {
                changeFields.add(withRead("EventDetail/Update/After/Configuration/Description", description));
            }
            rule("config_change", changes, changeFields);
            service(description);
            alert();
            final List<String> left = values.stream().filter(v -> !used.contains(v)).toList();
            return new Found(rules, left, split);
        }

        private void network(final String side, final String name, final Pattern pattern,
                             final Map<String, String> net) {
            final List<String> chosen = unused(v -> ToolText.match(pattern, v) && addressed(v, net));
            // The kind's protocols, seen in any of them.
            rule(name, chosen, networkFields(side, net, records));
        }

        // Only a value whose records carry both addresses: a firewall's SYSTEM START has none.
        private boolean addressed(final String value, final Map<String, String> net) {
            final List<Map<String, Object>> held = records.stream()
                    .filter(r -> value.equals(r.get(split)))
                    .toList();
            return !held.isEmpty() && held.stream().allMatch(r ->
                    !ToolText.strip(JsonValues.text(r.get(net.get(SRC_IP)))).isEmpty()
                    && !ToolText.strip(JsonValues.text(r.get(net.get(DST_IP)))).isEmpty());
        }

        private void logons(final Object user) {
            final List<String> logons = unused(v -> ToolText.search(AUTH, v) && !ToolText.search(LOGOFF, v));
            final Map<String, Object> outcome = new LinkedHashMap<>();
            for (final String value : logons) {
                if (ToolText.search(FAILED, value) || ToolText.search(SUCCEEDED, value)) {
                    outcome.put(value, ToolText.search(FAILED, value)
                            ? "false"
                            : "true");
                }
            }
            final List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(dict(PATH, "EventDetail/Authenticate/Action", VALUE, "Logon"));
            if (JsonValues.truthy(user)) {
                fields.add(withRead("EventDetail/Authenticate/User/Id", user));
            }
            if (!outcome.isEmpty() && outcome.size() == logons.size()) {
                fields.add(dict(PATH, "EventDetail/Authenticate/Outcome/Success", FIELD, split, MAP, outcome));
            }
            rule("logon", logons, fields);
        }

        private void service(final Object description) {
            final List<String> processes = unused(v -> ToolText.match(PROCESS, v));
            final Map<String, Object> actions = new LinkedHashMap<>();
            processes.forEach(v -> actions.put(v, ToolText.search(STOPPED, v)
                    ? "Shutdown"
                    : "Startup"));
            final List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(dict(PATH, "EventDetail/Process/Action", FIELD, split, MAP, actions));
            fields.add(dict(PATH, "EventDetail/Process/Type", VALUE, "Service"));
            fields.add(withRead("EventDetail/Process/Command", JsonValues.truthy(description)
                    ? description
                    : split));
            rule("service", processes, fields);
        }

        private void alert() {
            final List<String> alerts = unused(v -> ToolText.search(ALERT, v));
            final Map<String, Object> types = new LinkedHashMap<>();
            for (final String value : alerts) {
                final String type;
                if (ToolText.search(NETWORK_ALERT, value)) {
                    type = "Network";
                } else if (ToolText.search(ERROR_ALERT, value)) {
                    type = "Error";
                } else {
                    type = "Other";
                }
                types.put(value, type);
            }
            final List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(dict(PATH, "EventDetail/Alert/Type", FIELD, split, MAP, types));
            fields.addAll(severityFields(records, names));
            rule("alert", alerts, fields);
        }

        private List<String> unused(final Predicate<String> test) {
            return values.stream().filter(v -> test.test(v) && !used.contains(v)).toList();
        }

        private void rule(final String name, final List<String> chosen, final List<Map<String, Object>> fields) {
            if (chosen.isEmpty()) {
                return;
            }
            final Map<String, Object> condition = chosen.size() == 1
                    ? dict(FIELD, split, EQUALS, chosen.getFirst())
                    : dict(FIELD, split, ONE_OF, new ArrayList<>(chosen));
            final List<Map<String, Object>> when = new ArrayList<>(base);
            when.add(condition);
            rules.add(dict("name", prefix == null || prefix.isEmpty()
                    ? name
                    : prefix + "_" + name, WHEN, when, "fields", fields));
            used.addAll(chosen);
        }

        private static Map<String, Object> withRead(final String path, final Object source) {
            final Map<String, Object> entry = dict(PATH, path);
            entry.putAll(read(source));
            return entry;
        }
    }
}
