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

package stroom.ai.impl.tools.cef;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/// ArcSight's CEF dictionary as the CEF mapping uses it: each key's full name, type, length and meaning (from the
/// CEF implementation standard), its custom slots (`cs1`-`cs6`, `cn1`-`cn3`, `cfp1`-`cfp4`, `deviceCustomDate1`-`2`,
/// `flexString1`-`2`, `flexDate1`, each with its Label key), the header's fields, and where Event values go by
/// default.
public final class CefKeys {

    /// The event-logging namespace.
    public static final String EL = "event-logging:3";
    /// Each key, by its short name.
    public static final Map<String, KeyInfo> KEYS;
    /// ArcSight's custom slots by kind (`string`, `long`, `float`, `time`): built-in keys whose meaning a label
    /// gives, per event.
    public static final Map<String, List<String>> SLOTS;
    /// Every custom slot.
    public static final Set<String> SLOT_KEYS;
    /// Each key's short name, by its full name (`deviceCustomString6` -> `cs6`).
    public static final Map<String, String> FULL_NAMES;
    /// The header's fields after `CEF:0`, in order.
    public static final List<HeaderField> HEADER = List.of(
            new HeaderField("vendor", "Device Vendor", 63),
            new HeaderField("product", "Device Product", 63),
            new HeaderField("version", "Device Version", 31),
            new HeaderField("signature", "Device Event Class ID", 1023),
            new HeaderField("name", "Name", 512),
            new HeaderField("severity", "Severity", 10));
    /// Elements of EventDetail that are not its action.
    public static final Set<String> NOT_ACTION = Set.of("TypeId", "Description", "Classification", "Purpose",
            "Data");
    /// Where Event values go by default, first free key wins; `*` is any one step (the action element, or
    /// Network's).
    public static final List<Home> HOMES = homes();
    /// Event values the header carries by default (so not repeated in the extension).
    public static final Map<String, String> HEADER_PATHS = headerPaths();
    /// The event's action element and its Action, as an XPath: what names an event with no Description.
    public static final String ACTION_NAME = "string-join((local-name(EventDetail/*[not(self::TypeId or "
                                             + "self::Description or self::Classification or self::Purpose or "
                                             + "self::Data)][1]), EventDetail/*/Action), ' ')";
    /// The severities CEF takes.
    public static final Set<String> SEVERITIES = severities();

