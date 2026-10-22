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

import stroom.ai.impl.tools.fielddoc.FieldDocFixtures;
import stroom.ai.impl.tools.generation.XmlElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// The Stroom MCP server's CEF test fixtures (`tests/test_cef.py`): three Events (an Authenticate with a failed
/// outcome and Data, a View, a Network Deny) and the plan drafted from them.
final class CefFixtures {

    /// The sample Events.
    static final String EVENTS = """
            <Events xmlns="event-logging:3" Version="3.5.2">
            <Event><EventTime><TimeCreated>2026-09-27T23:48:10.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Environment>Prod</Environment>\
            <Organisation>Delinea</Organisation><Version>11.4</Version></System>
              <Device><HostName>ss-web-02</HostName><IPAddress>10.1.2.3</IPAddress></Device><Client>\
            <IPAddress>192.0.2.45</IPAddress></Client><User><Id>priya.patel</Id></User></EventSource>
             <EventDetail><TypeId>User-Login</TypeId><Description>User logged in</Description><Authenticate>\
            <Action>Logon</Action><User><Id>priya.patel</Id></User><Outcome><Success>false</Success></Outcome>
              <Data Name="session_id" Value="6857"/><Data Name="note" Value="a=b|c\\d&#10;second line"/>\
            </Authenticate></EventDetail></Event>
            <Event><EventTime><TimeCreated>2026-09-27T23:56:15.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Organisation>Delinea</Organisation><Version>11.4</Version>\
            </System>
              <Device><HostName>ss-web-01</HostName></Device><Client><IPAddress>ss-client.domain.com</IPAddress>\
            </Client><User><Id>riley.chen</Id></User></EventSource>
             <EventDetail><TypeId>Secret-View</TypeId><View><Resource><Type>Secret</Type><Name>Firewall | Edge</Name>\
            <Id>10005</Id></Resource></View></EventDetail></Event>
            <Event><EventTime><TimeCreated>2026-09-28T00:01:06.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Organisation>Delinea</Organisation></System><Device>\
            <HostName>fw1</HostName></Device></EventSource>
             <EventDetail><TypeId>Net</TypeId><Network><Deny><Source><Device><IPAddress>10.0.0.1</IPAddress></Device>\
            <Port>5555</Port></Source>
              <Destination><Device><IPAddress>10.0.0.2</IPAddress></Device><Port>443</Port></Destination>\
            <TransportProtocol>TCP</TransportProtocol></Deny></Network></EventDetail></Event>
            </Events>""";

    private CefFixtures() {
        // Static only.
    }

    /// @return The sample's Events.
    static List<XmlElement> events() {
        return CefEvents.eventsOf(EVENTS);
    }

    /// @param customKeys Whether keys outside the dictionary may be sent.
    /// @param overrides  Overrides, as JSON-like values.
    /// @param output     `kafka` or `text`.
    /// @return The plan drafted from the sample, for topic `arcsight-cef` (`drafted(...)`).
    static CefDraft.Drafted drafted(final boolean customKeys, final List<Map<String, Object>> overrides,
                                    final String output) {
        return CefDraft.draft(events(), customKeys, overrides.stream().map(CefOverride::fromJson).toList(), null,
                output, "arcsight-cef", null);
    }

    /// @return The plan drafted from the sample, as Kafka records.
    static CefDraft.Drafted drafted() {
        return drafted(false, List.of(), CefPlan.KAFKA);
    }

    /// @param xslt An XSLT.
    /// @param xml  What it runs over.
    /// @return What it writes.
    static String run(final String xslt, final String xml) {
        return FieldDocFixtures.transform(xslt, xml);
    }

    /// @param plan A plan.
    /// @return Its fields by kind (`common` too), then by path.
    static Map<String, Map<String, CefField>> byKind(final CefPlan plan) {
        final Map<String, Map<String, CefField>> out = new HashMap<>();
        out.put("common", byPath(plan.common()));
        plan.events().forEach((kind, fields) -> out.put(kind, byPath(fields)));
        return out;
    }

    private static Map<String, CefField> byPath(final List<CefField> fields) {
        final Map<String, CefField> out = new HashMap<>();
        fields.forEach(f -> out.put(f.path(), f));
        return out;
    }
}
