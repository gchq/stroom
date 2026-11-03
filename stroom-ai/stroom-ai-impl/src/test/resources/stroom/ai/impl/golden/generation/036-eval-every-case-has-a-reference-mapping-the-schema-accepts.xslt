<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_sudo_command"/>
  </xsl:template>
  <!-- Rule 'sudo command': every record. Writes a Process event (TypeId Sudo) from command, args, pwd. -->
  <xsl:template match="node()" mode="event_type_sudo_command">
    <Event>
      <xsl:variable name="event_time_time_created" select="concat('2026 ', replace(data[@name='time']/@value, '  ', ' '))[normalize-space(.)]"/>
      <xsl:if test="exists($event_time_time_created)">
        <EventTime>
          <TimeCreated>
            <xsl:if test="count($event_time_time_created) gt 1">
              <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($event_time_time_created), ') for ', 'EventTime/TimeCreated, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
            </xsl:if>
            <xsl:value-of select="stroom:format-date($event_time_time_created[1], 'yyyy MMM d HH:mm:ss', 'UTC')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>Web</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>sudo</Generator>
        <xsl:if test="data[@name='host']/@value[normalize-space(.)]">
          <Device>
            <HostName>
              <xsl:value-of select="(data[@name='host']/@value)[1]"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="data[@name='user']/@value[normalize-space(.)]">
          <User>
            <Id>
              <xsl:value-of select="(data[@name='user']/@value)[1]"/>
            </Id>
          </User>
        </xsl:if>
        <xsl:if test="data[@name='runas']/@value[normalize-space(.)]">
          <RunAs>
            <Id>
              <xsl:value-of select="(data[@name='runas']/@value)[1]"/>
            </Id>
          </RunAs>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>Sudo</TypeId>
        <Process>
          <Action>Execute</Action>
          <Type>Application</Type>
          <xsl:if test="data[@name='command']/@value[normalize-space(.)]">
            <Command>
              <xsl:value-of select="(data[@name='command']/@value)[1]"/>
            </Command>
          </xsl:if>
          <xsl:if test="data[@name='args']/@value[normalize-space(.)]">
            <Arguments>
              <xsl:value-of select="(data[@name='args']/@value)[1]"/>
            </Arguments>
          </xsl:if>
          <xsl:sequence select="mcp:data('working_directory', data[@name='pwd']/@value)"/>
        </Process>
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
