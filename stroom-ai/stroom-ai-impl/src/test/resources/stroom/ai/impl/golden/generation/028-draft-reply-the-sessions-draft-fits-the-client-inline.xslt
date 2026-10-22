<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:variable name="severity_to_severity" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'INFO'" select="'Info'"/>
      <xsl:map-entry key="'WARNING'" select="'Minor'"/>
      <xsl:map-entry key="'ERROR'" select="'Major'"/>
      <xsl:map-entry key="'DEBUG'" select="'Info'"/>
      <xsl:map-entry key="'NOTICE'" select="'Info'"/>
      <xsl:map-entry key="'WARN'" select="'Minor'"/>
      <xsl:map-entry key="'ERR'" select="'Major'"/>
      <xsl:map-entry key="'CRITICAL'" select="'Critical'"/>
      <xsl:map-entry key="'CRIT'" select="'Critical'"/>
      <xsl:map-entry key="'ALERT'" select="'Critical'"/>
      <xsl:map-entry key="'EMERGENCY'" select="'Critical'"/>
      <xsl:map-entry key="'FATAL'" select="'Critical'"/>
    </xsl:map>
  </xsl:variable>
  <xsl:variable name="protocol_to_transport_protocol" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'TCP'" select="'TCP'"/>
      <xsl:map-entry key="'UDP'" select="'UDP'"/>
    </xsl:map>
  </xsl:variable>
  <xsl:template match="records">
    <Events Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
    <xsl:variable name="event_type" select="data[@name='event_type']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$event_type = 'TRAFFIC' and $action = 'ALLOW'">
        <xsl:apply-templates select="." mode="event_type_traffic_permitted"/>
      </xsl:when>
      <xsl:when test="$event_type = 'TRAFFIC' and $action = 'DENY'">
        <xsl:apply-templates select="." mode="event_type_traffic_denied"/>
      </xsl:when>
      <xsl:when test="$event_type = 'SYSTEM' and $action = 'CONFIG_SAVED'">
        <xsl:apply-templates select="." mode="event_type_system_config_change"/>
      </xsl:when>
      <xsl:when test="$event_type = 'SYSTEM' and $action = 'START'">
        <xsl:apply-templates select="." mode="event_type_system_service"/>
      </xsl:when>
      <xsl:when test="$event_type = 'SYSTEM' and $action = ('HIGH_CPU', 'VPN_TUNNEL_DOWN', 'VPN_TUNNEL_UP')">
        <xsl:apply-templates select="." mode="event_type_system_alert"/>
      </xsl:when>
      <xsl:when test="$event_type = 'ADMIN' and $action = ('LOGIN_SUCCESS', 'LOGIN_FAILED')">
        <xsl:apply-templates select="." mode="event_type_admin_logon"/>
      </xsl:when>
      <xsl:when test="$event_type = 'ADMIN' and $action = 'LOGOUT'">
        <xsl:apply-templates select="." mode="event_type_admin_logoff"/>
      </xsl:when>
      <xsl:when test="$event_type = 'ADMIN' and $action = ('CONFIG_CHANGE', 'CONFIG_BACKUP')">
        <xsl:apply-templates select="." mode="event_type_admin_config_change"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'traffic_permitted': records where event_type = TRAFFIC and action = ALLOW. Writes a Network/Permit event
       (TypeId from event_type) from src_ip, src_port, protocol, dst_ip, dst_port, severity, rule_id,
       bytes_sent, bytes_received. -->
  <xsl:template match="node()" mode="event_type_traffic_permitted">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Network>
          <Permit>
            <xsl:apply-templates select="." mode="source"/>
            <xsl:apply-templates select="." mode="destination"/>
            <xsl:apply-templates select="." mode="data_severity_to_bytes_received"/>
          </Permit>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'traffic_denied': records where event_type = TRAFFIC and action = DENY. Writes a Network/Deny event
       (TypeId from event_type) from src_ip, src_port, protocol, dst_ip, dst_port, severity, rule_id,
       bytes_sent, bytes_received. -->
  <xsl:template match="node()" mode="event_type_traffic_denied">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Network>
          <Deny>
            <xsl:apply-templates select="." mode="source"/>
            <xsl:apply-templates select="." mode="destination"/>
            <xsl:apply-templates select="." mode="data_severity_to_bytes_received"/>
          </Deny>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'system_config_change': records where event_type = SYSTEM and action = CONFIG_SAVED. Writes an Update
       event (TypeId from event_type) from action, message, severity, protocol, rule_id, bytes_sent,
       bytes_received. -->
  <xsl:template match="node()" mode="event_type_system_config_change">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:apply-templates select="." mode="event_detail"/>
    </Event>
  </xsl:template>
  <!-- Rule 'system_service': records where event_type = SYSTEM and action = START. Writes a Process event (TypeId
       from event_type) from action, message, severity, protocol, rule_id, bytes_sent, bytes_received. -->
  <xsl:template match="node()" mode="event_type_system_service">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Process>
          <xsl:if test="data[@name='action']/@value = ('START')">
            <Action>
              <xsl:value-of select="'Startup'"/>
            </Action>
          </xsl:if>
          <Type>Service</Type>
          <xsl:if test="normalize-space(data[@name='message']/@value)">
            <Command>
              <xsl:value-of select="data[@name='message']/@value"/>
            </Command>
          </xsl:if>
          <xsl:apply-templates select="." mode="data_severity_to_bytes_received_2"/>
        </Process>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'system_alert': records where event_type = SYSTEM and action in HIGH_CPU, VPN_TUNNEL_DOWN, VPN_TUNNEL_UP.
       Writes an Alert event (TypeId from event_type) from action, severity, protocol, rule_id, bytes_sent,
       bytes_received. -->
  <xsl:template match="node()" mode="event_type_system_alert">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="bytes_received" select="data[@name='bytes_received']/@value[normalize-space(.)]"/>
      <xsl:variable name="bytes_sent" select="data[@name='bytes_sent']/@value[normalize-space(.)]"/>
      <xsl:variable name="rule_id" select="data[@name='rule_id']/@value[normalize-space(.)]"/>
      <xsl:variable name="protocol" select="data[@name='protocol']/@value[normalize-space(.)]"/>
      <xsl:variable name="severity" select="data[@name='severity']/@value[normalize-space(.)]"/>
      <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
      <xsl:if test="normalize-space(data[@name='message']/@value) or normalize-space(data[@name='event_type']/@value) or $action = ('HIGH_CPU', 'VPN_TUNNEL_DOWN', 'VPN_TUNNEL_UP') or exists($severity_to_severity?($severity)) or $protocol or $rule_id or $bytes_sent or $bytes_received">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:apply-templates select="." mode="description"/>
          <xsl:if test="$action = ('HIGH_CPU', 'VPN_TUNNEL_DOWN', 'VPN_TUNNEL_UP') or exists($severity_to_severity?($severity)) or $protocol or $rule_id or $bytes_sent or $bytes_received">
            <Alert>
              <xsl:if test="$action = ('HIGH_CPU', 'VPN_TUNNEL_DOWN', 'VPN_TUNNEL_UP')">
                <Type>
                  <xsl:value-of select="if ($action = 'HIGH_CPU') then 'Other' else if ($action = 'VPN_TUNNEL_DOWN') then 'Network' else 'Network'"/>
                </Type>
              </xsl:if>
              <xsl:if test="exists($severity_to_severity?($severity))">
                <Severity>
                  <xsl:value-of select="$severity_to_severity?($severity)[1]"/>
                </Severity>
              </xsl:if>
              <xsl:sequence select="mcp:data('protocol', $protocol)"/>
              <xsl:sequence select="mcp:data('rule_id', $rule_id)"/>
              <xsl:sequence select="mcp:data('bytes_sent', $bytes_sent)"/>
              <xsl:sequence select="mcp:data('bytes_received', $bytes_received)"/>
            </Alert>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'admin_logon': records where event_type = ADMIN and action in LOGIN_SUCCESS, LOGIN_FAILED. Writes an
       Authenticate event (TypeId from event_type) from username, action, severity, protocol, rule_id,
       bytes_sent, bytes_received. -->
  <xsl:template match="node()" mode="event_type_admin_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:if test="data[@name='action']/@value = ('LOGIN_SUCCESS', 'LOGIN_FAILED')">
            <Outcome>
              <Success>
                <xsl:value-of select="if (data[@name='action']/@value = 'LOGIN_SUCCESS') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
          <xsl:apply-templates select="." mode="data_severity_to_bytes_received_2"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'admin_logoff': records where event_type = ADMIN and action = LOGOUT. Writes an Authenticate event (TypeId
       from event_type) from username, severity, protocol, rule_id, bytes_sent, bytes_received. -->
  <xsl:template match="node()" mode="event_type_admin_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:apply-templates select="." mode="data_severity_to_bytes_received_2"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'admin_config_change': records where event_type = ADMIN and action in CONFIG_CHANGE, CONFIG_BACKUP. Writes
       an Update event (TypeId from event_type) from action, message, severity, protocol, rule_id, bytes_sent,
       bytes_received. -->
  <xsl:template match="node()" mode="event_type_admin_config_change">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:apply-templates select="." mode="event_detail"/>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from event_type) from severity,
       protocol, action, rule_id, bytes_sent, bytes_received. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='event_type']/@value)) then data[@name='event_type']/@value else 'other'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:if test="normalize-space(data[@name='severity']/@value) or normalize-space(data[@name='protocol']/@value) or normalize-space(data[@name='action']/@value) or normalize-space(data[@name='rule_id']/@value) or normalize-space(data[@name='bytes_sent']/@value) or normalize-space(data[@name='bytes_received']/@value)">
          <Unknown>
            <xsl:sequence select="mcp:data('severity', data[@name='severity']/@value)"/>
            <xsl:sequence select="mcp:data('protocol', data[@name='protocol']/@value)"/>
            <xsl:sequence select="mcp:data('action', data[@name='action']/@value)"/>
            <xsl:sequence select="mcp:data('rule_id', data[@name='rule_id']/@value)"/>
            <xsl:sequence select="mcp:data('bytes_sent', data[@name='bytes_sent']/@value)"/>
            <xsl:sequence select="mcp:data('bytes_received', data[@name='bytes_received']/@value)"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'traffic_permitted', 'traffic_denied', 'system_config_change',
       'system_service', 'system_alert', 'admin_logon', 'admin_logoff', 'admin_config_change', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='timestamp']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='timestamp']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssXXX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'traffic_permitted', 'traffic_denied', 'system_config_change',
       'system_service', 'system_alert', 'admin_logon', 'admin_logoff', 'admin_config_change', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Firewall Logs</Name>
        <Environment>TODO e.g. Prod</Environment>
      </System>
      <Generator>Firewall Logs</Generator>
      <xsl:if test="normalize-space(data[@name='device']/@value)">
        <Device>
          <HostName>
            <xsl:value-of select="data[@name='device']/@value"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="src_port" select="data[@name='src_port']/@value[normalize-space(.)]"/>
      <xsl:variable name="src_ip" select="data[@name='src_ip']/@value[normalize-space(.)]"/>
      <xsl:if test="$src_ip or $src_port">
        <Client>
          <xsl:if test="$src_ip">
            <IPAddress>
              <xsl:value-of select="$src_ip"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$src_port">
            <Port>
              <xsl:value-of select="$src_port"/>
            </Port>
          </xsl:if>
        </Client>
      </xsl:if>
      <xsl:variable name="dst_port" select="data[@name='dst_port']/@value[normalize-space(.)]"/>
      <xsl:variable name="dst_ip" select="data[@name='dst_ip']/@value[normalize-space(.)]"/>
      <xsl:if test="$dst_ip or $dst_port">
        <Server>
          <xsl:if test="$dst_ip">
            <IPAddress>
              <xsl:value-of select="$dst_ip"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$dst_port">
            <Port>
              <xsl:value-of select="$dst_port"/>
            </Port>
          </xsl:if>
        </Server>
      </xsl:if>
      <xsl:if test="normalize-space(data[@name='username']/@value)">
        <User>
          <Id>
            <xsl:value-of select="data[@name='username']/@value"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'traffic_permitted', 'traffic_denied', 'system_config_change', 'system_service',
       'system_alert', 'admin_logon', 'admin_logoff', 'admin_config_change' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(data[@name='event_type']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='event_type']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'traffic_permitted', 'traffic_denied', 'system_config_change',
       'system_service', 'system_alert', 'admin_logon', 'admin_logoff', 'admin_config_change', 'other' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="normalize-space(data[@name='message']/@value)">
      <Description>
        <xsl:value-of select="data[@name='message']/@value"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- source: shared by the rules 'traffic_permitted', 'traffic_denied' -->
  <xsl:template match="node()" mode="source">
    <Source>
      <xsl:if test="normalize-space(data[@name='src_ip']/@value)">
        <Device>
          <IPAddress>
            <xsl:value-of select="data[@name='src_ip']/@value"/>
          </IPAddress>
        </Device>
      </xsl:if>
      <TransportProtocol>
        <xsl:value-of select="($protocol_to_transport_protocol?(normalize-space(data[@name='protocol']/@value)), 'Other')[1]"/>
      </TransportProtocol>
      <xsl:if test="normalize-space(data[@name='src_port']/@value)">
        <Port>
          <xsl:value-of select="data[@name='src_port']/@value"/>
        </Port>
      </xsl:if>
    </Source>
  </xsl:template>
  <!-- destination: shared by the rules 'traffic_permitted', 'traffic_denied' -->
  <xsl:template match="node()" mode="destination">
    <xsl:variable name="dst_port" select="data[@name='dst_port']/@value[normalize-space(.)]"/>
    <xsl:variable name="dst_ip" select="data[@name='dst_ip']/@value[normalize-space(.)]"/>
    <xsl:if test="$dst_ip or $dst_port">
      <Destination>
        <xsl:if test="$dst_ip">
          <Device>
            <IPAddress>
              <xsl:value-of select="$dst_ip"/>
            </IPAddress>
          </Device>
        </xsl:if>
        <xsl:if test="$dst_port">
          <Port>
            <xsl:value-of select="$dst_port"/>
          </Port>
        </xsl:if>
      </Destination>
    </xsl:if>
  </xsl:template>
  <!-- data_severity_to_bytes_received: shared by the rules 'traffic_permitted', 'traffic_denied' -->
  <xsl:template match="node()" mode="data_severity_to_bytes_received">
    <xsl:sequence select="mcp:data('severity', data[@name='severity']/@value)"/>
    <xsl:sequence select="mcp:data('rule_id', data[@name='rule_id']/@value)"/>
    <xsl:sequence select="mcp:data('bytes_sent', data[@name='bytes_sent']/@value)"/>
    <xsl:sequence select="mcp:data('bytes_received', data[@name='bytes_received']/@value)"/>
  </xsl:template>
  <!-- event_detail: shared by the rules 'system_config_change', 'admin_config_change' -->
  <xsl:template match="node()" mode="event_detail">
    <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
    <xsl:variable name="message" select="data[@name='message']/@value[normalize-space(.)]"/>
    <xsl:if test="$message or normalize-space(data[@name='event_type']/@value) or $action or $message or normalize-space(data[@name='severity']/@value) or normalize-space(data[@name='protocol']/@value) or normalize-space(data[@name='rule_id']/@value) or normalize-space(data[@name='bytes_sent']/@value) or normalize-space(data[@name='bytes_received']/@value)">
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:if test="$action or $message or normalize-space(data[@name='severity']/@value) or normalize-space(data[@name='protocol']/@value) or normalize-space(data[@name='rule_id']/@value) or normalize-space(data[@name='bytes_sent']/@value) or normalize-space(data[@name='bytes_received']/@value)">
          <Update>
            <xsl:if test="$action or $message">
              <After>
                <Configuration>
                  <xsl:if test="$action">
                    <Type>
                      <xsl:value-of select="$action"/>
                    </Type>
                  </xsl:if>
                  <xsl:if test="$message">
                    <Description>
                      <xsl:value-of select="$message"/>
                    </Description>
                  </xsl:if>
                </Configuration>
              </After>
            </xsl:if>
            <xsl:apply-templates select="." mode="data_severity_to_bytes_received_2"/>
          </Update>
        </xsl:if>
      </EventDetail>
    </xsl:if>
  </xsl:template>
  <!-- data_severity_to_bytes_received_2: shared by the rules 'system_config_change', 'system_service', 'admin_logon',
       'admin_logoff', 'admin_config_change' -->
  <xsl:template match="node()" mode="data_severity_to_bytes_received_2">
    <xsl:sequence select="mcp:data('severity', data[@name='severity']/@value)"/>
    <xsl:sequence select="mcp:data('protocol', data[@name='protocol']/@value)"/>
    <xsl:sequence select="mcp:data('rule_id', data[@name='rule_id']/@value)"/>
    <xsl:sequence select="mcp:data('bytes_sent', data[@name='bytes_sent']/@value)"/>
    <xsl:sequence select="mcp:data('bytes_received', data[@name='bytes_received']/@value)"/>
  </xsl:template>
  <!-- user: shared by the rules 'admin_logon', 'admin_logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="normalize-space(data[@name='username']/@value)">
      <User>
        <Id>
          <xsl:value-of select="data[@name='username']/@value"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
