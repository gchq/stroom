<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <xsl:function name="mcp:time-ms" as="xs:string?">
    <xsl:param name="value" as="xs:string?"/>
    <xsl:param name="pattern" as="xs:string"/>
    <xsl:param name="zone" as="xs:string"/>
    <xsl:variable name="time" select="if ($zone) then stroom:format-date($value, $pattern, $zone) else stroom:format-date($value, $pattern)"/>
    <xsl:sequence select="if (matches($value, '^.*\d[.,]\d{3}(\d)\d*(\D.*)?$') and replace($value, '^.*\d[.,]\d{3}(\d)\d*(\D.*)?$', '$1') ge '5') then format-dateTime(xs:dateTime($time) + xs:dayTimeDuration('PT0.001S'), '[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f001]Z') else $time"/>
  </xsl:function>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_a"/>
  </xsl:template>
  <!-- Rule 'a': every record. Writes an Authenticate event (TypeId x) from u. -->
  <xsl:template match="node()" mode="event_type_a">
    <Event>
      <xsl:if test="normalize-space(*[@key='ts'])">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="mcp:time-ms(string(*[@key='ts'][1]), 'yyyy-MM-dd''T''HH:mm:ss.SSSSSSX', '')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>X</Name>
          <Environment>T</Environment>
        </System>
        <Generator>g</Generator>
        <Device>
          <HostName>h</HostName>
        </Device>
      </EventSource>
      <EventDetail>
        <TypeId>x</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="normalize-space(*[@key='u'])">
            <User>
              <Id>
                <xsl:value-of select="*[@key='u']"/>
              </Id>
            </User>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
