<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:apply-templates select="." mode="event_type_alert"/>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="*[@key][not(@key = ('dport', 'dst', 'sensor', 'sev', 'sig', 'src', 'ts'))][normalize-space(string(.))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'alert': every record. Writes an Alert event (TypeId IDS-Alert) from sig, sev, src, dst, dport. -->
  <xsl:template match="node()" mode="event_type_alert">
    <Event>
      <xsl:if test="*[@key='ts'][normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date((*[@key='ts'])[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>IDS</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>ids-sensor</Generator>
        <xsl:if test="*[@key='sensor'][normalize-space(.)]">
          <Device>
            <HostName>
              <xsl:value-of select="(*[@key='sensor'])[1]"/>
            </HostName>
          </Device>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>IDS-Alert</TypeId>
        <xsl:variable name="sig" select="*[@key='sig'][normalize-space(.)]"/>
        <xsl:if test="$sig">
          <Description>
            <xsl:value-of select="$sig[1]"/>
          </Description>
        </xsl:if>
        <Alert>
          <Type>IDS</Type>
          <Severity>
            <xsl:variable name="sev" select="*[@key='sev'][normalize-space(.)]"/>
            <xsl:value-of select="if ($sev[1] = '1') then 'Critical' else if ($sev[1] = '2') then 'Major' else if ($sev[1] = '3') then 'Minor' else 'Info'"/>
          </Severity>
          <xsl:if test="$sig">
            <Description>
              <xsl:value-of select="$sig[1]"/>
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
      <Data Name="{$name}" Value="{$value[1]}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
