<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="data[@name='type']/@value = 'TRAFFIC'">
        <xsl:apply-templates select="." mode="event_type_session"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('type=', string((data[@name='type']/@value[normalize-space(.)])[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:for-each select="data[@name][not(@name = ('action', 'app', 'dport', 'dst', 'generated_time', 'host', 'rule', 'session_end_reason', 'sport', 'src', 'srcuser', 'subtype', 'type'))][normalize-space(string(@value))]">
      <!-- A field no rule reads: logged once a stream (ignored_fields: left out on purpose) -->
      <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
      <xsl:if test="not(string(stroom:get($mcp_unread)))">
        <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
        <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  <!-- Rule 'session': records where type = TRAFFIC. Writes a Network/Open event from src, sport, dst, dport, rule,
       app, session_end_reason, action. -->
  <xsl:template match="node()" mode="event_type_session">
    <Event>
      <xsl:if test="data[@name='generated_time']/@value[normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date((data[@name='generated_time']/@value)[1], 'yyyy/MM/dd HH:mm:ss', 'UTC')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="src" select="data[@name='src']/@value[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>Palo Alto Firewall</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>PAN-OS</Generator>
        <xsl:if test="data[@name='host']/@value[normalize-space(.)]">
          <Device>
            <HostName>
              <xsl:value-of select="(data[@name='host']/@value)[1]"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="$src">
          <Client>
            <IPAddress>
              <xsl:value-of select="$src[1]"/>
            </IPAddress>
          </Client>
        </xsl:if>
        <xsl:if test="data[@name='srcuser']/@value[normalize-space(.)]">
          <User>
            <Id>
              <xsl:value-of select="replace(replace((data[@name='srcuser']/@value)[1], '^[^\\]*\\', ''), '@.*$', '')"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>
          <xsl:variable name="event_detail_type_id" select="concat(data[@name='type']/@value, '-', data[@name='subtype']/@value)[normalize-space(.)]"/>
          <xsl:if test="count($event_detail_type_id) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($event_detail_type_id), ') for ', 'EventDetail/TypeId, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="if (exists($event_detail_type_id)) then $event_detail_type_id[1] else 'session'"/>
        </TypeId>
        <Network>
          <Open>
            <xsl:variable name="sport" select="data[@name='sport']/@value[normalize-space(.)]"/>
            <xsl:if test="$src or $sport">
              <Source>
                <xsl:if test="$src">
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$src[1]"/>
                    </IPAddress>
                  </Device>
                </xsl:if>
                <xsl:if test="$sport">
                  <Port>
                    <xsl:value-of select="$sport[1]"/>
                  </Port>
                </xsl:if>
              </Source>
            </xsl:if>
            <xsl:variable name="dport" select="data[@name='dport']/@value[normalize-space(.)]"/>
            <xsl:variable name="dst" select="data[@name='dst']/@value[normalize-space(.)]"/>
            <xsl:if test="$dst or $dport">
              <Destination>
                <xsl:if test="$dst">
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$dst[1]"/>
                    </IPAddress>
                  </Device>
                </xsl:if>
                <xsl:if test="$dport">
                  <Port>
                    <xsl:value-of select="$dport[1]"/>
                  </Port>
                </xsl:if>
              </Destination>
            </xsl:if>
            <Outcome>
              <Permitted>
                <xsl:value-of select="if ((data[@name='action']/@value)[1] = 'allow') then 'true' else 'false'"/>
              </Permitted>
            </Outcome>
            <xsl:sequence select="mcp:data('rule', data[@name='rule']/@value)"/>
            <xsl:sequence select="mcp:data('app', data[@name='app']/@value)"/>
            <xsl:sequence select="mcp:data('end_reason', data[@name='session_end_reason']/@value)"/>
          </Open>
        </Network>
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
