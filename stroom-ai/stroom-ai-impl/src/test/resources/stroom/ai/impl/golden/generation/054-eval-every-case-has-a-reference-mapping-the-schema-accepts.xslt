<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_web_request"/>
  </xsl:template>
  <!-- Rule 'web request': every record. Writes a Network/Open event (TypeId from action) from src, dst, url,
       category, referer, action. -->
  <xsl:template match="node()" mode="event_type_web_request">
    <Event>
      <xsl:if test="data[@name='time']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>Web Filter</Name>
          <Environment>Eval</Environment>
        </System>
        <xsl:if test="data[@name='app']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
          <Generator>
            <xsl:value-of select="data[@name='app']/@value"/>
          </Generator>
        </xsl:if>
        <xsl:if test="data[@name='host']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
          <Device>
            <HostName>
              <xsl:value-of select="data[@name='host']/@value"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="data[@name='user']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
          <User>
            <Id>
              <xsl:value-of select="data[@name='user']/@value"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action else 'web request'"/>
        </TypeId>
        <xsl:variable name="category" select="data[@name='category']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <xsl:variable name="url" select="data[@name='url']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <xsl:variable name="dst" select="data[@name='dst']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <xsl:variable name="referer" select="data[@name='referer']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <xsl:variable name="src" select="data[@name='src']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <xsl:if test="$src or $referer or $dst or $url or $category or $action = ('allow', 'block')">
          <Network>
            <Open>
              <xsl:if test="$src or $referer">
                <Source>
                  <xsl:if test="$src">
                    <Device>
                      <IPAddress>
                        <xsl:value-of select="$src"/>
                      </IPAddress>
                    </Device>
                  </xsl:if>
                  <xsl:sequence select="mcp:data('referer', $referer)"/>
                </Source>
              </xsl:if>
              <xsl:if test="$dst or $url or $category">
                <Destination>
                  <xsl:if test="$dst">
                    <Device>
                      <IPAddress>
                        <xsl:value-of select="$dst"/>
                      </IPAddress>
                    </Device>
                  </xsl:if>
                  <xsl:sequence select="mcp:data('url', $url)"/>
                  <xsl:sequence select="mcp:data('category', $category)"/>
                </Destination>
              </xsl:if>
              <xsl:if test="$action = ('allow', 'block')">
                <Outcome>
                  <Permitted>
                    <xsl:value-of select="if ($action = 'allow') then 'true' else 'false'"/>
                  </Permitted>
                </Outcome>
              </xsl:if>
            </Open>
          </Network>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank, nor -) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)][not(normalize-space(.) = ('-'))])">
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
