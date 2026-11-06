<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:function name="mcp:json-to-xml" as="node()?">
    <xsl:param name="text" as="item()*"/>
    <xsl:variable name="json" select="string(($text)[1])"/>
    <xsl:if test="normalize-space($json)">
      <xsl:try>
        <xsl:sequence select="json-to-xml($json)"/>
        <xsl:catch/>
      </xsl:try>
    </xsl:if>
  </xsl:function>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="(mcp:json-to-xml(*[@key='message'])/*/*[@key='event']) = 'login'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="(mcp:json-to-xml(*[@key='message'])/*/*[@key='event']) = 'logout'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="*[@key][not(@key = ('event', 'host', 'message', 'ok', 'time', 'user'))][normalize-space(string(.))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'logon': records where json-to-xml(*[@key='message'])/*/*[@key='event'] = login. Writes an Authenticate
       event. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:variable name="event_detail_type_id" select="(mcp:json-to-xml(*[@key='message'])/*/*[@key='event'])[normalize-space(.)]"/>
          <xsl:if test="count($event_detail_type_id) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($event_detail_type_id), ') for ', 'EventDetail/TypeId, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="if (exists($event_detail_type_id)) then $event_detail_type_id[1] else 'logon'"/>
        </TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:apply-templates select="." mode="outcome"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where json-to-xml(*[@key='message'])/*/*[@key='event'] = logout. Writes an Authenticate
       event. -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:variable name="event_detail_type_id" select="(mcp:json-to-xml(*[@key='message'])/*/*[@key='event'])[normalize-space(.)]"/>
          <xsl:if test="count($event_detail_type_id) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($event_detail_type_id), ') for ', 'EventDetail/TypeId, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="if (exists($event_detail_type_id)) then $event_detail_type_id[1] else 'logoff'"/>
        </TypeId>
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
    <xsl:variable name="event_time_time_created" select="(mcp:json-to-xml(*[@key='message'])/*/*[@key='time'])[normalize-space(.)]"/>
    <xsl:if test="exists($event_time_time_created)">
      <EventTime>
        <TimeCreated>
          <xsl:if test="count($event_time_time_created) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($event_time_time_created), ') for ', 'EventTime/TimeCreated, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="stroom:format-date($event_time_time_created[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>App</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>log-shipper</Generator>
      <xsl:if test="*[@key='host'][normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(*[@key='host'])[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="user_id" select="(mcp:json-to-xml(*[@key='message'])/*/*[@key='user'])[normalize-space(.)]"/>
      <xsl:if test="exists($user_id)">
        <User>
          <Id>
            <xsl:if test="count($user_id) gt 1">
              <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($user_id), ') for ', 'EventSource/User/Id, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
            </xsl:if>
            <xsl:value-of select="$user_id[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="user_id" select="(mcp:json-to-xml(*[@key='message'])/*/*[@key='user'])[normalize-space(.)]"/>
    <xsl:if test="exists($user_id)">
      <User>
        <Id>
          <xsl:if test="count($user_id) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($user_id), ') for ', 'EventDetail/Authenticate/User/Id, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="$user_id[1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- outcome: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:variable name="outcome_success" select="(mcp:json-to-xml(*[@key='message'])/*/*[@key='ok'])[normalize-space(.)]"/>
    <xsl:if test="exists($outcome_success)">
      <Outcome>
        <Success>
          <xsl:if test="count($outcome_success) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($outcome_success), ') for ', 'EventDetail/Authenticate/Outcome/Success, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="$outcome_success[1]"/>
        </Success>
      </Outcome>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
