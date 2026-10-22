<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <xsl:function name="mcp:parse-xml" as="node()?">
    <xsl:param name="text" as="item()*"/>
    <xsl:variable name="xml" select="string(($text)[1])"/>
    <xsl:if test="starts-with(normalize-space($xml), '&lt;')">
      <xsl:try>
        <xsl:sequence select="parse-xml($xml)"/>
        <xsl:catch/>
      </xsl:try>
    </xsl:if>
  </xsl:function>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_login"/>
  </xsl:template>
  <!-- Rule 'login': every record. Writes an Authenticate event (TypeId x) from payload//User. -->
  <xsl:template match="node()" mode="event_type_login">
    <Event>
      <xsl:if test="normalize-space(*[@key='time'])">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(*[@key='time'][1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="payload_user" select="mcp:parse-xml((*[@key='payload'])[1])//*:User[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>GW</Name>
          <Environment>T</Environment>
        </System>
        <Generator>gw</Generator>
        <Device>
          <HostName>gw</HostName>
        </Device>
        <xsl:if test="$payload_user">
          <User>
            <Id>
              <xsl:value-of select="$payload_user"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>x</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="$payload_user">
            <User>
              <Id>
                <xsl:value-of select="$payload_user"/>
              </Id>
            </User>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
