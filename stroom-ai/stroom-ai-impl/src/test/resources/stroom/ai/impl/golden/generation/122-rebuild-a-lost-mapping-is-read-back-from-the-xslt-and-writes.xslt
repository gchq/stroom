<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs fn">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="message_1_parts" select="analyze-string(string(*[@key='message'][1]), '^(\S+) (\S+) (\S+) (.*)$')"/>
    <xsl:choose>
      <xsl:when test="$message_1_parts/fn:match//fn:group[@nr=3] = 'LOGIN'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$message_1_parts/fn:match//fn:group[@nr=3] = 'LOGOUT'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:when test="not(normalize-space($message_1_parts/fn:match//fn:group[@nr=1]))">
        <!-- unparsed: left untranslated on purpose -->
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where message_3 = LOGIN. Writes an Authenticate event (TypeId from message_3). -->
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
  <!-- Rule 'logoff': records where message_3 = LOGOUT. Writes an Authenticate event (TypeId from message_3). -->
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
    <xsl:variable name="message_1_parts" select="analyze-string(string(*[@key='message'][1]), '^(\S+) (\S+) (\S+) (.*)$')"/>
    <xsl:if test="normalize-space($message_1_parts/fn:match//fn:group[@nr=1])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="$message_1_parts/fn:match//fn:group[@nr=1]"/>
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
      <xsl:if test="normalize-space(*[@key='host'])">
        <Device>
          <HostName>
            <xsl:value-of select="*[@key='host']"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="message_1_parts" select="analyze-string(string(*[@key='message'][1]), '^(\S+) (\S+) (\S+) (.*)$')"/>
      <xsl:if test="normalize-space($message_1_parts/fn:match//fn:group[@nr=2])">
        <User>
          <Id>
            <xsl:value-of select="$message_1_parts/fn:match//fn:group[@nr=2]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:variable name="message_1_parts" select="analyze-string(string(*[@key='message'][1]), '^(\S+) (\S+) (\S+) (.*)$')"/>
    <xsl:if test="normalize-space($message_1_parts/fn:match//fn:group[@nr=3])">
      <TypeId>
        <xsl:value-of select="$message_1_parts/fn:match//fn:group[@nr=3]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="description">
    <xsl:variable name="message_1_parts" select="analyze-string(string(*[@key='message'][1]), '^(\S+) (\S+) (\S+) (.*)$')"/>
    <xsl:if test="normalize-space($message_1_parts/fn:match//fn:group[@nr=4])">
      <Description>
        <xsl:value-of select="$message_1_parts/fn:match//fn:group[@nr=4]"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="message_1_parts" select="analyze-string(string(*[@key='message'][1]), '^(\S+) (\S+) (\S+) (.*)$')"/>
    <xsl:if test="normalize-space($message_1_parts/fn:match//fn:group[@nr=2])">
      <User>
        <Id>
          <xsl:value-of select="$message_1_parts/fn:match//fn:group[@nr=2]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
