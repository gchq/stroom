<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs fn mcp">
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
    <xsl:variable name="user_parts" select="analyze-string(string((mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])[1]), '^user=(\S+) action=(\S+) src=(\S+) result=(\S+)$')"/>
    <xsl:variable name="action" select="$user_parts/fn:match//fn:group[@nr=2][normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$action = 'LOGIN'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$action = 'LOGOUT'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_logoff"/>
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
      <xsl:for-each select="*[@key][not(@key = ('host', 'msg', 'payload', 'ts'))][normalize-space(string(.))]">
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
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:apply-templates select="." mode="outcome"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where action = LOGOUT. Writes an Authenticate event (TypeId from action). -->
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
          <xsl:apply-templates select="." mode="outcome"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:variable name="event_time_time_created" select="(mcp:json-to-xml(*[@key='payload'])/*/*[@key='ts'])[normalize-space(.)]"/>
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
        <Name>Gateway</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>gateway</Generator>
      <xsl:if test="*[@key='host'][normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(*[@key='host'])[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="user_parts" select="analyze-string(string((mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])[1]), '^user=(\S+) action=(\S+) src=(\S+) result=(\S+)$')"/>
      <xsl:if test="$user_parts/fn:match//fn:group[@nr=3][normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="($user_parts/fn:match//fn:group[@nr=3])[1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="$user_parts/fn:match//fn:group[@nr=1][normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="($user_parts/fn:match//fn:group[@nr=1])[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:variable name="user_parts" select="analyze-string(string((mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])[1]), '^user=(\S+) action=(\S+) src=(\S+) result=(\S+)$')"/>
    <xsl:if test="$user_parts/fn:match//fn:group[@nr=2][normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="($user_parts/fn:match//fn:group[@nr=2])[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="description">
    <xsl:variable name="text" select="(mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])[normalize-space(.)]"/>
    <xsl:if test="exists($text)">
      <Description>
        <xsl:if test="count($text) gt 1">
          <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($text), ') for ', 'EventDetail/Description, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
        </xsl:if>
        <xsl:value-of select="$text[1]"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="user_parts" select="analyze-string(string((mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])[1]), '^user=(\S+) action=(\S+) src=(\S+) result=(\S+)$')"/>
    <xsl:if test="$user_parts/fn:match//fn:group[@nr=1][normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="($user_parts/fn:match//fn:group[@nr=1])[1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- outcome: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:variable name="user_parts" select="analyze-string(string((mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])[1]), '^user=(\S+) action=(\S+) src=(\S+) result=(\S+)$')"/>
    <xsl:if test="($user_parts/fn:match//fn:group[@nr=4])[1] = ('success', 'failure')">
      <Outcome>
        <Success>
          <xsl:value-of select="if (($user_parts/fn:match//fn:group[@nr=4])[1] = 'success') then 'true' else 'false'"/>
        </Success>
      </Outcome>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
