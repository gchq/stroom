<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:variable name="severity_to_severity" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'WARNING'" select="'Minor'"/>
      <xsl:map-entry key="'ERROR'" select="'Major'"/>
      <xsl:map-entry key="'INFO'" select="'Info'"/>
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
      <xsl:when test="$event_type = 'SYSTEM' and $action = 'START'">
        <xsl:apply-templates select="." mode="event_type_system_service"/>
      </xsl:when>
      <xsl:when test="$event_type = 'SYSTEM' and $action = ('HIGH_CPU', 'VPN_TUNNEL_DOWN', 'VPN_TUNNEL_UP')">
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
      <xsl:for-each select="data[@name][not(@name = ('action', 'device', 'event_type', 'message', 'severity', 'timestamp'))][normalize-space(string(@value))]">
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
  <!-- Rule 'system_service': records where event_type = SYSTEM and action = START. Writes a Process event (TypeId
       from event_type) from action, message, severity. -->
  <xsl:template match="node()" mode="event_type_system_service">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Process>
          <xsl:if test="(data[@name='action']/@value)[1] = ('START')">
            <Action>
              <xsl:value-of select="'Startup'"/>
            </Action>
          </xsl:if>
          <Type>Service</Type>
          <xsl:if test="data[@name='message']/@value[normalize-space(.)]">
            <Command>
              <xsl:value-of select="(data[@name='message']/@value)[1]"/>
            </Command>
          </xsl:if>
          <xsl:sequence select="mcp:data('severity', data[@name='severity']/@value)"/>
        </Process>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'system_alert': records where event_type = SYSTEM and action in HIGH_CPU, VPN_TUNNEL_DOWN, VPN_TUNNEL_UP.
       Writes an Alert event (TypeId from event_type) from action, severity. -->
  <xsl:template match="node()" mode="event_type_system_alert">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="severity" select="data[@name='severity']/@value[normalize-space(.)]"/>
      <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
      <xsl:if test="data[@name='message']/@value[normalize-space(.)] or data[@name='event_type']/@value[normalize-space(.)] or $action[1] = ('HIGH_CPU', 'VPN_TUNNEL_DOWN', 'VPN_TUNNEL_UP') or exists($severity_to_severity?($severity[1]))">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:apply-templates select="." mode="description"/>
          <xsl:if test="$action[1] = ('HIGH_CPU', 'VPN_TUNNEL_DOWN', 'VPN_TUNNEL_UP') or exists($severity_to_severity?($severity[1]))">
            <Alert>
              <xsl:if test="$action[1] = ('HIGH_CPU', 'VPN_TUNNEL_DOWN', 'VPN_TUNNEL_UP')">
                <Type>
                  <xsl:value-of select="if ($action[1] = 'HIGH_CPU') then 'Other' else if ($action[1] = 'VPN_TUNNEL_DOWN') then 'Network' else 'Network'"/>
                </Type>
              </xsl:if>
              <xsl:if test="exists($severity_to_severity?($severity[1]))">
                <Severity>
                  <xsl:value-of select="$severity_to_severity?($severity[1])[1]"/>
                </Severity>
              </xsl:if>
            </Alert>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from event_type) from severity,
       action. -->
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
          <xsl:sequence select="mcp:data('action', data[@name='action']/@value)"/>
        </Unknown>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'system_service', 'system_alert', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='timestamp']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((data[@name='timestamp']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'system_service', 'system_alert', 'other' -->
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
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'system_service', 'system_alert' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="data[@name='event_type']/@value[normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="(data[@name='event_type']/@value)[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'system_service', 'system_alert', 'other' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="data[@name='message']/@value[normalize-space(.)]">
      <Description>
        <xsl:value-of select="(data[@name='message']/@value)[1]"/>
      </Description>
    </xsl:if>
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