    static {
        final Map<String, KeyInfo> keys = new LinkedHashMap<>();
        key(keys, "act", "deviceAction", "string", 63, "Action the event records (e.g. Logon, View, blocked)");
        key(keys, "app", "applicationProtocol", "string", 31, "Application protocol, e.g. HTTP, SSH");
        key(keys, "cat", "deviceEventCategory", "string", 1023, "Category the device gives the event");
        key(keys, "cnt", "baseEventCount", "int", null, "How many times the same event was seen");
        key(keys, "dhost", "destinationHostName", "string", 1023, "Destination (target) host name");
        key(keys, "dmac", "destinationMacAddress", "mac", null, "Destination MAC address");
        key(keys, "dntdom", "destinationNtDomain", "string", 255, "Destination (target) user's domain");
        key(keys, "dpid", "destinationProcessId", "int", null, "Destination process id");
        key(keys, "dpriv", "destinationUserPrivileges", "string", 1023, "Destination user's privileges");
        key(keys, "dproc", "destinationProcessName", "string", 1023, "Destination process name");
        key(keys, "dpt", "destinationPort", "int", null, "Destination port");
        key(keys, "dst", "destinationAddress", "ip", null, "Destination IPv4 address");
        key(keys, "dtz", "deviceTimeZone", "string", 255, "Time zone of the device that generated the event");
        key(keys, "duid", "destinationUserId", "string", 1023, "Destination (target) user id");
        key(keys, "duser", "destinationUserName", "string", 1023, "Destination (target) user: the account acted on");
        key(keys, "dvc", "deviceAddress", "ip", null, "IPv4 address of the device that generated the event");
        key(keys, "dvchost", "deviceHostName", "string", 100, "Host name of the device that generated the event");
        key(keys, "dvcmac", "deviceMacAddress", "mac", null, "MAC address of the device that generated the event");
        key(keys, "dvcpid", "deviceProcessId", "int", null, "Process id on the device that generated the event");
        key(keys, "end", "endTime", "time", null, "When the activity ended");
        key(keys, "externalId", "externalId", "string", 40, "The event's id in the source system");
        key(keys, "fileHash", "fileHash", "string", 255, "File's hash");
        key(keys, "fileId", "fileId", "string", 1023, "File's id");
        key(keys, "filePath", "filePath", "string", 1023, "File's full path");
        key(keys, "filePermission", "filePermission", "string", 1023, "File's permissions");
        key(keys, "fileType", "fileType", "string", 1023, "File's type");
        key(keys, "fname", "fileName", "string", 1023, "File's name");
        key(keys, "fsize", "fileSize", "int", null, "File's size");
        key(keys, "in", "bytesIn", "int", null, "Bytes received");
        key(keys, "msg", "message", "string", 1023, "Free text about the event");
        key(keys, "oldFileName", "oldFileName", "string", 1023, "File's name before the change");
        key(keys, "oldFilePath", "oldFilePath", "string", 1023, "File's path before the change");
        key(keys, "out", "bytesOut", "int", null, "Bytes sent");
        key(keys, "outcome", "eventOutcome", "string", 63, "Outcome: success or failure");
        key(keys, "proto", "transportProtocol", "string", 31, "Transport protocol, e.g. TCP, UDP");
        key(keys, "reason", "reason", "string", 1023, "Why the outcome was what it was");
        key(keys, "request", "requestUrl", "string", 1023, "URL requested");
        key(keys, "requestClientApplication", "requestClientApplication", "string", 1023,
                "Client application (user agent)");
        key(keys, "requestMethod", "requestMethod", "string", 1023, "Request method, e.g. GET");
        key(keys, "rt", "deviceReceiptTime", "time", null, "When the event happened");
        key(keys, "shost", "sourceHostName", "string", 1023, "Source host name");
        key(keys, "smac", "sourceMacAddress", "mac", null, "Source MAC address");
        key(keys, "sntdom", "sourceNtDomain", "string", 255, "Source user's domain");
        key(keys, "sourceServiceName", "sourceServiceName", "string", 1023, "Service behind the source");
        key(keys, "destinationServiceName", "destinationServiceName", "string", 1023,
                "Service behind the destination");
        key(keys, "spid", "sourceProcessId", "int", null, "Source process id");
        key(keys, "spriv", "sourceUserPrivileges", "string", 1023, "Source user's privileges");
        key(keys, "sproc", "sourceProcessName", "string", 1023, "Source process name");
        key(keys, "spt", "sourcePort", "int", null, "Source port");
        key(keys, "src", "sourceAddress", "ip", null, "Source IPv4 address");
        key(keys, "start", "startTime", "time", null, "When the activity started");
        key(keys, "suid", "sourceUserId", "string", 1023, "Source user id");
        key(keys, "suser", "sourceUserName", "string", 1023, "Source user: the account that acted");
        key(keys, "deviceExternalId", "deviceExternalId", "string", 255, "The device's own id");
        key(keys, "deviceFacility", "deviceFacility", "string", 1023, "Facility that generated the event");
        key(keys, "deviceInboundInterface", "deviceInboundInterface", "string", 128,
                "Interface the traffic came in on");
        key(keys, "deviceOutboundInterface", "deviceOutboundInterface", "string", 128,
                "Interface the traffic went out on");
        key(keys, "deviceProcessName", "deviceProcessName", "string", 1023,
                "Process on the device that generated the event");
        key(keys, "deviceDirection", "deviceDirection", "int", null, "Direction: 0 inbound, 1 outbound");

        final Map<String, List<String>> slots = new LinkedHashMap<>();
        slots.put("string", List.of("cs1", "cs2", "cs3", "cs4", "cs5", "cs6", "flexString1", "flexString2"));
        slots.put("long", List.of("cn1", "cn2", "cn3"));
        slots.put("float", List.of("cfp1", "cfp2", "cfp3", "cfp4"));
        slots.put("time", List.of("deviceCustomDate1", "deviceCustomDate2", "flexDate1"));
        for (int n = 1; n <= 6; n++) {
            key(keys, "cs" + n, "deviceCustomString" + n, "string", 4000, "Custom string, named by its label");
        }
        for (int n = 1; n <= 3; n++) {
            key(keys, "cn" + n, "deviceCustomNumber" + n, "long", null, "Custom number, named by its label");
        }
        for (int n = 1; n <= 4; n++) {
            key(keys, "cfp" + n, "deviceCustomFloatingPoint" + n, "float", null,
                    "Custom decimal, named by its label");
        }
        for (final String k : List.of("deviceCustomDate1", "deviceCustomDate2", "flexDate1")) {
            key(keys, k, k, "time", null, "Custom time, named by its label");
        }
        for (final String k : List.of("flexString1", "flexString2")) {
            key(keys, k, k, "string", 1023, "Custom string, named by its label");
        }
        final Set<String> slotKeys = new LinkedHashSet<>();
        slots.values().forEach(slotKeys::addAll);
        for (final String k : new TreeSet<>(slotKeys)) {
            key(keys, k + "Label", keys.get(k).fullName() + "Label", "string", 1023, "The name of " + k);
        }
        final Map<String, String> fullNames = new LinkedHashMap<>();
        keys.forEach((k, info) -> fullNames.put(info.fullName(), k));
        KEYS = Collections.unmodifiableMap(keys);
        SLOTS = Collections.unmodifiableMap(slots);
        SLOT_KEYS = Collections.unmodifiableSet(slotKeys);
        FULL_NAMES = Collections.unmodifiableMap(fullNames);
    }

