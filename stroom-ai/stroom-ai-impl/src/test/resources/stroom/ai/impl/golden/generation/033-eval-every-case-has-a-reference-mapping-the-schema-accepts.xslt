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
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="(mcp:json-to-xml(*[@key='message'])/*/*[@key='event']) = 'login'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="(mcp:json-to-xml(*[@key='message'])/*/*[@key='event']) = 'logout'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where json-to-xml(*[@key='message'])/*/*[@key='event'] = login. Writes an Authenticate
       event. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (exists((mcp:json-to-xml(*[@key='message'])/*/*[@key='event'])[normalize-space(.)])) then (mcp:json-to-xml(*[@key='message'])/*/*[@key='event']) else 'logon'"/>
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
          <xsl:value-of select="if (exists((mcp:json-to-xml(*[@key='message'])/*/*[@key='event'])[normalize-space(.)])) then (mcp:json-to-xml(*[@key='message'])/*/*[@key='event']) else 'logoff'"/>
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
    <xsl:if test="exists((mcp:json-to-xml(*[@key='message'])/*/*[@key='time'])[normalize-space(.)])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((mcp:json-to-xml(*[@key='message'])/*/*[@key='time'])[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
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
      <xsl:if test="normalize-space(*[@key='host'])">
        <Device>
          <HostName>
            <xsl:value-of select="*[@key='host']"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="exists((mcp:json-to-xml(*[@key='message'])/*/*[@key='user'])[normalize-space(.)])">
        <User>
          <Id>
            <xsl:value-of select="(mcp:json-to-xml(*[@key='message'])/*/*[@key='user'])"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="exists((mcp:json-to-xml(*[@key='message'])/*/*[@key='user'])[normalize-space(.)])">
      <User>
        <Id>
          <xsl:value-of select="(mcp:json-to-xml(*[@key='message'])/*/*[@key='user'])"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- outcome: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:if test="exists((mcp:json-to-xml(*[@key='message'])/*/*[@key='ok'])[normalize-space(.)])">
      <Outcome>
        <Success>
          <xsl:value-of select="(mcp:json-to-xml(*[@key='message'])/*/*[@key='ok'])"/>
        </Success>
      </Outcome>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
