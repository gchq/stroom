<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <xsl:template match="records">
    <Events Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_view"/>
    <xsl:for-each select="data[@name][not(@name = ('action', 'host', 'time', 'user'))][normalize-space(string(@value))]">
      <!-- A field no rule reads: logged once a stream (ignored_fields: left out on purpose) -->
      <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
      <xsl:if test="not(string(stroom:get($mcp_unread)))">
        <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
        <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  <!-- Rule 'view': every record. Writes a View event (TypeId FileRead) from action. -->
  <xsl:template match="node()" mode="event_type_view">
    <Event>
      <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="(data[@name='time']/@value)[1]"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
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
      <EventDetail>
        <TypeId>FileRead</TypeId>
        <xsl:if test="data[@name='action']/@value[normalize-space(.)]">
          <View>
            <File>
              <Path>
                <xsl:value-of select="(data[@name='action']/@value)[1]"/>
              </Path>
            </File>
          </View>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
