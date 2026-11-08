<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:variable name="severity_to_severity" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'WARNING'" select="'Minor'"/>
      <xsl:map-entry key="'DEBUG'" select="'Info'"/>
      <xsl:map-entry key="'INFO'" select="'Info'"/>
      <xsl:map-entry key="'NOTICE'" select="'Info'"/>
      <xsl:map-entry key="'WARN'" select="'Minor'"/>
      <xsl:map-entry key="'ERROR'" select="'Major'"/>
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
    </xsl:map>
  </xsl:variable>
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="records">
    <Events Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <!-- Fields of the record no rule reads are logged (mcp_unread_fields) -->
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
    <xsl:variable name="event_type" select="data[@name='event_type']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$event_type = 'TRAFFIC' and $action = 'ALLOW'">
        <xsl:apply-templates select="." mode="event_type_traffic_permitted"/>
      </xsl:when>
      <xsl:when test="$event_type = 'TRAFFIC' and $action = 'DENY'">
        <xsl:apply-templates select="." mode="event_type_traffic_denied"/>
      </xsl:when>
      <xsl:when test="$event_type = 'ADMIN' and $action = 'LOGIN_SUCCESS'">
        <xsl:apply-templates select="." mode="event_type_admin_logon"/>
      </xsl:when>
      <xsl:when test="$event_type = 'SYSTEM' and $action = 'HIGH_CPU'">
        <xsl:apply-templates select="." mode="event_type_system_alert"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
        <xsl:call-template name="mcp_log_kind">
          <xsl:with-param name="group" select="concat('other|', string-join((string(($event_type)[1]), string(($action)[1])), '|'))"/>
          <xsl:with-param name="line" select="concat('Caught by rule ''other'': record ', $record-no, ' (', string-join((concat('event_type=', string(($event_type)[1])), concat('action=', string(($action)[1]))), ' | '), ')')"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('action', 'bytes_received', 'bytes_sent', 'device', 'dst_ip', 'dst_port', 'event_type', 'message', 'protocol', 'rule_id', 'severity', 'src_ip', 'src_port', 'timestamp', 'username'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- mcp_log_kind: a record a rule keeps as Unknown on purpose, or a catch-all rule takes, logged once a stream for
       each kind (the rule and the values the rules test) -->
  <xsl:template name="mcp_log_kind">
    <xsl:param name="group"/>
    <xsl:param name="line"/>
    <xsl:if test="not(string(stroom:get(concat('mcp-kind:', $group))))">
      <xsl:value-of select="stroom:put(concat('mcp-kind:', $group), 'y')"/>
      <xsl:sequence select="stroom:log('INFO', $line)"/>
    </xsl:if>
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
  <!-- Rule 'admin_logon': records where event_type = ADMIN and action = LOGIN_SUCCESS. Writes an Authenticate event
       (TypeId from event_type) from username, action, severity, protocol, rule_id, bytes_sent, bytes_received. -->
  <xsl:template match="node()" mode="event_type_admin_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="data[@name='username']/@value[normalize-space(.)]">
            <User>
              <Id>
                <xsl:value-of select="(data[@name='username']/@value)[1]"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="(data[@name='action']/@value)[1] = ('LOGIN_SUCCESS')">
            <Outcome>
              <Success>
                <xsl:value-of select="'true'"/>
              </Success>
            </Outcome>
          </xsl:if>
          <xsl:sequence select="mcp:data('severity', data[@name='severity']/@value)"/>
          <xsl:apply-templates select="." mode="data_protocol_to_bytes_received"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'system_alert': records where event_type = SYSTEM and action = HIGH_CPU. Writes an Alert event (TypeId
       from event_type) from action, severity, protocol, rule_id, bytes_sent, bytes_received. -->
  <xsl:template match="node()" mode="event_type_system_alert">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="severity" select="data[@name='severity']/@value[normalize-space(.)]"/>
      <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
      <xsl:if test="data[@name='message']/@value[normalize-space(.)] or data[@name='event_type']/@value[normalize-space(.)] or $action[1] = ('HIGH_CPU') or exists($severity_to_severity?($severity[1])) or data[@name='protocol']/@value[normalize-space(.)] or data[@name='rule_id']/@value[normalize-space(.)] or data[@name='bytes_sent']/@value[normalize-space(.)] or data[@name='bytes_received']/@value[normalize-space(.)]">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:apply-templates select="." mode="description"/>
          <xsl:if test="$action[1] = ('HIGH_CPU') or exists($severity_to_severity?($severity[1])) or data[@name='protocol']/@value[normalize-space(.)] or data[@name='rule_id']/@value[normalize-space(.)] or data[@name='bytes_sent']/@value[normalize-space(.)] or data[@name='bytes_received']/@value[normalize-space(.)]">
            <Alert>
              <xsl:if test="$action[1] = ('HIGH_CPU')">
                <Type>
                  <xsl:value-of select="'Other'"/>
                </Type>
              </xsl:if>
              <xsl:if test="exists($severity_to_severity?($severity[1]))">
                <Severity>
                  <xsl:value-of select="$severity_to_severity?($severity[1])[1]"/>
                </Severity>
              </xsl:if>
              <xsl:apply-templates select="." mode="data_protocol_to_bytes_received"/>
            </Alert>
          </xsl:if>
        </EventDetail>
      </xsl:if>
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
          <xsl:value-of select="if (data[@name='event_type']/@value[normalize-space(.)]) then (data[@name='event_type']/@value)[1] else 'other'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Unknown>
          <xsl:sequence select="mcp:data('severity', data[@name='severity']/@value)"/>
          <xsl:sequence select="mcp:data('protocol', data[@name='protocol']/@value)"/>
          <xsl:sequence select="mcp:data('action', data[@name='action']/@value)"/>
          <xsl:sequence select="mcp:data('rule_id', data[@name='rule_id']/@value)"/>
          <xsl:sequence select="mcp:data('bytes_sent', data[@name='bytes_sent']/@value)"/>
          <xsl:sequence select="mcp:data('bytes_received', data[@name='bytes_received']/@value)"/>
        </Unknown>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'traffic_permitted', 'traffic_denied', 'admin_logon', 'system_alert', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='timestamp']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((data[@name='timestamp']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssXXX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'traffic_permitted', 'traffic_denied', 'admin_logon', 'system_alert', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>FW</Name>
        <Environment>Prod</Environment>
      </System>
      <Generator>FW</Generator>
      <xsl:if test="data[@name='device']/@value[normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(data[@name='device']/@value)[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="src_port" select="data[@name='src_port']/@value[normalize-space(.)]"/>
      <xsl:variable name="src_ip" select="data[@name='src_ip']/@value[normalize-space(.)]"/>
      <xsl:if test="$src_ip or $src_port">
        <Client>
          <xsl:if test="$src_ip">
            <IPAddress>
              <xsl:value-of select="$src_ip[1]"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$src_port">
            <Port>
              <xsl:value-of select="$src_port[1]"/>
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
              <xsl:value-of select="$dst_ip[1]"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$dst_port">
            <Port>
              <xsl:value-of select="$dst_port[1]"/>
            </Port>
          </xsl:if>
        </Server>
      </xsl:if>
      <xsl:if test="data[@name='username']/@value[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(data[@name='username']/@value)[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'traffic_permitted', 'traffic_denied', 'admin_logon', 'system_alert' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="data[@name='event_type']/@value[normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="(data[@name='event_type']/@value)[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'traffic_permitted', 'traffic_denied', 'admin_logon', 'system_alert', 'other' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="data[@name='message']/@value[normalize-space(.)]">
      <Description>
        <xsl:value-of select="(data[@name='message']/@value)[1]"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- source: shared by the rules 'traffic_permitted', 'traffic_denied' -->
  <xsl:template match="node()" mode="source">
    <Source>
      <xsl:if test="data[@name='src_ip']/@value[normalize-space(.)]">
        <Device>
          <IPAddress>
            <xsl:value-of select="(data[@name='src_ip']/@value)[1]"/>
          </IPAddress>
        </Device>
      </xsl:if>
      <TransportProtocol>
        <xsl:value-of select="($protocol_to_transport_protocol?((data[@name='protocol']/@value)[1]), 'Other')[1]"/>
      </TransportProtocol>
      <xsl:if test="data[@name='src_port']/@value[normalize-space(.)]">
        <Port>
          <xsl:value-of select="(data[@name='src_port']/@value)[1]"/>
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
              <xsl:value-of select="$dst_ip[1]"/>
            </IPAddress>
          </Device>
        </xsl:if>
        <xsl:if test="$dst_port">
          <Port>
            <xsl:value-of select="$dst_port[1]"/>
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
  <!-- data_protocol_to_bytes_received: shared by the rules 'admin_logon', 'system_alert' -->
  <xsl:template match="node()" mode="data_protocol_to_bytes_received">
    <xsl:sequence select="mcp:data('protocol', data[@name='protocol']/@value)"/>
    <xsl:sequence select="mcp:data('rule_id', data[@name='rule_id']/@value)"/>
    <xsl:sequence select="mcp:data('bytes_sent', data[@name='bytes_sent']/@value)"/>
    <xsl:sequence select="mcp:data('bytes_received', data[@name='bytes_received']/@value)"/>
  </xsl:template>
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value[1]}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
