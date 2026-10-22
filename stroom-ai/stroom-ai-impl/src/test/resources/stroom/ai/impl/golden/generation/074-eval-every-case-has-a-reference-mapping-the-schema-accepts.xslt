<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
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
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="json_action" select="mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='action'][normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="not(normalize-space(mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='time']))">
        <!-- drop: damaged lines (not JSON): left untranslated on purpose -->
      </xsl:when>
      <xsl:when test="$json_action = 'login'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$json_action = 'logout'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:when test="$json_action = 'password_change'">
        <xsl:apply-templates select="." mode="event_type_password"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('json.time=', string((normalize-space(mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='time']))[1])), concat('json.action=', string(($json_action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where json.action = login. Writes an Authenticate event (TypeId from json.action) from
       json.user, json.result. -->
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
  <!-- Rule 'logoff': records where json.action = logout. Writes an Authenticate event (TypeId from json.action) from
       json.user. -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'password': records where json.action = password_change. Writes an Authenticate event (TypeId from
       json.action) from json.user, json.result. -->
  <xsl:template match="node()" mode="event_type_password">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>ChangePassword</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:apply-templates select="." mode="outcome"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff', 'password' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='time'])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='time'][1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'logoff', 'password' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Auth App</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>auth-app</Generator>
      <Device>
        <HostName>auth01</HostName>
      </Device>
      <xsl:if test="normalize-space(mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='ip'])">
        <Client>
          <IPAddress>
            <xsl:value-of select="mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='ip']"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="normalize-space(mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='user'])">
        <User>
          <Id>
            <xsl:value-of select="mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='user']"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'logoff', 'password' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='action'])">
      <TypeId>
        <xsl:value-of select="mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='action']"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff', 'password' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="normalize-space(mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='user'])">
      <User>
        <Id>
          <xsl:value-of select="mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='user']"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- outcome: shared by the rules 'logon', 'password' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:if test="mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='result'] = ('success', 'failure')">
      <Outcome>
        <Success>
          <xsl:value-of select="if (mcp:json-to-xml((data[@name='json']/@value)[1])/*/*[@key='result'] = 'success') then 'true' else 'false'"/>
        </Success>
      </Outcome>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
