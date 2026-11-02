<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="evt" select="data[@name='evt']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$evt = '4624'">
        <xsl:apply-templates select="." mode="event_type_4624"/>
      </xsl:when>
      <xsl:when test="$evt = '4625'">
        <xsl:apply-templates select="." mode="event_type_4625"/>
      </xsl:when>
      <xsl:when test="$evt = '4634'">
        <xsl:apply-templates select="." mode="event_type_4634"/>
      </xsl:when>
      <xsl:when test="$evt = '4648'">
        <xsl:apply-templates select="." mode="event_type_4648"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule '4624': records where evt = 4624. Writes an Authenticate event (TypeId Logon) from usr, res. -->
  <xsl:template match="node()" mode="event_type_4624">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <Description>An account logged on</Description>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <Outcome>
            <Success>true</Success>
          </Outcome>
          <xsl:sequence select="mcp:data('res', data[@name='res']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule '4625': records where evt = 4625. Writes an Authenticate event (TypeId Logon failed) from usr, res. -->
  <xsl:template match="node()" mode="event_type_4625">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Logon failed</TypeId>
        <Description>An account failed to log on</Description>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <Outcome>
            <Success>false</Success>
          </Outcome>
          <xsl:sequence select="mcp:data('res', data[@name='res']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule '4634': records where evt = 4634. Writes an Authenticate event (TypeId Logoff) from usr, res. -->
  <xsl:template match="node()" mode="event_type_4634">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Logoff</TypeId>
        <Description>An account logged off</Description>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:sequence select="mcp:data('res', data[@name='res']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule '4648': records where evt = 4648. Writes an Authenticate event (TypeId Explicit logon) from usr, res. -->
  <xsl:template match="node()" mode="event_type_4648">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Explicit logon</TypeId>
        <Description>A logon with explicit credentials</Description>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:sequence select="mcp:data('res', data[@name='res']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId Other) from evt, usr, res. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Other</TypeId>
        <xsl:if test="normalize-space(data[@name='evt']/@value) or normalize-space(data[@name='usr']/@value) or normalize-space(data[@name='res']/@value)">
          <Unknown>
            <xsl:sequence select="mcp:data('evt', data[@name='evt']/@value)"/>
            <xsl:sequence select="mcp:data('usr', data[@name='usr']/@value)"/>
            <xsl:sequence select="mcp:data('res', data[@name='res']/@value)"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules '4624', '4625', '4634', '4648', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='ts']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='ts']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules '4624', '4625', '4634', '4648', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Acme</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>Acme directory</Generator>
      <xsl:if test="normalize-space(data[@name='host']/@value)">
        <Device>
          <HostName>
            <xsl:value-of select="data[@name='host']/@value"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="normalize-space(data[@name='src']/@value)">
        <Client>
          <IPAddress>
            <xsl:value-of select="data[@name='src']/@value"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="normalize-space(data[@name='usr']/@value)">
        <User>
          <Id>
            <xsl:value-of select="data[@name='usr']/@value"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- user: shared by the rules '4624', '4625', '4634', '4648' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="normalize-space(data[@name='usr']/@value)">
      <User>
        <Id>
          <xsl:value-of select="data[@name='usr']/@value"/>
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
