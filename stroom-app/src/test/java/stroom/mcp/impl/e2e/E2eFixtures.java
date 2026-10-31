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

import stroom.util.json.JsonUtil;

import java.util.List;
import java.util.Map;

/// The samples, mappings and shared XSLTs the end-to-end suites share: the translation cases, the generator's
/// firewall sample and mappings, and the shared XSLTs. Each mapping is a fresh copy, for a test to change.
@SuppressWarnings("unchecked")
final class E2eFixtures {

    /// `FIREWALL['sample']`: blank fields, some just a space, as the device writes them.
    static final String FIREWALL_SAMPLE = """
            timestamp,device,event_type,severity,src_ip,src_port,dst_ip,dst_port,protocol,action,rule_id,username,\
            message
            2026-10-01T09:00:12+10:00,FW-EDGE-01,TRAFFIC,INFO,192.0.2.10,54321,198.51.100.20,443,TCP,ALLOW,1001,,\
            Outbound HTTPS allowed
            2026-10-01T09:01:05+10:00,FW-EDGE-01,TRAFFIC,WARNING,203.0.113.45,49822,192.0.2.25,22,TCP,DENY,2003,,\
            Inbound SSH blocked
            2026-10-01T09:10:03+10:00,FW-EDGE-01,SYSTEM,INFO,,,,,N/A,START,, ,Firewall logging service started
            2026-10-01T09:11:15+10:00,FW-EDGE-01,ADMIN,INFO,192.0.2.100,,,,HTTPS,LOGIN_SUCCESS,,admin,\
            Administrator logged in
            2026-10-01T09:12:02+10:00,FW-EDGE-01,ADMIN,WARNING,203.0.113.90,,,,HTTPS,LOGIN_FAILED,,admin,\
            Failed administrator login
            2026-10-01T09:16:08+10:00,FW-EDGE-01,ADMIN,INFO,192.0.2.100,,,,HTTPS,LOGOUT,,admin,\
            Administrator logged out
            2026-10-01T09:17:24+10:00,FW-EDGE-01,SYSTEM,ERROR,,,,,N/A,VPN_TUNNEL_DOWN,,,VPN tunnel branch-01 went down
            2026-10-01T09:18:50+10:00,FW-EDGE-01,SYSTEM,NOTICE,,,,,N/A,VPN_TUNNEL_UP,,,VPN tunnel branch-01 restored
            """;

    /// `CASES['xml']['sample']`.
    static final String XML_SAMPLE = "<logons><logon><when>2026-09-28 12:00:00</when><who>frank</who><host>ws06</host>"
                                     + "<addr>10.0.2.6</addr><result>SUCCESS</result></logon><logon><when>2026-09-28 "
                                     + "12:01:00</when><who>grace</who><host>ws07</host><addr>10.0.2.7</addr><result>"
                                     + "FAILURE</result></logon></logons>";

