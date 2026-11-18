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
        <xsl:variable name="parsed" select="json-to-xml($json)"/>
        <xsl:sequence select="if ($parsed/*[local-name() = 'string'] and matches($parsed/*, '^\s*[\[{]')) then mcp:json-to-xml(string($parsed/*)) else $parsed"/>
        <xsl:catch/>
      </xsl:try>
    </xsl:if>
  </xsl:function>
  <xsl:template match="/">
    <Events Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <!-- Fields of the record no rule reads are logged (mcp_unread_fields) -->
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:variable name="message_event_type" select="mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='event_type'][normalize-space(.)]"/>
    <xsl:variable name="event_type_2" select="*[@key='event_type'][normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$event_type_2 = 'login'">
        <xsl:apply-templates select="." mode="event_type_login"/>
      </xsl:when>
      <xsl:when test="$event_type_2 = 'logout'">
        <xsl:apply-templates select="." mode="event_type_logout"/>
      </xsl:when>
      <xsl:when test="$message_event_type = 'user_authentication_check'">
        <xsl:apply-templates select="." mode="event_type_user_authentication_check"/>
      </xsl:when>
      <xsl:when test="$message_event_type = 'password_change'">
        <xsl:apply-templates select="." mode="event_type_password_change"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
        <xsl:call-template name="mcp_log_kind">
          <xsl:with-param name="group" select="concat('other|', string-join((string(($event_type_2)[1]), string(($message_event_type)[1])), '|'))"/>
          <xsl:with-param name="line" select="concat('Caught by rule ''other'': record ', $record-no, ' (', string-join((concat('event_type=', string(($event_type_2)[1])), concat('message.event_type=', string(($message_event_type)[1]))), ' | '), ')')"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="*[@key][not(@key = ('@timestamp', 'event_type', 'host', 'level', 'message', 'src_ip', 'timestamp', 'user'))][normalize-space(string(.))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- mcp_log_kind: a record a rule keeps as Unknown on purpose, or a catch-all rule takes, logged once a stream for
       each kind (the rule and the values the rules test) -->
  <xsl:template name="mcp_log_kind">
    <xsl:param name="group"/>
    <xsl:param name="line"/>
    <xsl:if test="not(string(stroom:get(concat('mcp-kind:', $group))))">
      <xsl:value-of select="stroom:put(concat('mcp-kind:', $group), 'y')"/>
      <xsl:sequence select="stroom:log('INFO', $line)"/>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'login': records where event_type = login. Writes an Authenticate event from user, message.user, level,
       message.timestamp. -->
  <xsl:template match="node()" mode="event_type_login">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:apply-templates select="." mode="event_detail"/>
    </Event>
  </xsl:template>
  <!-- Rule 'logout': records where event_type = logout. Writes an Authenticate event from user, message.user, level,
       message.timestamp. -->
  <xsl:template match="node()" mode="event_type_logout">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:sequence select="mcp:data('level', *[@key='level'])"/>
          <xsl:sequence select="mcp:data('message.timestamp', mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='timestamp'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'user_authentication_check': records where message.event_type = user_authentication_check. Writes an
       Authenticate event from user, message.user, level, message.timestamp. -->
  <xsl:template match="node()" mode="event_type_user_authentication_check">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:apply-templates select="." mode="event_detail"/>
    </Event>
  </xsl:template>
  <!-- Rule 'password_change': records where message.event_type = password_change. Writes an Authenticate event from
       user, message.user, level, message.timestamp. -->
  <xsl:template match="node()" mode="event_type_password_change">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:apply-templates select="." mode="event_detail"/>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event from level, message.timestamp. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (exists(((*[@key='event_type'], mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='event_type'])[normalize-space(.)][1])[normalize-space(.)])) then ((*[@key='event_type'], mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='event_type'])[normalize-space(.)][1]) else 'other'"/>
        </TypeId>
        <Unknown>
          <xsl:sequence select="mcp:data('level', *[@key='level'])"/>
          <xsl:sequence select="mcp:data('message.timestamp', mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='timestamp'])"/>
        </Unknown>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'login', 'logout', 'user_authentication_check', 'password_change', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="exists(((*[@key='timestamp'], *[@key='@timestamp'])[normalize-space(.)][1])[normalize-space(.)])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(((*[@key='timestamp'], *[@key='@timestamp'])[normalize-space(.)][1])[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'login', 'logout', 'user_authentication_check', 'password_change', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>App</Name>
        <Environment>Prod</Environment>
      </System>
      <Generator>App</Generator>
      <xsl:if test="*[@key='host'][normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(*[@key='host'])[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="*[@key='src_ip'][normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="(*[@key='src_ip'])[1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="exists(((*[@key='user'], mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='user'])[normalize-space(.)][1])[normalize-space(.)])">
        <User>
          <Id>
            <xsl:value-of select="((*[@key='user'], mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='user'])[normalize-space(.)][1])"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- event_detail: shared by the rules 'login', 'user_authentication_check', 'password_change' -->
  <xsl:template match="node()" mode="event_detail">
    <EventDetail>
      <xsl:apply-templates select="." mode="type_id"/>
      <Authenticate>
        <Action>Logon</Action>
        <xsl:apply-templates select="." mode="user"/>
        <xsl:sequence select="mcp:data('level', *[@key='level'])"/>
        <xsl:sequence select="mcp:data('message.timestamp', mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='timestamp'])"/>
      </Authenticate>
    </EventDetail>
  </xsl:template>
  <!-- type_id: shared by the rules 'login', 'logout', 'user_authentication_check', 'password_change' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="exists(((*[@key='event_type'], mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='event_type'])[normalize-space(.)][1])[normalize-space(.)])">
      <TypeId>
        <xsl:value-of select="((*[@key='event_type'], mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='event_type'])[normalize-space(.)][1])"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'login', 'logout', 'user_authentication_check', 'password_change' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="exists(((*[@key='user'], mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='user'])[normalize-space(.)][1])[normalize-space(.)])">
      <User>
        <Id>
          <xsl:value-of select="((*[@key='user'], mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='user'])[normalize-space(.)][1])"/>
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
