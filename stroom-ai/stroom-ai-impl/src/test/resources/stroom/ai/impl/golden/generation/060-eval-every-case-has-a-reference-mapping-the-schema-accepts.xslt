<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$action = 'tunnel-up'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_signed_in"/>
      </xsl:when>
      <xsl:when test="$action = 'ssl-login-fail'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_failed_sign_in"/>
      </xsl:when>
      <xsl:when test="$action = 'tunnel-down'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_signed_out"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('action', 'devname', 'eventtime', 'logid', 'msg', 'remip', 'user'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'signed in': records where action = tunnel-up. Writes an Authenticate event (TypeId from logid) from user. -->
  <xsl:template match="node()" mode="event_type_signed_in">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (data[@name='logid']/@value[normalize-space(.)]) then (data[@name='logid']/@value)[1] else 'signed in'"/>
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
          <xsl:value-of select="if (data[@name='logid']/@value[normalize-space(.)]) then (data[@name='logid']/@value)[1] else 'failed sign in'"/>
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
          <xsl:value-of select="if (data[@name='logid']/@value[normalize-space(.)]) then (data[@name='logid']/@value)[1] else 'signed out'"/>
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
    <xsl:variable name="eventtime_ms_parts" select="analyze-string(string((data[@name='eventtime']/@value)[1]), '^(\d{13})')"/>
    <xsl:if test="$eventtime_ms_parts/fn:match//fn:group[@nr=1][normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(string(($eventtime_ms_parts/fn:match//fn:group[@nr=1])[1]))"/>
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
      <xsl:if test="data[@name='devname']/@value[normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(data[@name='devname']/@value)[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="data[@name='remip']/@value[normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="(data[@name='remip']/@value)[1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="data[@name='user']/@value[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(data[@name='user']/@value)[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- description: shared by the rules 'signed in', 'failed sign in', 'signed out' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="data[@name='msg']/@value[normalize-space(.)]">
      <Description>
        <xsl:value-of select="(data[@name='msg']/@value)[1]"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'signed in', 'failed sign in', 'signed out' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="data[@name='user']/@value[normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="(data[@name='user']/@value)[1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
