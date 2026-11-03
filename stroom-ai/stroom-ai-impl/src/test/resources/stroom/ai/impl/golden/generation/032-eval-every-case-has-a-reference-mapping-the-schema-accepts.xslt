<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_process_start"/>
  </xsl:template>
  <!-- Rule 'process start': every record. Writes a Process event (TypeId Process-Start) from process.executable,
       process.args, process.pid. -->
  <xsl:template match="node()" mode="event_type_process_start">
    <Event>
      <xsl:if test="*[@key='@timestamp'][normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date((*[@key='@timestamp'])[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>EDR</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>edr-agent</Generator>
        <xsl:variable name="host_ip" select="*[@key='host']/*[@key='ip'][normalize-space(.)]"/>
        <xsl:variable name="host_name" select="*[@key='host']/*[@key='name'][normalize-space(.)]"/>
        <xsl:if test="$host_name or $host_ip">
          <Device>
            <xsl:if test="$host_name">
              <HostName>
                <xsl:value-of select="$host_name[1]"/>
              </HostName>
            </xsl:if>
            <xsl:if test="$host_ip">
              <IPAddress>
                <xsl:value-of select="$host_ip[1]"/>
              </IPAddress>
            </xsl:if>
          </Device>
        </xsl:if>
        <xsl:if test="*[@key='user']/*[@key='name'][normalize-space(.)]">
          <User>
            <Id>
              <xsl:value-of select="(*[@key='user']/*[@key='name'])[1]"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>Process-Start</TypeId>
        <Process>
          <Action>Execute</Action>
          <Type>Application</Type>
          <xsl:if test="*[@key='process']/*[@key='executable'][normalize-space(.)]">
            <Command>
              <xsl:value-of select="(*[@key='process']/*[@key='executable'])[1]"/>
            </Command>
          </xsl:if>
          <xsl:if test="*[@key='process']/*[@key='args'][normalize-space(.)]">
            <Arguments>
              <xsl:value-of select="(*[@key='process']/*[@key='args'])[1]"/>
            </Arguments>
          </xsl:if>
          <xsl:if test="*[@key='process']/*[@key='pid'][normalize-space(.)]">
            <ProcessId>
              <xsl:value-of select="(*[@key='process']/*[@key='pid'])[1]"/>
            </ProcessId>
          </xsl:if>
        </Process>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
