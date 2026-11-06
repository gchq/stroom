<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:apply-templates select="." mode="event_type_printed"/>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('document', 'location', 'pages', 'printer', 'time', 'user'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'printed': every record. Writes a Print event (TypeId print-job) from document, pages, printer, location. -->
  <xsl:template match="node()" mode="event_type_printed">
    <Event>
      <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date((data[@name='time']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="printer" select="data[@name='printer']/@value[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>Print Services</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>print-server</Generator>
        <xsl:if test="$printer">
          <Device>
            <HostName>
              <xsl:value-of select="$printer[1]"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="data[@name='user']/@value[normalize-space(.)]">
          <User>
            <Id>
              <xsl:value-of select="replace((data[@name='user']/@value)[1], '&quot;&quot;', '&quot;')"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>print-job</TypeId>
        <Print>
          <Action>FinishPrint</Action>
          <xsl:variable name="pages" select="data[@name='pages']/@value[normalize-space(.)]"/>
          <xsl:variable name="document" select="data[@name='document']/@value[normalize-space(.)]"/>
          <xsl:if test="$document or $pages">
            <PrintJob>
              <xsl:if test="$document">
                <Document>
                  <Title>
                    <xsl:value-of select="replace($document[1], '&quot;&quot;', '&quot;')"/>
                  </Title>
                </Document>
              </xsl:if>
              <xsl:if test="$pages">
                <Pages>
                  <xsl:value-of select="$pages[1]"/>
                </Pages>
              </xsl:if>
            </PrintJob>
          </xsl:if>
          <xsl:if test="$printer">
            <Printer>
              <Name>
                <xsl:value-of select="$printer[1]"/>
              </Name>
            </Printer>
          </xsl:if>
          <xsl:if test="data[@name='location']/@value[normalize-space(.)]">
            <Data Name="printer_location" Value="{replace((data[@name='location']/@value)[1], '&quot;&quot;', '&quot;')}"/>
          </xsl:if>
        </Print>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
