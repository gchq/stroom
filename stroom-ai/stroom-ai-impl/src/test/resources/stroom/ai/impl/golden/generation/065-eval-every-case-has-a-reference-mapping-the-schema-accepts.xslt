<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_request"/>
    <xsl:for-each select="data[@name][not(@name = ('#Remark', '#Software', 'c-ip', 'cs-categories', 'cs-host', 'cs-method', 'cs-uri-path', 'cs-uri-port', 'cs-username', 'date', 's-action', 'sc-bytes', 'sc-filter-result', 'sc-status', 'time', 'x-virus-id'))][normalize-space(string(@value))]">
      <!-- A field no rule reads: logged once a stream (ignored_fields: left out on purpose) -->
      <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
      <xsl:if test="not(string(stroom:get($mcp_unread)))">
        <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
        <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  <!-- Rule 'request': every record. Writes a Network/Open event (TypeId from s-action) from c-ip, cs-host,
       cs-uri-port, cs-uri-path, cs-categories, cs-method, sc-status, sc-bytes, x-virus-id, sc-filter-result. -->
  <xsl:template match="node()" mode="event_type_request">
    <Event>
      <xsl:variable name="event_time_time_created" select="concat(data[@name='date']/@value, 'T', data[@name='time']/@value, 'Z')[normalize-space(.)]"/>
      <xsl:if test="exists($event_time_time_created)">
        <EventTime>
          <TimeCreated>
            <xsl:if test="count($event_time_time_created) gt 1">
              <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($event_time_time_created), ') for ', 'EventTime/TimeCreated, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
            </xsl:if>
            <xsl:value-of select="stroom:format-date($event_time_time_created[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="c_ip" select="data[@name='c-ip']/@value[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>Blue Coat ProxySG</Name>
          <Environment>Eval</Environment>
        </System>
        <xsl:if test="data[@name='#Software']/@value[normalize-space(.)]">
          <Generator>
            <xsl:value-of select="(data[@name='#Software']/@value)[1]"/>
          </Generator>
        </xsl:if>
        <xsl:variable name="proxy_name_parts" select="analyze-string(string((data[@name='#Remark']/@value)[1]), '^\S+ &quot;([^&quot;]*)&quot; &quot;([^&quot;]*)&quot;.*$')"/>
        <xsl:variable name="proxy_ip" select="$proxy_name_parts/fn:match//fn:group[@nr=2][normalize-space(.)]"/>
        <xsl:variable name="proxy_name" select="$proxy_name_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
        <xsl:if test="$proxy_name or $proxy_ip">
          <Device>
            <xsl:if test="$proxy_name">
              <HostName>
                <xsl:value-of select="$proxy_name[1]"/>
              </HostName>
            </xsl:if>
            <xsl:if test="$proxy_ip">
              <IPAddress>
                <xsl:value-of select="$proxy_ip[1]"/>
              </IPAddress>
            </xsl:if>
          </Device>
        </xsl:if>
        <xsl:if test="$c_ip">
          <Client>
            <IPAddress>
              <xsl:value-of select="$c_ip[1]"/>
            </IPAddress>
          </Client>
        </xsl:if>
        <xsl:if test="data[@name='cs-username']/@value[normalize-space(.)]">
          <User>
            <Id>
              <xsl:value-of select="(data[@name='cs-username']/@value)[1]"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (data[@name='s-action']/@value[normalize-space(.)]) then (data[@name='s-action']/@value)[1] else 'request'"/>
        </TypeId>
        <xsl:variable name="sc_filter_result" select="data[@name='sc-filter-result']/@value[normalize-space(.)]"/>
        <xsl:variable name="cs_categories" select="data[@name='cs-categories']/@value[normalize-space(.)]"/>
        <xsl:variable name="cs_uri_path" select="data[@name='cs-uri-path']/@value[normalize-space(.)]"/>
        <xsl:variable name="cs_uri_port" select="data[@name='cs-uri-port']/@value[normalize-space(.)]"/>
        <xsl:variable name="cs_host" select="data[@name='cs-host']/@value[normalize-space(.)]"/>
        <xsl:if test="$c_ip or $cs_host or $cs_uri_port or $cs_uri_path or $cs_categories or $sc_filter_result[1] = ('OBSERVED', 'DENIED') or data[@name='cs-method']/@value[normalize-space(.)] or data[@name='sc-status']/@value[normalize-space(.)] or data[@name='sc-bytes']/@value[normalize-space(.)] or data[@name='x-virus-id']/@value[normalize-space(.)]">
          <Network>
            <Open>
              <xsl:if test="$c_ip">
                <Source>
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$c_ip[1]"/>
                    </IPAddress>
                  </Device>
                </Source>
              </xsl:if>
              <xsl:if test="$cs_host or $cs_uri_port or $cs_uri_path or $cs_categories">
                <Destination>
                  <xsl:if test="$cs_host">
                    <Device>
                      <HostName>
                        <xsl:value-of select="$cs_host[1]"/>
                      </HostName>
                    </Device>
                  </xsl:if>
                  <xsl:if test="$cs_uri_port">
                    <Port>
                      <xsl:value-of select="$cs_uri_port[1]"/>
                    </Port>
                  </xsl:if>
                  <xsl:sequence select="mcp:data('path', $cs_uri_path)"/>
                  <xsl:sequence select="mcp:data('category', $cs_categories)"/>
                </Destination>
              </xsl:if>
              <xsl:if test="$sc_filter_result[1] = ('OBSERVED', 'DENIED')">
                <Outcome>
                  <Permitted>
                    <xsl:value-of select="if ($sc_filter_result[1] = 'OBSERVED') then 'true' else 'false'"/>
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
      <Data Name="{$name}" Value="{$value[1]}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