    /// The shared XSLT writing Event/Meta and EventSource/Device (`e2e_shared_xslt.COMMON_EVENT`).
    static final String COMMON_EVENT = "E2E-Common-Event-V1";
    /// `e2e_shared_xslt.SHARED_EVENT`.
    static final String SHARED_EVENT = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xmlns="event-logging:3" xmlns:stroom="stroom" \
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
              <!-- The stream's GUID, for tracing an event back to what was received. -->
              <xsl:template name="eventMeta">
                <Meta ContentType="stroom-guid"><xsl:value-of select="stroom:meta('GUID')" /></Meta>
              </xsl:template>
              <!-- The device that sent the stream, from the collector's MyHostName header; its IP when the record \
            has one. -->
              <xsl:template name="eventSourceDevice">
                <xsl:param name="ip" />
                <Device>
                  <HostName><xsl:value-of select="stroom:meta('MyHostName')" /></HostName>
                  <xsl:if test="$ip"><IPAddress><xsl:value-of select="$ip" /></IPAddress></xsl:if>
                </Device>
              </xsl:template>
            </xsl:stylesheet>
            """;

    private static final String SYSTEM = """
            {"path": "EventSource/System/Name", "value": "E2E"},
            {"path": "EventSource/System/Environment", "value": "Dev"},
            {"path": "EventSource/Generator", "value": "e2e-generator"}""";

    private static final String FIREWALL_MAPPING = """
            {"input": "data_splitter",
             "common": [
              {"path": "EventTime/TimeCreated", "field": "timestamp", "time_format": "yyyy-MM-dd'T'HH:mm:ssXXX"},
              @SYSTEM@,
              {"path": "EventSource/Device/HostName", "field": "device"},
              {"path": "EventSource/User/Id", "field": "username"},
              {"path": "EventDetail/TypeId",
               "xpath": "concat(data[@name='event_type']/@value, '-', data[@name='action']/@value)"},
              {"path": "EventDetail/Description", "field": "message"}],
             "events": [
              {"name": "traffic", "when": [{"field": "event_type", "equals": "TRAFFIC"}], "fields": [
                {"path": "EventDetail/Network/Open/Source/Device/IPAddress", "field": "src_ip"},
                {"path": "EventDetail/Network/Open/Source/Port", "field": "src_port"},
                {"path": "EventDetail/Network/Open/Destination/Device/IPAddress", "field": "dst_ip"},
                {"path": "EventDetail/Network/Open/Destination/Port", "field": "dst_port"},
                {"path": "EventDetail/Network/Open/Outcome/Success", "field": "action",
                 "map": {"ALLOW": "true", "DENY": "false"}},
                {"path": "EventDetail/Network/Open/Outcome/Permitted", "field": "action",
                 "map": {"ALLOW": "true", "DENY": "false"}},
                {"path": "EventDetail/Network/Open/Data", "data_name": "RuleId", "field": "rule_id"}]},
              {"name": "logon", "when": [{"field": "event_type", "equals": "ADMIN"},
                                         {"field": "action", "one_of": ["LOGIN_SUCCESS", "LOGIN_FAILED"]}],
               "fields": [
                {"path": "EventSource/Client/IPAddress", "field": "src_ip"},
                {"path": "EventDetail/Authenticate/User/Id", "field": "username"},
                {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                {"path": "EventDetail/Authenticate/Outcome/Success", "field": "action",
                 "map": {"LOGIN_SUCCESS": "true", "LOGIN_FAILED": "false"}}]},
              {"name": "logoff", "when": [{"field": "event_type", "equals": "ADMIN"},
                                          {"field": "action", "equals": "LOGOUT"}],
               "fields": [
                {"path": "EventSource/Client/IPAddress", "field": "src_ip"},
                {"path": "EventDetail/Authenticate/User/Id", "field": "username"},
                {"path": "EventDetail/Authenticate/Action", "value": "Logoff"}]},
              {"name": "system", "when": [{"field": "event_type", "equals": "SYSTEM"}], "fields": [
                {"path": "EventDetail/Alert/Type", "value": "Other"},
                {"path": "EventDetail/Alert/Severity", "field": "severity",
                 "map": {"WARNING": "Minor", "ERROR": "Major", "CRITICAL": "Critical"}, "default": "Info"}]}]}
            """.replace("@SYSTEM@", SYSTEM);

    private static final String CSV_MAPPING = """
            {"input": "data_splitter",
             "common": [
              {"path": "EventTime/TimeCreated", "field": "time", "time_format": "yyyy-MM-dd'T'HH:mm:ss"},
              @SYSTEM@,
              {"path": "EventSource/Device/HostName", "field": "host"},
              {"path": "EventSource/Device/IPAddress", "field": "ip"},
              {"path": "EventSource/User/Id", "field": "user"}],
             "events": [
              {"name": "failed logon", "when": [{"field": "result", "equals": "fail"}], "fields": [
                {"path": "EventDetail/TypeId", "value": "Logon"},
                {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                {"path": "EventDetail/Authenticate/User/Id", "field": "user"},
                {"path": "EventDetail/Authenticate/Outcome/Success", "value": "false"},
                {"path": "EventDetail/Description", "value": "Failed logon"},
                {"path": "EventDetail/Authenticate/Data", "data_name": "result", "field": "result"}]},
              {"name": "logon", "fields": [
                {"path": "EventDetail/TypeId", "value": "Logon"},
                {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                {"path": "EventDetail/Authenticate/User/Id", "field": "user"},
                {"path": "EventDetail/Authenticate/Outcome/Success", "field": "result", "map": {"ok": "true"}}]}]}
            """.replace("@SYSTEM@", SYSTEM);

    private static final String XML_MAPPING = """
            {"input": "xml", "root": "logons", "record": "logon",
             "common": [
              {"path": "EventTime/TimeCreated", "field": "when", "time_format": "yyyy-MM-dd HH:mm:ss"},
              @SYSTEM@,
              {"path": "EventSource/Device/HostName", "field": "host"},
              {"path": "EventSource/Device/IPAddress", "field": "addr"},
              {"path": "EventSource/User/Id", "field": "who"}],
             "events": [
              {"name": "logon", "when": [{"field": "result", "one_of": ["SUCCESS", "FAILURE"]}], "fields": [
                {"path": "EventDetail/TypeId", "value": "Logon"},
                {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                {"path": "EventDetail/Authenticate/User/Id", "field": "who"},
                {"path": "EventDetail/Authenticate/Outcome/Success", "field": "result",
                 "map": {"SUCCESS": "true", "FAILURE": "false"}}]}]}
            """.replace("@SYSTEM@", SYSTEM);

    private E2eFixtures() {
    }

    /// @return `MAPPINGS['firewall']`, a fresh copy.
    static Map<String, Object> firewallMapping() {
        return JsonUtil.readValue(FIREWALL_MAPPING, Map.class);
    }

    /// @return `MAPPINGS['csv']`, a fresh copy.
    static Map<String, Object> csvMapping() {
        return JsonUtil.readValue(CSV_MAPPING, Map.class);
    }

    /// @return `MAPPINGS['xml']`, a fresh copy.
    static Map<String, Object> xmlMapping() {
        return JsonUtil.readValue(XML_MAPPING, Map.class);
    }

    /// @param mapping A mapping.
    /// @return Its rules.
    static List<Map<String, Object>> rules(final Map<String, Object> mapping) {
        return (List<Map<String, Object>>) mapping.get("events");
    }

    /// @param mapping A mapping.
    /// @param name    A rule's name.
    /// @return That rule.
    static Map<String, Object> rule(final Map<String, Object> mapping, final String name) {
        return rules(mapping).stream()
                .filter(rule -> name.equals(rule.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No rule " + name));
    }

    /// @param ruleOrMapping A rule (its fields) or a mapping (its common entries).
    /// @return Those entries.
    static List<Map<String, Object>> entries(final Map<String, Object> ruleOrMapping) {
        return (List<Map<String, Object>>) (ruleOrMapping.containsKey("fields")
                ? ruleOrMapping.get("fields")
                : ruleOrMapping.get("common"));
    }
}
