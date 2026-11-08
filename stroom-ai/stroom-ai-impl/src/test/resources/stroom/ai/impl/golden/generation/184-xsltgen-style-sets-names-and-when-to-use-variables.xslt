<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:variable name="resultToSuccess" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'ok'" select="'true'"/>
      <xsl:map-entry key="'fail'" select="'false'"/>
    </xsl:map>
  </xsl:variable>
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <!-- Fields of the record no rule reads are logged (mcp_unread_fields) -->
    <xsl:call-template name="mcp_unread_fields"/>
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
                    <xsl:value-of select="$user[1]"/>
                  </Id>
                </User>
              </xsl:if>
              <xsl:variable name="result" select="data[@name='result']/@value[normalize-space(.)]"/>
              <xsl:if test="exists($resultToSuccess?($result[1]))">
                <Outcome>
                  <Success>
                    <xsl:value-of select="$resultToSuccess?($result[1])[1]"/>
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
              <xsl:value-of select="if ($action) then $action[1] else 'other'"/>
            </TypeId>
            <Unknown>
              <Data Name="action" Value="{$action[1]}"/>
            </Unknown>
          </EventDetail>
        </Event>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('action', 'host', 'result', 'sid', 'time', 'user'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- eventTime: shared by the rules 'logon', 'other' -->
  <xsl:template name="eventTime">
    <xsl:variable name="time" select="data[@name='time']/@value[normalize-space(.)]"/>
    <xsl:if test="$time">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="$time[1]"/>
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
          <xsl:value-of select="if ($host) then $host[1] else 'unknown'"/>
        </HostName>
      </Device>
      <xsl:variable name="user" select="data[@name='user']/@value[normalize-space(.)]"/>
      <xsl:if test="$user">
        <User>
          <Id>
            <xsl:value-of select="$user[1]"/>
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
      <Data Name="{$name}" Value="{$value[1]}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
