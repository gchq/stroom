<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn mcp">
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
  </xsl:template>
  <!-- Rule 'deny': records where action = deny. Writes a Network/Deny event (TypeId deny) from srcip, dstip,
       dstintfrole, proto, logid, policyid, service, srcintfrole (extracted from msg). -->
  <xsl:template match="node()" mode="event_type_deny">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>deny</TypeId>
        <xsl:variable name="msg" select="data[@name='msg']/@value[normalize-space(.)]"/>
        <xsl:variable name="srcintfrole" select="mcp:quoted_value($msg, 'srcintfrole')[normalize-space(.)]"/>
        <xsl:variable name="service" select="mcp:quoted_value($msg, 'service')[normalize-space(.)]"/>
        <xsl:variable name="policyid_parts" select="analyze-string(string($msg[1]), '(?:^|\s)policyid=(\S+)')"/>
        <xsl:variable name="policyid" select="$policyid_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
        <xsl:variable name="logid" select="mcp:quoted_value($msg, 'logid')[normalize-space(.)]"/>
        <xsl:variable name="proto_parts" select="analyze-string(string($msg[1]), '(?:^|\s)proto=(\S+)')"/>
        <xsl:variable name="proto" select="$proto_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
        <xsl:variable name="dstintfrole" select="mcp:quoted_value($msg, 'dstintfrole')[normalize-space(.)]"/>
        <xsl:if test="normalize-space(mcp:quoted_value($msg, 'srcip')) or normalize-space(mcp:quoted_value($msg, 'dstip')) or $dstintfrole or $proto or $logid or $policyid or $service or $srcintfrole">
          <Network>
            <Deny>
              <xsl:apply-templates select="." mode="source"/>
              <xsl:apply-templates select="." mode="destination"/>
              <xsl:if test="$dstintfrole">
                <Data Name="dstintfrole" Value="{$dstintfrole}"/>
              </xsl:if>
              <xsl:if test="$proto">
                <Data Name="proto" Value="{$proto}"/>
              </xsl:if>
              <xsl:if test="$logid">
                <Data Name="logid" Value="{$logid}"/>
              </xsl:if>
              <xsl:if test="$policyid">
                <Data Name="policyid" Value="{$policyid}"/>
              </xsl:if>
              <xsl:if test="$service">
                <Data Name="service" Value="{$service}"/>
              </xsl:if>
              <xsl:if test="$srcintfrole">
                <Data Name="srcintfrole" Value="{$srcintfrole}"/>
              </xsl:if>
            </Deny>
          </Network>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'permit': records where action = accept. Writes a Network/Permit event (TypeId permit) from srcip, dstip,
       dstintfrole, proto, logid, policyid, service, srcintfrole (extracted from msg). -->
  <xsl:template match="node()" mode="event_type_permit">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>permit</TypeId>
        <xsl:variable name="msg" select="data[@name='msg']/@value[normalize-space(.)]"/>
        <xsl:variable name="srcintfrole" select="mcp:quoted_value($msg, 'srcintfrole')[normalize-space(.)]"/>
        <xsl:variable name="service" select="mcp:quoted_value($msg, 'service')[normalize-space(.)]"/>
        <xsl:variable name="policyid_parts" select="analyze-string(string($msg[1]), '(?:^|\s)policyid=(\S+)')"/>
        <xsl:variable name="policyid" select="$policyid_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
        <xsl:variable name="logid" select="mcp:quoted_value($msg, 'logid')[normalize-space(.)]"/>
        <xsl:variable name="proto_parts" select="analyze-string(string($msg[1]), '(?:^|\s)proto=(\S+)')"/>
        <xsl:variable name="proto" select="$proto_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
        <xsl:variable name="dstintfrole" select="mcp:quoted_value($msg, 'dstintfrole')[normalize-space(.)]"/>
        <xsl:if test="normalize-space(mcp:quoted_value($msg, 'srcip')) or normalize-space(mcp:quoted_value($msg, 'dstip')) or $dstintfrole or $proto or $logid or $policyid or $service or $srcintfrole">
          <Network>
            <Permit>
              <xsl:apply-templates select="." mode="source"/>
              <xsl:apply-templates select="." mode="destination"/>
              <xsl:if test="$dstintfrole">
                <Data Name="dstintfrole" Value="{$dstintfrole}"/>
              </xsl:if>
              <xsl:if test="$proto">
                <Data Name="proto" Value="{$proto}"/>
              </xsl:if>
              <xsl:if test="$logid">
                <Data Name="logid" Value="{$logid}"/>
              </xsl:if>
              <xsl:if test="$policyid">
                <Data Name="policyid" Value="{$policyid}"/>
              </xsl:if>
              <xsl:if test="$service">
                <Data Name="service" Value="{$service}"/>
              </xsl:if>
              <xsl:if test="$srcintfrole">
                <Data Name="srcintfrole" Value="{$srcintfrole}"/>
              </xsl:if>
            </Permit>
          </Network>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'deny', 'permit' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='time']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="data[@name='time']/@value"/>
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
          <xsl:value-of select="if (normalize-space(data[@name='host']/@value)) then data[@name='host']/@value else 'unknown'"/>
        </HostName>
      </Device>
      <xsl:if test="normalize-space(mcp:quoted_value(data[@name='msg']/@value, 'user'))">
        <User>
          <Id>
            <xsl:value-of select="mcp:quoted_value(data[@name='msg']/@value, 'user')"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- source: shared by the rules 'deny', 'permit' -->
  <xsl:template match="node()" mode="source">
    <xsl:if test="normalize-space(mcp:quoted_value(data[@name='msg']/@value, 'srcip'))">
      <Source>
        <Device>
          <IPAddress>
            <xsl:value-of select="mcp:quoted_value(data[@name='msg']/@value, 'srcip')"/>
          </IPAddress>
        </Device>
      </Source>
    </xsl:if>
  </xsl:template>
  <!-- destination: shared by the rules 'deny', 'permit' -->
  <xsl:template match="node()" mode="destination">
    <xsl:if test="normalize-space(mcp:quoted_value(data[@name='msg']/@value, 'dstip'))">
      <Destination>
        <Device>
          <IPAddress>
            <xsl:value-of select="mcp:quoted_value(data[@name='msg']/@value, 'dstip')"/>
          </IPAddress>
        </Device>
      </Destination>
    </xsl:if>
  </xsl:template>
  <xsl:function name="mcp:quoted_value" as="xs:string?">
    <!-- The value of a key in the text: the regex (?:^|\s)<key>="([^"]*)", its first group -->
    <xsl:param name="text" as="item()*"/>
    <xsl:param name="key" as="xs:string"/>
    <xsl:sequence select="for $g in (analyze-string(string($text[1]), concat('(?:^|\s)', $key, '=&quot;([^&quot;]*)&quot;'))//fn:group[@nr=1])[1] return string($g)"/>
  </xsl:function>
</xsl:stylesheet>
