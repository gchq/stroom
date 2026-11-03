<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="logons">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="logon" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="action = 'login'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where action = login. Writes an Authenticate event (TypeId Logon) from user, result, sid. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="(user)[normalize-space(.)]">
            <User>
              <Id>
                <xsl:value-of select="(user)[1]"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="(result)[1] = ('ok', 'fail')">
            <Outcome>
              <Success>
                <xsl:value-of select="if ((result)[1] = 'ok') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
          <xsl:sequence select="mcp:data('session', sid)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from action) from action. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:variable name="action" select="(action)[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action[1] else 'other'"/>
        </TypeId>
        <xsl:if test="$action">
          <Unknown>
            <Data Name="action" Value="{$action[1]}"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="(time)[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="(time)[1]"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Acme VPN</Name>
        <Environment>Dev</Environment>
      </System>
      <Generator>vpnd</Generator>
      <Device>
        <HostName>
          <xsl:value-of select="if ((host)[normalize-space(.)]) then (host)[1] else 'unknown'"/>
        </HostName>
      </Device>
      <xsl:variable name="user_id" select="(upper-case(//data[@name='user']/@value))[normalize-space(.)]"/>
      <xsl:if test="exists($user_id)">
        <User>
          <Id>
            <xsl:if test="count($user_id) gt 1">
              <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($user_id), ') for ', 'EventSource/User/Id, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
            </xsl:if>
            <xsl:value-of select="$user_id[1]"/>
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