    private CefKeys() {
        // Static only.
    }

    /// A CEF key from its short or full name.
    ///
    /// @param name A key's short or full name, e.g. `deviceCustomString6`.
    /// @return Its short name, e.g. `cs6`; a name outside the dictionary as it is.
    public static String keyOf(final String name) {
        return KEYS.containsKey(name)
                ? name
                : FULL_NAMES.getOrDefault(name, name);
    }

    /// `described`: a key for a reader.
    ///
    /// @param key   A key's short name.
    /// @param label A custom slot's label, or null.
    /// @return E.g. `cs6 (deviceCustomString6): Custom string, named by its label, labelled 'Target user'
    /// (cs6Label)`.
    public static String described(final String key, final String label) {
        final KeyInfo info = KEYS.getOrDefault(key, new KeyInfo(key, "", null, "Key outside the CEF dictionary"));
        final String said = !info.fullName().equals(key)
                ? key + " (" + info.fullName() + ")"
                : key;
        return said + ": " + info.meaning() + (label != null && !label.isEmpty()
                ? ", labelled '" + label + "' (" + key + "Label)"
                : "");
    }

    private static void key(final Map<String, KeyInfo> keys, final String key, final String fullName,
                            final String type, final Integer maxLength, final String meaning) {
        keys.put(key, new KeyInfo(fullName, type, maxLength, meaning));
    }

