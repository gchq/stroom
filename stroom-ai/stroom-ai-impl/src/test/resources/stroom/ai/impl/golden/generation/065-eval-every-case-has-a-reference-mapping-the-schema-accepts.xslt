<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_request"/>
  </xsl:template>
  <!-- Rule 'request': every record. Writes a Network/Open event (TypeId from s-action) from c-ip, cs-host,
       cs-uri-port, cs-uri-path, cs-categories, cs-method, sc-status, sc-bytes, x-virus-id, sc-filter-result. -->
  <xsl:template match="node()" mode="event_type_request">
    <Event>
      <xsl:if test="exists(concat(data[@name='date']/@value, 'T', data[@name='time']/@value, 'Z')[normalize-space(.)])">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(concat(data[@name='date']/@value, 'T', data[@name='time']/@value, 'Z')[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="c_ip" select="data[@name='c-ip']/@value[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>Blue Coat ProxySG</Name>
          <Environment>Eval</Environment>
        </System>
        <xsl:if test="normalize-space(data[@name='#Software']/@value)">
          <Generator>
            <xsl:value-of select="data[@name='#Software']/@value"/>
          </Generator>
        </xsl:if>
        <xsl:variable name="proxy_name_parts" select="analyze-string(string(data[@name='#Remark']/@value[1]), '^\S+ &quot;([^&quot;]*)&quot; &quot;([^&quot;]*)&quot;.*$')"/>
        <xsl:variable name="proxy_ip" select="$proxy_name_parts/fn:match//fn:group[@nr=2][normalize-space(.)]"/>
        <xsl:variable name="proxy_name" select="$proxy_name_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
        <xsl:if test="$proxy_name or $proxy_ip">
          <Device>
            <xsl:if test="$proxy_name">
              <HostName>
                <xsl:value-of select="$proxy_name"/>
              </HostName>
            </xsl:if>
            <xsl:if test="$proxy_ip">
              <IPAddress>
                <xsl:value-of select="$proxy_ip"/>
              </IPAddress>
            </xsl:if>
          </Device>
        </xsl:if>
        <xsl:if test="$c_ip">
          <Client>
            <IPAddress>
              <xsl:value-of select="$c_ip"/>
            </IPAddress>
          </Client>
        </xsl:if>
        <xsl:if test="normalize-space(data[@name='cs-username']/@value)">
          <User>
            <Id>
              <xsl:value-of select="data[@name='cs-username']/@value"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='s-action']/@value)) then data[@name='s-action']/@value else 'request'"/>
        </TypeId>
        <xsl:variable name="sc_filter_result" select="data[@name='sc-filter-result']/@value[normalize-space(.)]"/>
        <xsl:variable name="cs_categories" select="data[@name='cs-categories']/@value[normalize-space(.)]"/>
        <xsl:variable name="cs_uri_path" select="data[@name='cs-uri-path']/@value[normalize-space(.)]"/>
        <xsl:variable name="cs_uri_port" select="data[@name='cs-uri-port']/@value[normalize-space(.)]"/>
        <xsl:variable name="cs_host" select="data[@name='cs-host']/@value[normalize-space(.)]"/>
        <xsl:if test="$c_ip or $cs_host or $cs_uri_port or $cs_uri_path or $cs_categories or $sc_filter_result = ('OBSERVED', 'DENIED') or normalize-space(data[@name='cs-method']/@value) or normalize-space(data[@name='sc-status']/@value) or normalize-space(data[@name='sc-bytes']/@value) or normalize-space(data[@name='x-virus-id']/@value)">
          <Network>
            <Open>
              <xsl:if test="$c_ip">
                <Source>
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$c_ip"/>
                    </IPAddress>
                  </Device>
                </Source>
              </xsl:if>
              <xsl:if test="$cs_host or $cs_uri_port or $cs_uri_path or $cs_categories">
                <Destination>
                  <xsl:if test="$cs_host">
                    <Device>
                      <HostName>
                        <xsl:value-of select="$cs_host"/>
                      </HostName>
                    </Device>
                  </xsl:if>
                  <xsl:if test="$cs_uri_port">
                    <Port>
                      <xsl:value-of select="$cs_uri_port"/>
                    </Port>
                  </xsl:if>
                  <xsl:sequence select="mcp:data('path', $cs_uri_path)"/>
                  <xsl:sequence select="mcp:data('category', $cs_categories)"/>
                </Destination>
              </xsl:if>
              <xsl:if test="$sc_filter_result = ('OBSERVED', 'DENIED')">
                <Outcome>
                  <Permitted>
                    <xsl:value-of select="if ($sc_filter_result = 'OBSERVED') then 'true' else 'false'"/>
                  </Permitted>
                </Outcome>
              </xsl:if>
              <xsl:sequence select="mcp:data('method', data[@name='cs-method']/@value)"/>
              <xsl:sequence select="mcp:data('status', data[@name='sc-status']/@value)"/>
              <xsl:sequence select="mcp:data('bytes_sent', data[@name='sc-bytes']/@value)"/>
              <xsl:sequence select="mcp:data('virus', data[@name='x-virus-id']/@value)"/>
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
