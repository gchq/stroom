<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:variable name="resultToSuccess" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'ok'" select="'true'"/>
      <xsl:map-entry key="'fail'" select="'false'"/>
    </xsl:map>
  </xsl:variable>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$action = 'login'">
        <Event>
          <xsl:call-template name="eventTime"/>
          <xsl:call-template name="eventSource"/>
          <EventDetail>
            <TypeId>Logon</TypeId>
            <Authenticate>
              <Action>Logon</Action>
              <xsl:variable name="user" select="data[@name='user']/@value[normalize-space(.)]"/>
              <xsl:if test="$user">
                <User>
                  <Id>
                    <xsl:value-of select="$user"/>
                  </Id>
                </User>
              </xsl:if>
              <xsl:variable name="result" select="data[@name='result']/@value[normalize-space(.)]"/>
              <xsl:if test="exists($resultToSuccess?($result))">
                <Outcome>
                  <Success>
                    <xsl:value-of select="$resultToSuccess?($result)[1]"/>
                  </Success>
                </Outcome>
              </xsl:if>
              <xsl:variable name="sid" select="data[@name='sid']/@value[normalize-space(.)]"/>
              <xsl:sequence select="mcp:data('session', $sid)"/>
            </Authenticate>
          </EventDetail>
        </Event>
      </xsl:when>
      <xsl:otherwise>
        <Event>
          <xsl:call-template name="eventTime"/>
          <xsl:call-template name="eventSource"/>
          <EventDetail>
            <TypeId>
              <xsl:value-of select="if ($action) then $action else 'other'"/>
            </TypeId>
            <xsl:if test="$action">
              <Unknown>
                <Data Name="action" Value="{$action}"/>
              </Unknown>
            </xsl:if>
          </EventDetail>
        </Event>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- eventTime: shared by the rules 'logon', 'other' -->
  <xsl:template name="eventTime">
    <xsl:variable name="time" select="data[@name='time']/@value[normalize-space(.)]"/>
    <xsl:if test="$time">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="$time"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- eventSource: shared by the rules 'logon', 'other' -->
  <xsl:template name="eventSource">
    <EventSource>
      <System>
        <Name>Acme VPN</Name>
        <Environment>Dev</Environment>
      </System>
      <Generator>vpnd</Generator>
      <Device>
        <HostName>
          <xsl:variable name="host" select="data[@name='host']/@value[normalize-space(.)]"/>
          <xsl:value-of select="if ($host) then $host else 'unknown'"/>
        </HostName>
      </Device>
      <xsl:variable name="user" select="data[@name='user']/@value[normalize-space(.)]"/>
      <xsl:if test="$user">
        <User>
          <Id>
            <xsl:value-of select="$user"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
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
