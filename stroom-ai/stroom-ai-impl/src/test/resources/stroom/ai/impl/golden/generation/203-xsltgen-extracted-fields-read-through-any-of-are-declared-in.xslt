<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="data[@name='action']/@value = 'login'">
        <xsl:apply-templates select="." mode="event_type_login"/>
      </xsl:when>
      <xsl:when test="data[@name='action']/@value = 'logout'">
        <xsl:apply-templates select="." mode="event_type_logout"/>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'login': records where action = login. Writes an Authenticate event (TypeId login) from user_quoted,
       user_plain (extracted from msg). -->
  <xsl:template match="node()" mode="event_type_login">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>login</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logout': records where action = logout. Writes an Authenticate event (TypeId logout) from user_quoted,
       user_plain (extracted from msg). -->
  <xsl:template match="node()" mode="event_type_logout">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>logout</TypeId>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'login', 'logout' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='time']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="data[@name='time']/@value"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'login', 'logout' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Acme VPN</Name>
        <Environment>Dev</Environment>
      </System>
      <Generator>vpnd</Generator>
      <Device>
        <HostName>
          <xsl:value-of select="if (normalize-space(data[@name='host']/@value)) then data[@name='host']/@value else 'unknown'"/>
        </HostName>
      </Device>
      <xsl:variable name="user_quoted_parts" select="analyze-string(string(data[@name='msg']/@value[1]), ' user=(?:&quot;([^&quot;]*)&quot;|(\S+))')"/>
      <xsl:if test="exists((($user_quoted_parts/fn:match//fn:group[@nr=1], $user_quoted_parts/fn:match//fn:group[@nr=2])[normalize-space(.)][1])[normalize-space(.)])">
        <User>
          <Id>
            <xsl:value-of select="(($user_quoted_parts/fn:match//fn:group[@nr=1], $user_quoted_parts/fn:match//fn:group[@nr=2])[normalize-space(.)][1])"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- user: shared by the rules 'login', 'logout' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="user_quoted_parts" select="analyze-string(string(data[@name='msg']/@value[1]), ' user=(?:&quot;([^&quot;]*)&quot;|(\S+))')"/>
    <xsl:if test="exists((($user_quoted_parts/fn:match//fn:group[@nr=1], $user_quoted_parts/fn:match//fn:group[@nr=2])[normalize-space(.)][1])[normalize-space(.)])">
      <User>
        <Id>
          <xsl:value-of select="(($user_quoted_parts/fn:match//fn:group[@nr=1], $user_quoted_parts/fn:match//fn:group[@nr=2])[normalize-space(.)][1])"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
