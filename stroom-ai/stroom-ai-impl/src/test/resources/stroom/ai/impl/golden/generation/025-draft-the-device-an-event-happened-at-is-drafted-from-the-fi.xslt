<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:apply-templates select="." mode="event_type_other"/>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="*[@key][not(@key = ('ip', 'mac', 'switch', 'time', 'user'))][normalize-space(string(.))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'other': every record. Writes an Unknown event (TypeId Other) from time. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:variable name="time" select="*[@key='time'][normalize-space(.)]"/>
      <xsl:if test="$time">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date($time[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>S</Name>
          <Environment>Prod</Environment>
        </System>
        <Generator>S</Generator>
        <xsl:variable name="mac" select="*[@key='mac'][normalize-space(.)]"/>
        <xsl:variable name="switch" select="*[@key='switch'][normalize-space(.)]"/>
        <xsl:if test="$switch or $mac">
          <Device>
            <xsl:if test="$switch">
              <HostName>
                <xsl:value-of select="$switch[1]"/>
              </HostName>
            </xsl:if>
            <xsl:if test="$mac">
              <MACAddress>
                <xsl:value-of select="$mac[1]"/>
              </MACAddress>
            </xsl:if>
          </Device>
        </xsl:if>
        <xsl:if test="*[@key='ip'][normalize-space(.)]">
          <Client>
            <IPAddress>
              <xsl:value-of select="(*[@key='ip'])[1]"/>
            </IPAddress>
          </Client>
        </xsl:if>
        <xsl:if test="*[@key='user'][normalize-space(.)]">
          <User>
            <Id>
              <xsl:value-of select="(*[@key='user'])[1]"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>Other</TypeId>
        <Unknown>
          <Data Name="time" Value="{$time[1]}"/>
        </Unknown>
      </EventDetail>
    </Event>
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
