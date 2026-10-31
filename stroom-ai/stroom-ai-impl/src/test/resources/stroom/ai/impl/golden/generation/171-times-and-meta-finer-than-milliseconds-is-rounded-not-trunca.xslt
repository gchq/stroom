<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs">
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
            <xsl:value-of select="stroom:format-date(*[@key='ts'][1], 'yyyy-MM-dd''T''HH:mm:ss.SSSSSSX')"/>
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
