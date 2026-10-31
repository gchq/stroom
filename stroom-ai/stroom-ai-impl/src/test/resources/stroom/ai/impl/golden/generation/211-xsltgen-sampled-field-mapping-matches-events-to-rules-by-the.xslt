<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="data[@name='action']/@value = 'login'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="data[@name='action']/@value = 'logout'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where action = login. Writes an Authenticate event (TypeId Logon) from user, result, sid. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:processing-instruction name="stroom-mcp-rule">logon</xsl:processing-instruction>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <xsl:if test="normalize-space(data[@name='result']/@value)">
          <Description>
            <xsl:value-of select="data[@name='result']/@value"/>
          </Description>
        </xsl:if>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:apply-templates select="." mode="outcome"/>
          <xsl:sequence select="mcp:data('session', data[@name='sid']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where action = logout. Writes an Authenticate event (TypeId Logon) from user, result,
       sid, ip. -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:processing-instruction name="stroom-mcp-rule">logoff</xsl:processing-instruction>
      <xsl:apply-templates select="." mode="event_time"/>
      <EventSource>
        <xsl:apply-templates select="." mode="system"/>
        <Generator>vpnd</Generator>
        <xsl:apply-templates select="." mode="device"/>
        <xsl:if test="normalize-space(data[@name='ip']/@value)">
          <Client>
            <IPAddress>
              <xsl:value-of select="data[@name='ip']/@value"/>
            </IPAddress>
          </Client>
        </xsl:if>
        <xsl:apply-templates select="." mode="user"/>
      </EventSource>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:apply-templates select="." mode="outcome"/>
          <xsl:sequence select="mcp:data('session', data[@name='sid']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from action) from action. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:processing-instruction name="stroom-mcp-rule">other</xsl:processing-instruction>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
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
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='time']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="data[@name='time']/@value"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <xsl:apply-templates select="." mode="system"/>
      <Generator>vpnd</Generator>
      <xsl:apply-templates select="." mode="device"/>
      <xsl:apply-templates select="." mode="user"/>
    </EventSource>
  </xsl:template>
  <!-- system: shared by the rules 'logon', 'logoff', 'other' -->
  <xsl:template match="node()" mode="system">
    <System>
      <Name>Acme VPN</Name>
      <Environment>Dev</Environment>
    </System>
  </xsl:template>
  <!-- device: shared by the rules 'logon', 'logoff', 'other' -->
  <xsl:template match="node()" mode="device">
    <Device>
      <HostName>
        <xsl:value-of select="if (normalize-space(data[@name='host']/@value)) then data[@name='host']/@value else 'unknown'"/>
      </HostName>
    </Device>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff', 'other' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="normalize-space(data[@name='user']/@value)">
      <User>
        <Id>
          <xsl:value-of select="data[@name='user']/@value"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- authenticate_user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="authenticate_user">
    <xsl:if test="normalize-space(data[@name='user']/@value)">
      <User>
        <Id>
          <xsl:value-of select="data[@name='user']/@value"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- outcome: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:if test="data[@name='result']/@value = ('ok', 'fail')">
      <Outcome>
        <Success>
          <xsl:value-of select="if (data[@name='result']/@value = 'ok') then 'true' else 'false'"/>
        </Success>
      </Outcome>
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
