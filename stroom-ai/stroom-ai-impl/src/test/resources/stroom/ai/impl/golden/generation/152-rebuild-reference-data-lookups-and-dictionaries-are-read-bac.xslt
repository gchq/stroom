<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:map="http://www.w3.org/2005/xpath-functions/map" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs map mcp">
  <xsl:variable name="host_names_map" select="map:merge(for $line in tokenize(stroom:dictionary('Host names'), '\r?\n')[contains(., '=')] return map{normalize-space(substring-before($line, '=')): normalize-space(substring-after($line, '='))})"/>
  <xsl:variable name="vip_users_list" select="tokenize(stroom:dictionary('VIP users'), '\r?\n') ! normalize-space(.)"/>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="data[@name='user']/@value = $vip_users_list">
        <xsl:apply-templates select="." mode="event_type_vip"/>
        <xsl:sequence select="stroom:log('INFO', concat('Kept as Unknown by rule ''vip'': record ', stroom:record-no(), ' (', string-join((concat('user=', string((normalize-space(data[@name='user']/@value))[1]))), ' | '), ')'))"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'vip': records where user in the dictionary VIP users. Writes an Unknown event (TypeId vip) from user.
       Unknown on purpose: a VIP list match has no action of its own. -->
  <xsl:template match="node()" mode="event_type_vip">
    <!-- vip: EventDetail/Unknown on purpose: a VIP list match has no action of its own -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>vip</TypeId>
        <xsl:apply-templates select="." mode="unknown"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId x) from user. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>x</TypeId>
        <xsl:apply-templates select="." mode="unknown"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'vip', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='time']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="data[@name='time']/@value"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'vip', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>A</Name>
        <Environment>D</Environment>
      </System>
      <Generator>g</Generator>
      <xsl:variable name="hostname" select="((data[@name='hostname']/@value, data[@name='host']/@value)[normalize-space(.)][1])[normalize-space(.)]"/>
      <xsl:variable name="hostname_host_names" select="($host_names_map?(string(((data[@name='hostname']/@value, data[@name='host']/@value)[normalize-space(.)][1])[1])))[normalize-space(.)]"/>
      <xsl:if test="exists($hostname_host_names) or exists($hostname)">
        <Device>
          <xsl:if test="exists($hostname_host_names)">
            <Name>
              <xsl:value-of select="$hostname_host_names"/>
            </Name>
          </xsl:if>
          <xsl:if test="exists($hostname)">
            <HostName>
              <xsl:value-of select="lower-case($hostname[1])"/>
            </HostName>
          </xsl:if>
        </Device>
      </xsl:if>
      <xsl:variable name="user" select="data[@name='user']/@value[normalize-space(.)]"/>
      <xsl:if test="$user">
        <User>
          <Id>
            <xsl:value-of select="replace(replace($user[1], '^[^\\]*\\', ''), '@.*$', '')"/>
          </Id>
          <Domain>
            <xsl:value-of select="replace($user[1], '^(?:([^\\]*)\\.*|[^@]*@(.*))$', '$1$2')"/>
          </Domain>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- unknown: shared by the rules 'vip', 'other' -->
  <xsl:template match="node()" mode="unknown">
    <xsl:if test="normalize-space(data[@name='user']/@value)">
      <Unknown>
        <Data Name="u" Value="{data[@name='user']/@value}"/>
      </Unknown>
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
