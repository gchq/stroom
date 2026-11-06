<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="data[@name='action']/@value = 'login'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:for-each select="data[@name][not(@name = ('action', 'host', 'result', 'sid', 'time', 'user'))][normalize-space(string(@value))]">
      <!-- A field no rule reads: logged once a stream (ignored_fields: left out on purpose) -->
      <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
      <xsl:if test="not(string(stroom:get($mcp_unread)))">
        <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
        <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  <!-- Rule 'logon': records where action = login. Writes an Authenticate event (TypeId Logon) from user, result, sid,
       host. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="data[@name='user']/@value[normalize-space(.)]">
            <User>
              <Id>
                <xsl:value-of select="(data[@name='user']/@value)[1]"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="(data[@name='result']/@value)[1] = ('ok', 'fail')">
            <Outcome>
              <Success>
                <xsl:value-of select="if ((data[@name='result']/@value)[1] = 'ok') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
          <xsl:if test="data[@name='sid']/@value[normalize-space(.)]">
            <Data Name="Session" Value="{(data[@name='sid']/@value)[1]}"/>
          </xsl:if>
          <xsl:if test="data[@name='host']/@value[normalize-space(.)]">
            <Data Name="ServerNode" Value="{(data[@name='host']/@value)[1]}"/>
          </xsl:if>
          <Data Name="IPAddress" Value="a{{b}}"/>
          <xsl:variable name="authenticate_data" select="replace(data[@name='user']/@value, '^(.{2}).*', '$1')[normalize-space(.)]"/>
          <xsl:if test="exists($authenticate_data)">
            <xsl:if test="count($authenticate_data) gt 1">
              <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($authenticate_data), ') for ', 'EventDetail/Authenticate/Data, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
            </xsl:if>
            <Data Name="FirstTwo" Value="{$authenticate_data[1]}"/>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from action) from action. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action[1] else 'other'"/>
        </TypeId>
        <Unknown>
          <Data Name="Action" Value="{$action[1]}"/>
        </Unknown>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="(data[@name='time']/@value)[1]"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Acme VPN</Name>
        <Environment>Dev</Environment>
      </System>
      <Generator>vpnd</Generator>
      <Device>
        <HostName>
          <xsl:value-of select="if (data[@name='host']/@value[normalize-space(.)]) then (data[@name='host']/@value)[1] else 'unknown'"/>
        </HostName>
      </Device>
      <xsl:if test="data[@name='user']/@value[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(data[@name='user']/@value)[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
</xsl:stylesheet>
