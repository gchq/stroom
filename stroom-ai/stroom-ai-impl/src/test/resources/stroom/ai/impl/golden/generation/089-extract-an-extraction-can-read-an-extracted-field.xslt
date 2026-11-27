<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs fn">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <!-- Fields of the record no rule reads are logged (mcp_unread_fields), but for records left untranslated on purpose -->
    <xsl:variable name="ts_parts" select="analyze-string(string((*[@key='message'])[1]), '^(\S+) (\S+) (\S+) (.*)$')"/>
    <xsl:variable name="action" select="$ts_parts/fn:match//fn:group[@nr=3][normalize-space(.)]"/>
    <xsl:if test="not(((not($ts_parts/fn:match//fn:group[@nr=1][normalize-space(.)])) and not(($action = 'LOGIN') or ($action = 'LOGOUT'))))">
      <xsl:call-template name="mcp_unread_fields"/>
    </xsl:if>
    <xsl:choose>
      <xsl:when test="$action = 'LOGIN'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$action = 'LOGOUT'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:when test="not($ts_parts/fn:match//fn:group[@nr=1][normalize-space(.)])">
        <!-- unparsed: left untranslated on purpose -->
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="*[@key][not(@key = ('host', 'message'))][normalize-space(string(.))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'logon': records where action = LOGIN. Writes an Authenticate event (TypeId from action) from user
       (extracted from message). -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where action = LOGOUT. Writes an Authenticate event (TypeId from action) from user
       (extracted from message). -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:variable name="ts_parts" select="analyze-string(string((*[@key='message'])[1]), '^(\S+) (\S+) (\S+) (.*)$')"/>
    <xsl:if test="$ts_parts/fn:match//fn:group[@nr=1][normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="($ts_parts/fn:match//fn:group[@nr=1])[1]"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>App</Name>
        <Environment>Dev</Environment>
      </System>
      <Generator>app</Generator>
      <xsl:if test="*[@key='host'][normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(*[@key='host'])[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="ts_parts" select="analyze-string(string((*[@key='message'])[1]), '^(\S+) (\S+) (\S+) (.*)$')"/>
      <xsl:variable name="client_ip_parts" select="analyze-string(string(($ts_parts/fn:match//fn:group[@nr=4])[1]), 'from (\S+)$')"/>
      <xsl:if test="$client_ip_parts/fn:match//fn:group[@nr=1][normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="($client_ip_parts/fn:match//fn:group[@nr=1])[1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="$ts_parts/fn:match//fn:group[@nr=2][normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="($ts_parts/fn:match//fn:group[@nr=2])[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:variable name="ts_parts" select="analyze-string(string((*[@key='message'])[1]), '^(\S+) (\S+) (\S+) (.*)$')"/>
    <xsl:if test="$ts_parts/fn:match//fn:group[@nr=3][normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="($ts_parts/fn:match//fn:group[@nr=3])[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="description">
    <xsl:variable name="ts_parts" select="analyze-string(string((*[@key='message'])[1]), '^(\S+) (\S+) (\S+) (.*)$')"/>
    <xsl:if test="$ts_parts/fn:match//fn:group[@nr=4][normalize-space(.)]">
      <Description>
        <xsl:value-of select="($ts_parts/fn:match//fn:group[@nr=4])[1]"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="ts_parts" select="analyze-string(string((*[@key='message'])[1]), '^(\S+) (\S+) (\S+) (.*)$')"/>
    <xsl:if test="$ts_parts/fn:match//fn:group[@nr=2][normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="($ts_parts/fn:match//fn:group[@nr=2])[1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
