<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_other"/>
  </xsl:template>
  <!-- Rule 'other': every record. Writes an Unknown event (TypeId Other) from granted. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:if test="(when)[normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(when[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>S</Name>
          <Environment>Prod</Environment>
        </System>
        <Generator>S</Generator>
        <xsl:if test="(door)[normalize-space(.)]">
          <Device>
            <Name>
              <xsl:value-of select="door"/>
            </Name>
          </Device>
        </xsl:if>
        <xsl:if test="(holder)[normalize-space(.)]">
          <User>
            <Id>
              <xsl:value-of select="holder"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>Other</TypeId>
        <xsl:if test="(granted)[normalize-space(.)]">
          <Unknown>
            <Data Name="granted" Value="{granted}"/>
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
