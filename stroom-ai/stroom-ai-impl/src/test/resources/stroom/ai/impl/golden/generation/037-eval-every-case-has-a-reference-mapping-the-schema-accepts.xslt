<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
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
    <xsl:apply-templates select="." mode="event_type_connection"/>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('action', 'dport', 'dst', 'host', 'proto', 'src', 'time'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'connection': every record. Writes a Network/Open event (TypeId from action) from action, src, dst, dport,
       proto. -->
  <xsl:template match="node()" mode="event_type_connection">
    <Event>
      <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date((data[@name='time']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>Perimeter Firewall</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>firewall</Generator>
        <xsl:if test="data[@name='host']/@value[normalize-space(.)]">
          <Device>
            <HostName>
              <xsl:value-of select="(data[@name='host']/@value)[1]"/>
            </HostName>
          </Device>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action[1] else 'connection'"/>
        </TypeId>
        <xsl:variable name="proto" select="data[@name='proto']/@value[normalize-space(.)]"/>
        <xsl:variable name="dport" select="data[@name='dport']/@value[normalize-space(.)]"/>
        <xsl:variable name="dst" select="data[@name='dst']/@value[normalize-space(.)]"/>
        <xsl:variable name="src" select="data[@name='src']/@value[normalize-space(.)]"/>
        <xsl:if test="$src or $dst or $dport or $proto[1] = ('tcp', 'udp') or $action[1] = ('allow', 'deny')">
          <Network>
            <Open>
              <xsl:if test="$src">
                <Source>
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$src[1]"/>
                    </IPAddress>
                  </Device>
                </Source>
              </xsl:if>
              <xsl:if test="$dst or $dport or $proto[1] = ('tcp', 'udp')">
                <Destination>
                  <xsl:if test="$dst">
                    <Device>
                      <IPAddress>
                        <xsl:value-of select="$dst[1]"/>
                      </IPAddress>
                    </Device>
                  </xsl:if>
                  <xsl:if test="$proto[1] = ('tcp', 'udp')">
                    <TransportProtocol>
                      <xsl:value-of select="if ($proto[1] = 'tcp') then 'TCP' else 'UDP'"/>
                    </TransportProtocol>
                  </xsl:if>
                  <xsl:if test="$dport">
                    <Port>
                      <xsl:value-of select="$dport[1]"/>
                    </Port>
                  </xsl:if>
                </Destination>
              </xsl:if>
              <xsl:if test="$action[1] = ('allow', 'deny')">
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
</xsl:stylesheet>
