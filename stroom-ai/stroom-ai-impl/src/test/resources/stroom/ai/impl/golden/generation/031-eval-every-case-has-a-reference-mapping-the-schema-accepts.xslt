<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_alert"/>
  </xsl:template>
  <!-- Rule 'alert': every record. Writes an Alert event (TypeId IDS-Alert) from sig, sev, src, dst, dport. -->
  <xsl:template match="node()" mode="event_type_alert">
    <Event>
      <xsl:if test="normalize-space(*[@key='ts'])">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(*[@key='ts'][1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>IDS</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>ids-sensor</Generator>
        <xsl:if test="normalize-space(*[@key='sensor'])">
          <Device>
            <HostName>
              <xsl:value-of select="*[@key='sensor']"/>
            </HostName>
          </Device>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>IDS-Alert</TypeId>
        <xsl:variable name="sig" select="*[@key='sig'][normalize-space(.)]"/>
        <xsl:if test="$sig">
          <Description>
            <xsl:value-of select="$sig"/>
          </Description>
        </xsl:if>
        <Alert>
          <Type>IDS</Type>
          <Severity>
            <xsl:variable name="sev" select="*[@key='sev'][normalize-space(.)]"/>
            <xsl:value-of select="if ($sev = '1') then 'Critical' else if ($sev = '2') then 'Major' else if ($sev = '3') then 'Minor' else 'Info'"/>
          </Severity>
          <xsl:if test="$sig">
            <Description>
              <xsl:value-of select="$sig"/>
            </Description>
          </xsl:if>
          <xsl:sequence select="mcp:data('source_ip', *[@key='src'])"/>
          <xsl:sequence select="mcp:data('destination_ip', *[@key='dst'])"/>
          <xsl:sequence select="mcp:data('destination_port', *[@key='dport'])"/>
        </Alert>
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
