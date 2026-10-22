<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_any"/>
  </xsl:template>
  <!-- Rule 'any': every record. Writes an Unknown event (TypeId x) from host. -->
  <xsl:template match="node()" mode="event_type_any">
    <Event>
      <xsl:if test="normalize-space(*[@key='time'])">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="*[@key='time']"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="host" select="*[@key='host'][normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>A</Name>
          <Environment>D</Environment>
        </System>
        <Generator>g</Generator>
        <xsl:if test="$host">
          <Device>
            <HostName>
              <xsl:value-of select="$host"/>
            </HostName>
          </Device>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>x</TypeId>
        <xsl:if test="$host">
          <Unknown>
            <Data Name="h" Value="{$host}"/>
          </Unknown>
        </xsl:if>
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
