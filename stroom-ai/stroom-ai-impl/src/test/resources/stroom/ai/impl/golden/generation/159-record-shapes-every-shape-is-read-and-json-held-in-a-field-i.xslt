<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
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
    <Events Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="*[@key='event_type'] = 'login'">
        <xsl:apply-templates select="." mode="event_type_login"/>
      </xsl:when>
      <xsl:when test="*[@key='event_type'] = 'logout'">
        <xsl:apply-templates select="." mode="event_type_logout"/>
      </xsl:when>
      <xsl:when test="mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='event_type'] = 'user_authentication_check'">
        <xsl:apply-templates select="." mode="event_type_user_authentication_check"/>
      </xsl:when>
      <xsl:when test="mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='event_type'] = 'password_change'">
        <xsl:apply-templates select="." mode="event_type_password_change"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
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
        <xsl:if test="normalize-space(*[@key='level']) or normalize-space(mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='timestamp'])">
          <Unknown>
            <xsl:sequence select="mcp:data('level', *[@key='level'])"/>
            <xsl:sequence select="mcp:data('message.timestamp', mcp:json-to-xml((*[@key='message'])[1])/*/*[@key='timestamp'])"/>
          </Unknown>
        </xsl:if>
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
      <xsl:if test="normalize-space(*[@key='host'])">
        <Device>
          <HostName>
            <xsl:value-of select="*[@key='host']"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="normalize-space(*[@key='src_ip'])">
        <Client>
          <IPAddress>
            <xsl:value-of select="*[@key='src_ip']"/>
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
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
