<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$action = 'tunnel-up'">
        <xsl:apply-templates select="." mode="event_type_signed_in"/>
      </xsl:when>
      <xsl:when test="$action = 'ssl-login-fail'">
        <xsl:apply-templates select="." mode="event_type_failed_sign_in"/>
      </xsl:when>
      <xsl:when test="$action = 'tunnel-down'">
        <xsl:apply-templates select="." mode="event_type_signed_out"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'signed in': records where action = tunnel-up. Writes an Authenticate event (TypeId from logid) from user. -->
  <xsl:template match="node()" mode="event_type_signed_in">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='logid']/@value)) then data[@name='logid']/@value else 'signed in'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'failed sign in': records where action = ssl-login-fail. Writes an Authenticate event (TypeId from logid)
       from user. -->
  <xsl:template match="node()" mode="event_type_failed_sign_in">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='logid']/@value)) then data[@name='logid']/@value else 'failed sign in'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <Outcome>
            <Success>false</Success>
          </Outcome>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'signed out': records where action = tunnel-down. Writes an Authenticate event (TypeId from logid) from
       user. -->
  <xsl:template match="node()" mode="event_type_signed_out">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='logid']/@value)) then data[@name='logid']/@value else 'signed out'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'signed in', 'failed sign in', 'signed out' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:variable name="eventtime_ms_parts" select="analyze-string(string(data[@name='eventtime']/@value[1]), '^(\d{13})')"/>
    <xsl:if test="normalize-space($eventtime_ms_parts/fn:match//fn:group[@nr=1])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(string($eventtime_ms_parts/fn:match//fn:group[@nr=1][1]))"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'signed in', 'failed sign in', 'signed out' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>VPN Portal</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>fortigate</Generator>
      <xsl:if test="normalize-space(data[@name='devname']/@value)">
        <Device>
          <HostName>
            <xsl:value-of select="data[@name='devname']/@value"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="normalize-space(data[@name='remip']/@value)">
        <Client>
          <IPAddress>
            <xsl:value-of select="data[@name='remip']/@value"/>
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
  <!-- description: shared by the rules 'signed in', 'failed sign in', 'signed out' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="normalize-space(data[@name='msg']/@value)">
      <Description>
        <xsl:value-of select="data[@name='msg']/@value"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'signed in', 'failed sign in', 'signed out' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="normalize-space(data[@name='user']/@value)">
      <User>
        <Id>
          <xsl:value-of select="data[@name='user']/@value"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
