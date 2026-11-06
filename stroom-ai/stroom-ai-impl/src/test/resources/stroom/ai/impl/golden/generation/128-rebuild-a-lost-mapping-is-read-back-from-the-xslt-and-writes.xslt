<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:variable name="msg_1_to_transport_protocol" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'6'" select="'TCP'"/>
      <xsl:map-entry key="'17'" select="'UDP'"/>
    </xsl:map>
  </xsl:variable>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="data[@name='action']/@value = 'deny'">
        <xsl:apply-templates select="." mode="event_type_deny"/>
      </xsl:when>
      <xsl:when test="data[@name='action']/@value = 'accept'">
        <xsl:apply-templates select="." mode="event_type_permit"/>
      </xsl:when>
    </xsl:choose>
    <xsl:for-each select="data[@name][not(@name = ('action', 'host', 'msg', 'time'))][normalize-space(string(@value))]">
      <!-- A field no rule reads: logged once a stream (ignored_fields: left out on purpose) -->
      <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
      <xsl:if test="not(string(stroom:get($mcp_unread)))">
        <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
        <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  <!-- Rule 'deny': records where action = deny. Writes a Network/Deny event (TypeId deny) from srcip, msg_1, dstip,
       dstintfrole (extracted from msg). -->
  <xsl:template match="node()" mode="event_type_deny">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>deny</TypeId>
        <Network>
          <Deny>
            <xsl:apply-templates select="." mode="source"/>
            <xsl:apply-templates select="." mode="destination"/>
            <xsl:sequence select="mcp:data('dstintfrole', mcp:quoted_value(data[@name='msg']/@value, 'dstintfrole'))"/>
            <xsl:variable name="msg_1_parts" select="analyze-string(string((data[@name='msg']/@value)[1]), '(?:^|\s)proto=(\S+)')"/>
            <xsl:sequence select="mcp:data('proto', $msg_1_parts/fn:match//fn:group[@nr=1])"/>
          </Deny>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'permit': records where action = accept. Writes a Network/Permit event (TypeId permit) from srcip, msg_1,
       dstip, dstintfrole (extracted from msg). -->
  <xsl:template match="node()" mode="event_type_permit">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>permit</TypeId>
        <Network>
          <Permit>
            <xsl:apply-templates select="." mode="source"/>
            <xsl:apply-templates select="." mode="destination"/>
            <xsl:sequence select="mcp:data('dstintfrole', mcp:quoted_value(data[@name='msg']/@value, 'dstintfrole'))"/>
            <xsl:variable name="msg_1_parts" select="analyze-string(string((data[@name='msg']/@value)[1]), '(?:^|\s)proto=(\S+)')"/>
            <xsl:sequence select="mcp:data('proto', $msg_1_parts/fn:match//fn:group[@nr=1])"/>
          </Permit>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'deny', 'permit' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="(data[@name='time']/@value)[1]"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'deny', 'permit' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Acme VPN</Name>
        <Environment>Dev</Environment>
      </System>
      <Generator>vpnd</Generator>
      <Device>
        <HostName>
          <xsl:value-of select="if (data[@name='host']/@value[normalize-space(.)]) then (data[@name='host']/@value)[1] else 'unknown'"/>
        </HostName>
      </Device>
      <xsl:if test="normalize-space(mcp:quoted_value(data[@name='msg']/@value, 'user'))">
        <User>
          <Id>
            <xsl:value-of select="mcp:quoted_value(data[@name='msg']/@value, 'user')[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- source: shared by the rules 'deny', 'permit' -->
  <xsl:template match="node()" mode="source">
    <Source>
      <xsl:if test="normalize-space(mcp:quoted_value(data[@name='msg']/@value, 'srcip'))">
        <Device>
          <IPAddress>
            <xsl:value-of select="mcp:quoted_value(data[@name='msg']/@value, 'srcip')[1]"/>
          </IPAddress>
        </Device>
      </xsl:if>
      <TransportProtocol>
        <xsl:variable name="msg_1_parts" select="analyze-string(string((data[@name='msg']/@value)[1]), '(?:^|\s)proto=(\S+)')"/>
        <xsl:value-of select="($msg_1_to_transport_protocol?(($msg_1_parts/fn:match//fn:group[@nr=1])[1]), 'Other')[1]"/>
      </TransportProtocol>
    </Source>
  </xsl:template>
  <!-- destination: shared by the rules 'deny', 'permit' -->
  <xsl:template match="node()" mode="destination">
    <xsl:if test="normalize-space(mcp:quoted_value(data[@name='msg']/@value, 'dstip'))">
      <Destination>
        <Device>
          <IPAddress>
            <xsl:value-of select="mcp:quoted_value(data[@name='msg']/@value, 'dstip')[1]"/>
          </IPAddress>
        </Device>
      </Destination>
    </xsl:if>
  </xsl:template>
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value[1]}"/>
    </xsl:if>
  </xsl:function>
  <xsl:function name="mcp:quoted_value" as="xs:string?">
    <!-- The value of a key in the text: the regex (?:^|\s)<key>="([^"]*)", its first group -->
    <xsl:param name="text" as="item()*"/>
    <xsl:param name="key" as="xs:string"/>
    <xsl:sequence select="for $g in (analyze-string(string($text[1]), concat('(?:^|\s)', $key, '=&quot;([^&quot;]*)&quot;'))//fn:group[@nr=1])[1] return string($g)"/>
  </xsl:function>
</xsl:stylesheet>
