<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$action = 'login'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$action = 'logout'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where action = login. Writes an Authenticate event (TypeId from action). -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:apply-templates select="." mode="outcome"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where action = logout. Writes an Authenticate event (TypeId from action). -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:apply-templates select="." mode="outcome"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='time']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd HH:mm:ss', 'UTC')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Acme VPN</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>vpn-gateway</Generator>
      <xsl:if test="normalize-space(data[@name='gateway']/@value)">
        <Device>
          <HostName>
            <xsl:value-of select="data[@name='gateway']/@value"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="normalize-space(data[@name='src_ip']/@value)">
        <Client>
          <IPAddress>
            <xsl:value-of select="data[@name='src_ip']/@value"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="normalize-space(data[@name='user']/@value)">
        <User>
          <Id>
            <xsl:value-of select="data[@name='user']/@value"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(data[@name='action']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='action']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="normalize-space(data[@name='user']/@value)">
      <User>
        <Id>
          <xsl:value-of select="data[@name='user']/@value"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- outcome: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:if test="data[@name='result']/@value = ('success', 'failure')">
      <Outcome>
        <Success>
          <xsl:value-of select="if (data[@name='result']/@value = 'success') then 'true' else 'false'"/>
        </Success>
      </Outcome>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
