<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs fn mcp">
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
    <xsl:variable name="user_parts" select="analyze-string(string((mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])[1]), '^user=(\S+) action=(\S+) src=(\S+) result=(\S+)$')"/>
    <xsl:choose>
      <xsl:when test="$user_parts/fn:match//fn:group[@nr=2] = 'LOGIN'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$user_parts/fn:match//fn:group[@nr=2] = 'LOGOUT'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
    </xsl:choose>
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
    <xsl:if test="exists((mcp:json-to-xml(*[@key='payload'])/*/*[@key='ts'])[normalize-space(.)])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((mcp:json-to-xml(*[@key='payload'])/*/*[@key='ts'])[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
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
      <xsl:if test="normalize-space(*[@key='host'])">
        <Device>
          <HostName>
            <xsl:value-of select="*[@key='host']"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="user_parts" select="analyze-string(string((mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])[1]), '^user=(\S+) action=(\S+) src=(\S+) result=(\S+)$')"/>
      <xsl:if test="normalize-space($user_parts/fn:match//fn:group[@nr=3])">
        <Client>
          <IPAddress>
            <xsl:value-of select="$user_parts/fn:match//fn:group[@nr=3]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="normalize-space($user_parts/fn:match//fn:group[@nr=1])">
        <User>
          <Id>
            <xsl:value-of select="$user_parts/fn:match//fn:group[@nr=1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:variable name="user_parts" select="analyze-string(string((mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])[1]), '^user=(\S+) action=(\S+) src=(\S+) result=(\S+)$')"/>
    <xsl:if test="normalize-space($user_parts/fn:match//fn:group[@nr=2])">
      <TypeId>
        <xsl:value-of select="$user_parts/fn:match//fn:group[@nr=2]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="exists((mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])[normalize-space(.)])">
      <Description>
        <xsl:value-of select="(mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="user_parts" select="analyze-string(string((mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])[1]), '^user=(\S+) action=(\S+) src=(\S+) result=(\S+)$')"/>
    <xsl:if test="normalize-space($user_parts/fn:match//fn:group[@nr=1])">
      <User>
        <Id>
          <xsl:value-of select="$user_parts/fn:match//fn:group[@nr=1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- outcome: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:variable name="user_parts" select="analyze-string(string((mcp:json-to-xml(*[@key='payload'])/*/*[@key='msg'])[1]), '^user=(\S+) action=(\S+) src=(\S+) result=(\S+)$')"/>
    <xsl:if test="$user_parts/fn:match//fn:group[@nr=4] = ('success', 'failure')">
      <Outcome>
        <Success>
          <xsl:value-of select="if ($user_parts/fn:match//fn:group[@nr=4] = 'success') then 'true' else 'false'"/>
        </Success>
      </Outcome>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
