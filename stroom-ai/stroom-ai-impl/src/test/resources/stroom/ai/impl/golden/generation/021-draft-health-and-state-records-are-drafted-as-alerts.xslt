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
  <xsl:template match="records">
    <Events Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="data[@name='event_type']/@value = 'SYSTEM' and data[@name='action']/@value = 'START'">
        <xsl:apply-templates select="." mode="event_type_system_service"/>
      </xsl:when>
      <xsl:when test="data[@name='event_type']/@value = 'SYSTEM' and data[@name='action']/@value = ('HIGH_CPU', 'VPN_TUNNEL_DOWN', 'VPN_TUNNEL_UP')">
        <xsl:apply-templates select="." mode="event_type_system_alert"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
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
      <xsl:if test="normalize-space(data[@name='message']/@value) or normalize-space(data[@name='event_type']/@value) or $action = ('HIGH_CPU', 'VPN_TUNNEL_DOWN', 'VPN_TUNNEL_UP') or exists($severity_to_severity?($severity))">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:apply-templates select="." mode="description"/>
          <xsl:if test="$action = ('HIGH_CPU', 'VPN_TUNNEL_DOWN', 'VPN_TUNNEL_UP') or exists($severity_to_severity?($severity))">
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
          <xsl:value-of select="if (normalize-space(data[@name='event_type']/@value)) then data[@name='event_type']/@value else 'other'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:if test="normalize-space(data[@name='severity']/@value) or normalize-space(data[@name='action']/@value)">
          <Unknown>
            <xsl:sequence select="mcp:data('severity', data[@name='severity']/@value)"/>
            <xsl:sequence select="mcp:data('action', data[@name='action']/@value)"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'system_service', 'system_alert', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='timestamp']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='timestamp']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
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
      <xsl:if test="normalize-space(data[@name='device']/@value)">
        <Device>
          <HostName>
            <xsl:value-of select="data[@name='device']/@value"/>
          </HostName>
        </Device>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'system_service', 'system_alert' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(data[@name='event_type']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='event_type']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'system_service', 'system_alert', 'other' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="normalize-space(data[@name='message']/@value)">
      <Description>
        <xsl:value-of select="data[@name='message']/@value"/>
      </Description>
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
