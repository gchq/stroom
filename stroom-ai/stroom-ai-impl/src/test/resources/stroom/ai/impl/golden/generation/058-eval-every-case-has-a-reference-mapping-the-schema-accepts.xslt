<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_request"/>
  </xsl:template>
  <!-- Rule 'request': every record. Writes a Network/Open event (TypeId from action) from client_ip, url, category,
       method, status, bytes, action. -->
  <xsl:template match="node()" mode="event_type_request">
    <Event>
      <xsl:if test="normalize-space(data[@name='time']/@value)">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="client_ip" select="data[@name='client_ip']/@value[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>Web Proxy</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>proxy</Generator>
        <Device>
          <HostName>proxy01.example.org</HostName>
        </Device>
        <xsl:if test="$client_ip">
          <Client>
            <IPAddress>
              <xsl:value-of select="$client_ip"/>
            </IPAddress>
          </Client>
        </xsl:if>
        <xsl:if test="normalize-space(data[@name='user']/@value)">
          <User>
            <Id>
              <xsl:value-of select="data[@name='user']/@value"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action else 'request'"/>
        </TypeId>
        <xsl:variable name="category" select="data[@name='category']/@value[normalize-space(.)]"/>
        <xsl:variable name="url" select="data[@name='url']/@value[normalize-space(.)]"/>
        <xsl:if test="$client_ip or $url or $category or $action = ('allowed', 'blocked') or normalize-space(data[@name='method']/@value) or normalize-space(data[@name='status']/@value) or normalize-space(data[@name='bytes']/@value)">
          <Network>
            <Open>
              <xsl:if test="$client_ip">
                <Source>
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$client_ip"/>
                    </IPAddress>
                  </Device>
                </Source>
              </xsl:if>
              <xsl:if test="$url or $category">
                <Destination>
                  <xsl:sequence select="mcp:data('url', $url)"/>
                  <xsl:sequence select="mcp:data('category', $category)"/>
                </Destination>
              </xsl:if>
              <xsl:if test="$action = ('allowed', 'blocked')">
                <Outcome>
                  <Permitted>
                    <xsl:value-of select="if ($action = 'allowed') then 'true' else 'false'"/>
                  </Permitted>
                </Outcome>
              </xsl:if>
              <xsl:sequence select="mcp:data('method', data[@name='method']/@value)"/>
              <xsl:sequence select="mcp:data('status', data[@name='status']/@value)"/>
              <xsl:sequence select="mcp:data('bytes', data[@name='bytes']/@value)"/>
            </Open>
          </Network>
        </xsl:if>
      </EventDetail>
    </Event>
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
