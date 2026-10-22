<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_printed"/>
  </xsl:template>
  <!-- Rule 'printed': every record. Writes a Print event (TypeId print-job) from document, pages, printer, location. -->
  <xsl:template match="node()" mode="event_type_printed">
    <Event>
      <xsl:if test="normalize-space(data[@name='time']/@value)">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
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
              <xsl:value-of select="$printer"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="normalize-space(data[@name='user']/@value)">
          <User>
            <Id>
              <xsl:value-of select="replace(data[@name='user']/@value[1], '&quot;&quot;', '&quot;')"/>
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
                  <xsl:value-of select="$pages"/>
                </Pages>
              </xsl:if>
            </PrintJob>
          </xsl:if>
          <xsl:if test="$printer">
            <Printer>
              <Name>
                <xsl:value-of select="$printer"/>
              </Name>
            </Printer>
          </xsl:if>
          <xsl:if test="normalize-space(data[@name='location']/@value)">
            <Data Name="printer_location" Value="{replace(data[@name='location']/@value[1], '&quot;&quot;', '&quot;')}"/>
          </xsl:if>
        </Print>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
