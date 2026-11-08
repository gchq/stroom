<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="records">
    <Events Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <!-- Fields of the record no rule reads are logged (mcp_unread_fields) -->
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:choose>
      <xsl:when test="data[@name='action']/@value = 'connect'">
        <xsl:apply-templates select="." mode="event_type_traffic"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_alert"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('action', 'host', 'ip', 'result', 'time', 'user'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'traffic': records where action = connect. Writes a Network/Permit event (TypeId Traffic) from ip, result. -->
  <xsl:template match="node()" mode="event_type_traffic">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Traffic</TypeId>
        <xsl:variable name="ip" select="data[@name='ip']/@value[normalize-space(.)]"/>
        <xsl:if test="$ip or data[@name='result']/@value[normalize-space(.)]">
          <Network>
            <Permit>
              <xsl:if test="$ip">
                <Source>
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$ip[1]"/>
                    </IPAddress>
                  </Device>
                </Source>
              </xsl:if>
              <xsl:sequence select="mcp:data('result', data[@name='result']/@value)"/>
            </Permit>
          </Network>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'alert': records no other rule matches. Writes an Alert event (TypeId Alert) from result. -->
  <xsl:template match="node()" mode="event_type_alert">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Alert</TypeId>
        <Alert>
          <Type>Other</Type>
          <xsl:sequence select="mcp:data('result', data[@name='result']/@value)"/>
        </Alert>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'traffic', 'alert' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="(data[@name='time']/@value)[1]"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'traffic', 'alert' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Acme VPN</Name>
        <Environment>Dev</Environment>
      </System>
      <Generator>vpnd</Generator>
      <Device>
        <HostName>
          <xsl:value-of select="if (data[@name='host']/@value[normalize-space(.)]) then (data[@name='host']/@value)[1] else 'unknown'"/>
        </HostName>
      </Device>
      <xsl:if test="data[@name='user']/@value[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(data[@name='user']/@value)[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
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
