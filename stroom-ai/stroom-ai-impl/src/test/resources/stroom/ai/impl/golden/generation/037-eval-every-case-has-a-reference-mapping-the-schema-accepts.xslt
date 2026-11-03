<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_connection"/>
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
