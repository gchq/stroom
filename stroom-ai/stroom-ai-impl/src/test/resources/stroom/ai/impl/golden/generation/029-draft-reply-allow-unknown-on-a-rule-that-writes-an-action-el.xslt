<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="event_type" select="data[@name='event_type']/@value[normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$event_type = 'SYSTEM'">
        <xsl:apply-templates select="." mode="event_type_system_alert"/>
        <xsl:sequence select="stroom:log('INFO', concat('Kept as Unknown by rule ''system_alert'': record ', $record-no, ' (', string-join((concat('event_type=', string(($event_type)[1]))), ' | '), ')'))"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
        <xsl:sequence select="stroom:log('INFO', concat('Kept as Unknown by rule ''other'': record ', $record-no, ' (', string-join((concat('event_type=', string(($event_type)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'system_alert': records where event_type = SYSTEM. Writes an Alert event (TypeId from action). Unknown on
       purpose: System events. -->
  <xsl:template match="node()" mode="event_type_system_alert">
    <!-- system_alert: EventDetail/Unknown on purpose: System events -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='action']/@value)) then data[@name='action']/@value else 'system_alert'"/>
        </TypeId>
        <Alert>
          <Type>Other</Type>
        </Alert>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from action) from action. Unknown
       on purpose: anything else. -->
  <xsl:template match="node()" mode="event_type_other">
    <!-- other: EventDetail/Unknown on purpose: anything else -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action else 'other'"/>
        </TypeId>
        <xsl:if test="$action">
          <Unknown>
            <Data Name="action" Value="{$action}"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'system_alert', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='timestamp']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="data[@name='timestamp']/@value"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'system_alert', 'other' -->
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
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
