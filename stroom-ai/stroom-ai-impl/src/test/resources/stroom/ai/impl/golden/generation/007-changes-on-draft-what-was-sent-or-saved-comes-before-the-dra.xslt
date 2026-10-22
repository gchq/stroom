<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="mcp:quoted_value(*[@key='body'], 'type') = 'traffic' and mcp:quoted_value(*[@key='body'], 'action') = 'accept'">
        <xsl:apply-templates select="." mode="event_type_traffic_permitted"/>
      </xsl:when>
      <xsl:when test="mcp:quoted_value(*[@key='body'], 'type') = 'event' and mcp:quoted_value(*[@key='body'], 'action') = 'accept'">
        <xsl:apply-templates select="." mode="event_type_event_permitted"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'traffic_permitted': records where type = traffic and action = accept. Writes a Network/Permit event
       (TypeId from type) from srcip, dstip, logid (extracted from body). -->
  <xsl:template match="node()" mode="event_type_traffic_permitted">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:apply-templates select="." mode="event_detail"/>
    </Event>
  </xsl:template>
  <!-- Rule 'event_permitted': records where type = event and action = accept. Writes a Network/Permit event (TypeId
       from type) from srcip, dstip, logid (extracted from body). -->
  <xsl:template match="node()" mode="event_type_event_permitted">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:apply-templates select="." mode="event_detail"/>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from type) from logid, action
       (extracted from body). -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:variable name="body" select="*[@key='body'][normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if (normalize-space(mcp:quoted_value($body, 'type'))) then mcp:quoted_value($body, 'type') else 'other'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:if test="normalize-space(mcp:quoted_value($body, 'logid')) or normalize-space(mcp:quoted_value($body, 'action'))">
          <Unknown>
            <xsl:sequence select="mcp:data('logid', mcp:quoted_value($body, 'logid'))"/>
            <xsl:sequence select="mcp:data('action', mcp:quoted_value($body, 'action'))"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'traffic_permitted', 'event_permitted', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(*[@key='time'])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(*[@key='time'][1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'traffic_permitted', 'event_permitted', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>TODO system name</Name>
        <Environment>TODO e.g. Prod</Environment>
      </System>
      <Generator>TODO generator</Generator>
      <xsl:if test="normalize-space(*[@key='host'])">
        <Device>
          <HostName>
            <xsl:value-of select="*[@key='host']"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="body" select="*[@key='body'][normalize-space(.)]"/>
      <xsl:variable name="srcip_parts" select="analyze-string(string($body[1]), '(?:^|[\s,;])srcip=([^\s,;&quot;]*)')"/>
      <xsl:if test="normalize-space($srcip_parts/fn:match//fn:group[@nr=1])">
        <Client>
          <IPAddress>
            <xsl:value-of select="$srcip_parts/fn:match//fn:group[@nr=1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:variable name="dstip_parts" select="analyze-string(string($body[1]), '(?:^|[\s,;])dstip=([^\s,;&quot;]*)')"/>
      <xsl:if test="normalize-space($dstip_parts/fn:match//fn:group[@nr=1])">
        <Server>
          <IPAddress>
            <xsl:value-of select="$dstip_parts/fn:match//fn:group[@nr=1]"/>
          </IPAddress>
        </Server>
      </xsl:if>
      <xsl:if test="normalize-space(mcp:quoted_value($body, 'user'))">
        <User>
          <Id>
            <xsl:value-of select="mcp:quoted_value($body, 'user')"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- event_detail: shared by the rules 'traffic_permitted', 'event_permitted' -->
  <xsl:template match="node()" mode="event_detail">
    <xsl:variable name="body" select="*[@key='body'][normalize-space(.)]"/>
    <xsl:variable name="logid" select="mcp:quoted_value($body, 'logid')[normalize-space(.)]"/>
    <xsl:variable name="dstip_parts" select="analyze-string(string($body[1]), '(?:^|[\s,;])dstip=([^\s,;&quot;]*)')"/>
    <xsl:variable name="dstip" select="$dstip_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
    <xsl:variable name="srcip_parts" select="analyze-string(string($body[1]), '(?:^|[\s,;])srcip=([^\s,;&quot;]*)')"/>
    <xsl:variable name="srcip" select="$srcip_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
    <xsl:variable name="type" select="mcp:quoted_value($body, 'type')[normalize-space(.)]"/>
    <xsl:if test="normalize-space(mcp:quoted_value($body, 'msg')) or $type or $srcip or $dstip or $logid">
      <EventDetail>
        <xsl:if test="$type">
          <TypeId>
            <xsl:value-of select="$type"/>
          </TypeId>
        </xsl:if>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:if test="$srcip or $dstip or $logid">
          <Network>
            <Permit>
              <xsl:if test="$srcip">
                <Source>
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$srcip"/>
                    </IPAddress>
                  </Device>
                </Source>
              </xsl:if>
              <xsl:if test="$dstip">
                <Destination>
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$dstip"/>
                    </IPAddress>
                  </Device>
                </Destination>
              </xsl:if>
              <xsl:sequence select="mcp:data('logid', $logid)"/>
            </Permit>
          </Network>
        </xsl:if>
      </EventDetail>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'traffic_permitted', 'event_permitted', 'other' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="normalize-space(mcp:quoted_value(*[@key='body'], 'msg'))">
      <Description>
        <xsl:value-of select="mcp:quoted_value(*[@key='body'], 'msg')"/>
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
  <xsl:function name="mcp:quoted_value" as="xs:string?">
    <!-- The value of a key in the text: the regex (?:^|[\s,;])<key>="([^"]*)", its first group -->
    <xsl:param name="text" as="item()*"/>
    <xsl:param name="key" as="xs:string"/>
    <xsl:sequence select="for $g in (analyze-string(string($text[1]), concat('(?:^|[\s,;])', $key, '=&quot;([^&quot;]*)&quot;'))//fn:group[@nr=1])[1] return string($g)"/>
  </xsl:function>
</xsl:stylesheet>
