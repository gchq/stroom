<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="src_parts" select="analyze-string(string(*[@key='message'][1]), 'src=(\S+)')"/>
    <xsl:choose>
      <xsl:when test="normalize-space($src_parts/fn:match//fn:group[@nr=1])">
        <xsl:apply-templates select="." mode="event_type_connection"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
        <xsl:sequence select="stroom:log('INFO', concat('Kept as Unknown by rule ''other'': record ', stroom:record-no(), ' (', string-join((concat('src=', string((normalize-space($src_parts/fn:match//fn:group[@nr=1]))[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'connection': records where src present. Writes a Network/Open event (TypeId connection) from src, dst
       (extracted from message). -->
  <xsl:template match="node()" mode="event_type_connection">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>connection</TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:variable name="message" select="*[@key='message'][normalize-space(.)]"/>
        <xsl:variable name="dst_parts" select="analyze-string(string($message[1]), 'dst=(\S+)')"/>
        <xsl:variable name="dst" select="$dst_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
        <xsl:variable name="src_parts" select="analyze-string(string($message[1]), 'src=(\S+)')"/>
        <xsl:variable name="src" select="$src_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
        <xsl:if test="$src or $dst">
          <Network>
            <Open>
              <xsl:if test="$src">
                <Source>
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$src"/>
                    </IPAddress>
                  </Device>
                </Source>
              </xsl:if>
              <xsl:if test="$dst">
                <Destination>
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$dst"/>
                    </IPAddress>
                  </Device>
                </Destination>
              </xsl:if>
            </Open>
          </Network>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId other) from message. Unknown on
       purpose: lines with no addresses carry no activity. -->
  <xsl:template match="node()" mode="event_type_other">
    <!-- other: EventDetail/Unknown on purpose: lines with no addresses carry no activity -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>other</TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:if test="normalize-space(*[@key='message'])">
          <Unknown>
            <Data Name="message" Value="{*[@key='message']}"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'connection', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(*[@key='ts'])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="*[@key='ts']"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'connection', 'other' -->
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
      <xsl:if test="normalize-space(*[@key='user'])">
        <User>
          <Id>
            <xsl:value-of select="*[@key='user']"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- description: shared by the rules 'connection', 'other' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="normalize-space(*[@key='desc'])">
      <Description>
        <xsl:value-of select="*[@key='desc']"/>
      </Description>
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
