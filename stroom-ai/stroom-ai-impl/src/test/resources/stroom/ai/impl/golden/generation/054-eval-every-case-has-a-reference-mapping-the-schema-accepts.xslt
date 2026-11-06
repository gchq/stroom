<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:apply-templates select="." mode="event_type_web_request"/>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('action', 'app', 'category', 'dst', 'host', 'referer', 'src', 'time', 'url', 'user'))][normalize-space(string(@value))][not(normalize-space(@value) = ('-'))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'web request': every record. Writes a Network/Open event (TypeId from action) from src, dst, url,
       category, referer, action. -->
  <xsl:template match="node()" mode="event_type_web_request">
    <Event>
      <xsl:if test="data[@name='time']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date((data[@name='time']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
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
            <xsl:value-of select="(data[@name='app']/@value)[1]"/>
          </Generator>
        </xsl:if>
        <xsl:if test="data[@name='host']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
          <Device>
            <HostName>
              <xsl:value-of select="(data[@name='host']/@value)[1]"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="data[@name='user']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
          <User>
            <Id>
              <xsl:value-of select="(data[@name='user']/@value)[1]"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action[1] else 'web request'"/>
        </TypeId>
        <xsl:variable name="category" select="data[@name='category']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <xsl:variable name="url" select="data[@name='url']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <xsl:variable name="dst" select="data[@name='dst']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <xsl:variable name="referer" select="data[@name='referer']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <xsl:variable name="src" select="data[@name='src']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <xsl:if test="$src or $referer or $dst or $url or $category or $action[1] = ('allow', 'block')">
          <Network>
            <Open>
              <xsl:if test="$src or $referer">
                <Source>
                  <xsl:if test="$src">
                    <Device>
                      <IPAddress>
                        <xsl:value-of select="$src[1]"/>
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
                        <xsl:value-of select="$dst[1]"/>
                      </IPAddress>
                    </Device>
                  </xsl:if>
                  <xsl:sequence select="mcp:data('url', $url)"/>
                  <xsl:sequence select="mcp:data('category', $category)"/>
                </Destination>
              </xsl:if>
              <xsl:if test="$action[1] = ('allow', 'block')">
                <Outcome>
                  <Permitted>
                    <xsl:value-of select="if ($action[1] = 'allow') then 'true' else 'false'"/>
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
      <Data Name="{$name}" Value="{$value[1]}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
