<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_logon"/>
  </xsl:template>
  <!-- Rule 'logon': every record. Writes an Authenticate event (TypeId Logon) from user, result, sid, time. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:variable name="time" select="data[@name='time']/@value[normalize-space(.)]"/>
      <xsl:if test="$time">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="mcp:parse_time($time[1])"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="user" select="data[@name='user']/@value[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>Acme VPN</Name>
          <Environment>Dev</Environment>
        </System>
        <Generator>vpnd</Generator>
        <Device>
          <HostName>
            <xsl:value-of select="if (normalize-space(data[@name='host']/@value)) then data[@name='host']/@value else 'unknown'"/>
          </HostName>
        </Device>
        <xsl:if test="$user">
          <User>
            <Id>
              <xsl:value-of select="$user"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="$user">
            <User>
              <Id>
                <xsl:value-of select="$user"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="data[@name='result']/@value = ('ok', 'fail')">
            <Outcome>
              <Success>
                <xsl:value-of select="if (data[@name='result']/@value = 'ok') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
          <xsl:variable name="sid" select="data[@name='sid']/@value[normalize-space(.)]"/>
          <xsl:sequence select="mcp:data('session', $sid)"/>
          <xsl:if test="$time">
            <Data Name="seen" Value="{mcp:parse_time($time[1])}"/>
          </xsl:if>
          <xsl:if test="$sid">
            <Data Name="stamp" Value="{stroom:format-date(string($sid[1]))}"/>
          </xsl:if>
        </Authenticate>
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
  <xsl:function name="mcp:parse_time" as="xs:string?">
    <xsl:param name="value" as="item()?"/>
    <xsl:sequence select="stroom:format-date($value, 'dd/MM/yyyy HH:mm:ss', '+10:00')"/>
  </xsl:function>
</xsl:stylesheet>