    private static List<Home> homes() {
        final String[][] homes = {
                {"EventTime/TimeCreated", "rt", "epoch_ms"},
                {"EventSource/Device/HostName", "dvchost", "text"}, {"EventSource/Device/IPAddress", "dvc", "text"},
                {"EventSource/Device/MACAddress", "dvcmac", "text"},
                {"EventSource/Client/HostName", "shost", "text"}, {"EventSource/Client/IPAddress", "src", "text"},
                {"EventSource/Client/MACAddress", "smac", "text"}, {"EventSource/Client/Port", "spt", "text"},
                {"EventSource/Server/HostName", "dhost", "text"}, {"EventSource/Server/IPAddress", "dst", "text"},
                {"EventSource/Server/MACAddress", "dmac", "text"}, {"EventSource/Server/Port", "dpt", "text"},
                {"EventSource/User/Id", "suser", "text"}, {"EventSource/User/Domain", "sntdom", "text"},
                {"EventDetail/*/Action", "act", "text"},
                {"EventDetail/*/Outcome/Success", "outcome", "outcome"},
                {"EventDetail/*/Outcome/Reason", "reason", "text"},
                {"EventDetail/*/Outcome/Description", "reason", "text"},
                {"EventDetail/*/User/Id", "duser", "text"}, {"EventDetail/*/User/Domain", "dntdom", "text"},
                {"EventDetail/*/Device/HostName", "dhost", "text"}, {"EventDetail/*/Device/IPAddress", "dst", "text"},
                {"EventDetail/*/Device/MACAddress", "dmac", "text"},
                {"EventDetail/*/File/Path", "filePath", "text"}, {"EventDetail/*/File/Name", "fname", "text"},
                {"EventDetail/*/File/Size", "fsize", "text"}, {"EventDetail/*/File/Hash", "fileHash", "text"},
                {"EventDetail/*/File/Type", "fileType", "text"}, {"EventDetail/*/Folder/Path", "filePath", "text"},
                {"EventDetail/*/Source/File/Path", "oldFilePath", "text"},
                {"EventDetail/*/Source/File/Name", "oldFileName", "text"},
                {"EventDetail/*/Destination/File/Path", "filePath", "text"},
                {"EventDetail/*/Destination/File/Name", "fname", "text"},
                {"EventDetail/*/Resource/URL", "request", "text"},
                {"EventDetail/Network/*/Source/Device/IPAddress", "src", "text"},
                {"EventDetail/Network/*/Source/Device/HostName", "shost", "text"},
                {"EventDetail/Network/*/Source/Device/MACAddress", "smac", "text"},
                {"EventDetail/Network/*/Source/Port", "spt", "text"},
                {"EventDetail/Network/*/Destination/Device/IPAddress", "dst", "text"},
                {"EventDetail/Network/*/Destination/Device/HostName", "dhost", "text"},
                {"EventDetail/Network/*/Destination/Device/MACAddress", "dmac", "text"},
                {"EventDetail/Network/*/Destination/Port", "dpt", "text"},
                {"EventDetail/Network/*/TransportProtocol", "proto", "text"},
                {"EventDetail/Network/*/ApplicationProtocol", "app", "text"},
                {"EventDetail/Network/*/Outcome/Success", "outcome", "outcome"},
                {"EventDetail/Network/*/ProcessName", "sproc", "text"},
                {"EventDetail/Process/ProcessId", "dpid", "text"},
                {"EventDetail/Alert/Type", "cat", "text"}, {"EventDetail/Alert/Subject", "msg", "text"}};
        return java.util.Arrays.stream(homes).map(h -> new Home(h[0], h[1], h[2])).toList();
    }

    private static Map<String, String> headerPaths() {
        final Map<String, String> paths = new LinkedHashMap<>();
        paths.put("vendor", "EventSource/System/Organisation");
        paths.put("product", "EventSource/System/Name");
        paths.put("version", "EventSource/System/Version");
        paths.put("signature", "EventDetail/TypeId");
        paths.put("name", "EventDetail/Description");
        return Collections.unmodifiableMap(paths);
    }

    private static Set<String> severities() {
        final Set<String> severities = new LinkedHashSet<>();
        for (int n = 0; n <= 10; n++) {
            severities.add(String.valueOf(n));
        }
        severities.addAll(List.of("Low", "Medium", "High", "Very-High", "Unknown"));
        return Collections.unmodifiableSet(severities);
    }


    // --------------------------------------------------------------------------------


    /// One key of the dictionary.
    ///
    /// @param fullName  ArcSight's full name, e.g. `deviceCustomString1`.
    /// @param type      Its type: `string`, `int`, `long`, `float`, `ip`, `mac` or `time`.
    /// @param maxLength Its length, or null for none.
    /// @param meaning   What it holds.
    public record KeyInfo(String fullName, String type, Integer maxLength, String meaning) {

    }

    /// One field of the header.
    ///
    /// @param name   The plan's name for it, e.g. `vendor`.
    /// @param said   CEF's name for it, e.g. `Device Vendor`.
    /// @param length Its length.
    public record HeaderField(String name, String said, int length) {

    }

    /// Where an Event value goes by default.
    ///
    /// @param pattern   The Event path, `*` for any one step.
    /// @param key       The key.
    /// @param transform `text`, `epoch_ms` or `outcome`.
    public record Home(String pattern, String key, String transform) {

    }
}
