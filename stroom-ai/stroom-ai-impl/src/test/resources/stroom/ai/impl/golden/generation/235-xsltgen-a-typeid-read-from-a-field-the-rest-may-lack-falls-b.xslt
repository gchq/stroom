<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="/">
    <Events Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="*[@key='kind'] = 'LOGON'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
        <xsl:sequence select="stroom:log('INFO', concat('Kept as Unknown by rule ''other'': record ', $record-no, ' (', string-join((concat('kind=', string((normalize-space(*[@key='kind']))[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where kind = LOGON. Writes an Authenticate event (TypeId from kind) from user. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:if test="normalize-space(*[@key='kind'])">
          <TypeId>
            <xsl:value-of select="*[@key='kind']"/>
          </TypeId>
        </xsl:if>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="normalize-space(*[@key='user'])">
            <User>
              <Id>
                <xsl:value-of select="*[@key='user']"/>
              </Id>
            </User>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from kind) from msg. Unknown on
       purpose: no action element describes them. -->
  <xsl:template match="node()" mode="event_type_other">
    <!-- other: EventDetail/Unknown on purpose: no action element describes them -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(*[@key='kind'])) then *[@key='kind'] else 'other'"/>
        </TypeId>
        <xsl:if test="normalize-space(*[@key='msg'])">
          <Unknown>
            <Data Name="msg" Value="{*[@key='msg']}"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(*[@key='time'])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="*[@key='time']"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Acme</Name>
        <Environment>Prod</Environment>
      </System>
      <Generator>Acme</Generator>
      <xsl:if test="normalize-space(*[@key='host'])">
        <Device>
          <HostName>
            <xsl:value-of select="*[@key='host']"/>
          </HostName>
        </Device>
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
