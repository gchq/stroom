<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <xsl:variable name="protocol_to_transport_protocol" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'TCP'" select="'TCP'"/>
      <xsl:map-entry key="'UDP'" select="'UDP'"/>
    </xsl:map>
  </xsl:variable>
  <xsl:template match="records">
    <Events Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
    <xsl:variable name="event_type" select="data[@name='event_type']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$event_type = 'TRAFFIC' and $action = 'ALLOW'">
        <xsl:apply-templates select="." mode="event_type_traffic_permitted"/>
      </xsl:when>
      <xsl:when test="$event_type = 'TRAFFIC' and $action = 'DENY'">
        <xsl:apply-templates select="." mode="event_type_traffic_denied"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('event_type=', string(($event_type)[1])), concat('action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'traffic_permitted': records where event_type = TRAFFIC and action = ALLOW. Writes a Network/Permit event
       (TypeId from event_type) from src_ip, src_port, protocol, dst_ip, dst_port. -->
  <xsl:template match="node()" mode="event_type_traffic_permitted">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Network>
          <Permit>
            <xsl:apply-templates select="." mode="source"/>
            <xsl:apply-templates select="." mode="destination"/>
          </Permit>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'traffic_denied': records where event_type = TRAFFIC and action = DENY. Writes a Network/Deny event
       (TypeId from event_type) from src_ip, src_port, protocol, dst_ip, dst_port. -->
  <xsl:template match="node()" mode="event_type_traffic_denied">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Network>
          <Deny>
            <xsl:apply-templates select="." mode="source"/>
            <xsl:apply-templates select="." mode="destination"/>
          </Deny>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'traffic_permitted', 'traffic_denied' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='timestamp']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='timestamp']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssXXX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'traffic_permitted', 'traffic_denied' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>FW</Name>
        <Environment>Prod</Environment>
      </System>
      <Generator>Firewall</Generator>
      <xsl:if test="normalize-space(data[@name='device']/@value)">
        <Device>
          <HostName>
            <xsl:value-of select="data[@name='device']/@value"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="src_port" select="data[@name='src_port']/@value[normalize-space(.)]"/>
      <xsl:variable name="src_ip" select="data[@name='src_ip']/@value[normalize-space(.)]"/>
      <xsl:if test="$src_ip or $src_port">
        <Client>
          <xsl:if test="$src_ip">
            <IPAddress>
              <xsl:value-of select="$src_ip"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$src_port">
            <Port>
              <xsl:value-of select="$src_port"/>
            </Port>
          </xsl:if>
        </Client>
      </xsl:if>
      <xsl:variable name="dst_port" select="data[@name='dst_port']/@value[normalize-space(.)]"/>
      <xsl:variable name="dst_ip" select="data[@name='dst_ip']/@value[normalize-space(.)]"/>
      <xsl:if test="$dst_ip or $dst_port">
        <Server>
          <xsl:if test="$dst_ip">
            <IPAddress>
              <xsl:value-of select="$dst_ip"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$dst_port">
            <Port>
              <xsl:value-of select="$dst_port"/>
            </Port>
          </xsl:if>
        </Server>
      </xsl:if>
      <xsl:if test="normalize-space(data[@name='username']/@value)">
        <User>
          <Id>
            <xsl:value-of select="data[@name='username']/@value"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'traffic_permitted', 'traffic_denied' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(data[@name='event_type']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='event_type']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'traffic_permitted', 'traffic_denied' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="normalize-space(data[@name='message']/@value)">
      <Description>
        <xsl:value-of select="data[@name='message']/@value"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- source: shared by the rules 'traffic_permitted', 'traffic_denied' -->
  <xsl:template match="node()" mode="source">
    <Source>
      <xsl:if test="normalize-space(data[@name='src_ip']/@value)">
        <Device>
          <IPAddress>
            <xsl:value-of select="data[@name='src_ip']/@value"/>
          </IPAddress>
        </Device>
      </xsl:if>
      <TransportProtocol>
        <xsl:value-of select="($protocol_to_transport_protocol?(normalize-space(data[@name='protocol']/@value)), 'Other')[1]"/>
      </TransportProtocol>
      <xsl:if test="normalize-space(data[@name='src_port']/@value)">
        <Port>
          <xsl:value-of select="data[@name='src_port']/@value"/>
        </Port>
      </xsl:if>
    </Source>
  </xsl:template>
  <!-- destination: shared by the rules 'traffic_permitted', 'traffic_denied' -->
  <xsl:template match="node()" mode="destination">
    <xsl:variable name="dst_port" select="data[@name='dst_port']/@value[normalize-space(.)]"/>
    <xsl:variable name="dst_ip" select="data[@name='dst_ip']/@value[normalize-space(.)]"/>
    <xsl:if test="$dst_ip or $dst_port">
      <Destination>
        <xsl:if test="$dst_ip">
          <Device>
            <IPAddress>
              <xsl:value-of select="$dst_ip"/>
            </IPAddress>
          </Device>
        </xsl:if>
        <xsl:if test="$dst_port">
          <Port>
            <xsl:value-of select="$dst_port"/>
          </Port>
        </xsl:if>
      </Destination>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
