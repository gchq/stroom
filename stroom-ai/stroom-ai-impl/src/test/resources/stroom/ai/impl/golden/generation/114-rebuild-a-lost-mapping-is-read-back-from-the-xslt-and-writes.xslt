<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_logon"/>
  </xsl:template>
  <!-- Rule 'logon': every record. Writes an Authenticate event (TypeId Logon) from user, sid, result. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:if test="normalize-space(data[@name='time']/@value)">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="data[@name='time']/@value"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="user" select="data[@name='user']/@value[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>Acme VPN</Name>
          <Environment>Dev</Environment>
        </System>
        <Generator>vpnd</Generator>
        <Device>
          <HostName>
            <xsl:value-of select="if (normalize-space(data[@name='host']/@value)) then data[@name='host']/@value else 'unknown'"/>
          </HostName>
        </Device>
        <xsl:if test="$user">
          <User>
            <Id>
              <xsl:value-of select="$user"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="$user">
            <User>
              <Id>
                <xsl:value-of select="$user"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="data[@name='result']/@value = ('ok')">
            <Outcome>
              <Success>
                <xsl:value-of select="'true'"/>
              </Success>
            </Outcome>
          </xsl:if>
          <xsl:sequence select="mcp:data('session', data[@name='sid']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
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
