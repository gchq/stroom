<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="event_action" select="*[@key='event']/*[@key='action'][normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$event_action = 'user-login'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$event_action = 'file-delete'">
        <xsl:apply-templates select="." mode="event_type_file_delete"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('event.action=', string(($event_action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:for-each select="*[@key][not(@key = ('@timestamp', 'action', 'event', 'file', 'host', 'ip', 'name', 'outcome', 'path', 'source', 'user'))][normalize-space(string(.))]">
      <!-- A field no rule reads: logged once a stream (ignored_fields: left out on purpose) -->
      <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
      <xsl:if test="not(string(stroom:get($mcp_unread)))">
        <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
        <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  <!-- Rule 'logon': records where event.action = user-login. Writes an Authenticate event (TypeId from event.action)
       from user.name, event.outcome. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="*[@key='user']/*[@key='name'][normalize-space(.)]">
            <User>
              <Id>
                <xsl:value-of select="(*[@key='user']/*[@key='name'])[1]"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="(*[@key='event']/*[@key='outcome'])[1] = ('success', 'failure')">
            <Outcome>
              <Success>
                <xsl:value-of select="if ((*[@key='event']/*[@key='outcome'])[1] = 'success') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'file-delete': records where event.action = file-delete. Writes a Delete event (TypeId from event.action)
       from file.path, file.name. -->
  <xsl:template match="node()" mode="event_type_file_delete">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Delete>
          <Object>
            <Type>File</Type>
            <xsl:if test="*[@key='file']/*[@key='path'][normalize-space(.)]">
              <Id>
                <xsl:value-of select="(*[@key='file']/*[@key='path'])[1]"/>
              </Id>
            </xsl:if>
            <xsl:if test="*[@key='file']/*[@key='name'][normalize-space(.)]">
              <Name>
                <xsl:value-of select="(*[@key='file']/*[@key='name'])[1]"/>
              </Name>
            </xsl:if>
          </Object>
        </Delete>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'file-delete' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="*[@key='@timestamp'][normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((*[@key='@timestamp'])[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'file-delete' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Endpoint Agent</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>ecs-shipper</Generator>
      <xsl:if test="*[@key='host']/*[@key='name'][normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(*[@key='host']/*[@key='name'])[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="*[@key='source']/*[@key='ip'][normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="(*[@key='source']/*[@key='ip'])[1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="*[@key='user']/*[@key='name'][normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(*[@key='user']/*[@key='name'])[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'file-delete' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="*[@key='event']/*[@key='action'][normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="(*[@key='event']/*[@key='action'])[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
