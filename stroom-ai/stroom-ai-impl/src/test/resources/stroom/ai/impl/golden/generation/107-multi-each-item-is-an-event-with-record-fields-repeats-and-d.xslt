<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="*[@key='events']/*" mode="item">
      <xsl:with-param name="record" select="." tunnel="yes"/>
    </xsl:apply-templates>
    <xsl:call-template name="mcp_unread_fields"/>
  </xsl:template>
  <xsl:template match="*" mode="item">
    <xsl:param name="record" tunnel="yes"/>
    <xsl:variable name="action" select="*[@key='action'][normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="exists($record/*[@key='host'][matches(., '^qa-')])">
        <!-- drop: QA traffic: left untranslated on purpose -->
      </xsl:when>
      <xsl:when test="$action = 'HEARTBEAT'">
        <!-- drop: heartbeats are not events: left untranslated on purpose -->
      </xsl:when>
      <xsl:when test="$action = 'LOGIN'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$action = 'LOGOUT'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="*[@key][not(@key = ('action', 'batch', 'events', 'host', 'roles', 'ts', 'user'))][normalize-space(string(.))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'logon': records where action = LOGIN. Writes an Authenticate event (TypeId from action). -->
  <xsl:template match="node()" mode="event_type_logon">
    <xsl:param name="record" tunnel="yes"/>
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:sequence select="mcp:data('batch', $record/*[@key='batch'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where action = LOGOUT. Writes an Authenticate event (TypeId from action). -->
  <xsl:template match="node()" mode="event_type_logoff">
    <xsl:param name="record" tunnel="yes"/>
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:sequence select="mcp:data('batch', $record/*[@key='batch'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="*[@key='ts'][normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="(*[@key='ts'])[1]"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="event_source">
    <xsl:param name="record" tunnel="yes"/>
    <EventSource>
      <System>
        <Name>App</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>audit</Generator>
      <xsl:if test="$record/*[@key='host'][normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="($record/*[@key='host'])[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="user" select="*[@key='user'][normalize-space(.)]"/>
      <xsl:if test="$user or exists((*[@key='roles'])[normalize-space(.)])">
        <User>
          <xsl:if test="$user">
            <Id>
              <xsl:value-of select="$user[1]"/>
            </Id>
          </xsl:if>
          <xsl:if test="exists((*[@key='roles'])[normalize-space(.)])">
            <Groups>
              <xsl:for-each select="*[@key='roles']/*">
                <Group>
                  <Name>
                    <xsl:value-of select="."/>
                  </Name>
                </Group>
              </xsl:for-each>
            </Groups>
          </xsl:if>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="*[@key='action'][normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="(*[@key='action'])[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="*[@key='user'][normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="(*[@key='user'])[1]"/>
        </Id>
      </User>
    </xsl:if>
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
