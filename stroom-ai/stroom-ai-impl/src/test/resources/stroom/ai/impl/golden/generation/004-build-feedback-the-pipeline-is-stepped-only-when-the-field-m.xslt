<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_logon"/>
  </xsl:template>
  <!-- Rule 'logon': every record. Writes an Authenticate event (TypeId Logon) from user. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <!--stroom-mcp rule: logon-->
      <xsl:if test="normalize-space(*[@key='ts'])">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="*[@key='ts']"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>Acme</Name>
          <Environment>Test</Environment>
        </System>
        <Generator>acme</Generator>
        <xsl:if test="normalize-space(*[@key='host'])">
          <Device>
            <HostName>
              <xsl:value-of select="*[@key='host']"/>
            </HostName>
          </Device>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="normalize-space(*[@key='user'])">
            <User>
              <Id>
                <xsl:value-of select="*[@key='user']"/>
              </Id>
            </User>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
