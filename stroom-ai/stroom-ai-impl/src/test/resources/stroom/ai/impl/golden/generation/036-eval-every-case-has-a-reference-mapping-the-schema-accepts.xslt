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
      <xsl:if test="exists(concat('2026 ', replace(data[@name='time']/@value, '  ', ' '))[normalize-space(.)])">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(concat('2026 ', replace(data[@name='time']/@value, '  ', ' '))[1], 'yyyy MMM d HH:mm:ss', 'UTC')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>Web</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>sudo</Generator>
        <xsl:if test="normalize-space(data[@name='host']/@value)">
          <Device>
            <HostName>
              <xsl:value-of select="data[@name='host']/@value"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="normalize-space(data[@name='user']/@value)">
          <User>
            <Id>
              <xsl:value-of select="data[@name='user']/@value"/>
            </Id>
          </User>
        </xsl:if>
        <xsl:if test="normalize-space(data[@name='runas']/@value)">
          <RunAs>
            <Id>
              <xsl:value-of select="data[@name='runas']/@value"/>
            </Id>
          </RunAs>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>Sudo</TypeId>
        <Process>
          <Action>Execute</Action>
          <Type>Application</Type>
          <xsl:if test="normalize-space(data[@name='command']/@value)">
            <Command>
              <xsl:value-of select="data[@name='command']/@value"/>
            </Command>
          </xsl:if>
          <xsl:if test="normalize-space(data[@name='args']/@value)">
            <Arguments>
              <xsl:value-of select="data[@name='args']/@value"/>
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
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
