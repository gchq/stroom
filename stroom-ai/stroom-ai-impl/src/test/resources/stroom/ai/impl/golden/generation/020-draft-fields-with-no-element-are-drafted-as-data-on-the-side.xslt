<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="data[@name='event']/@value = 'CONNECT'">
        <xsl:apply-templates select="." mode="event_type_connected"/>
      </xsl:when>
      <xsl:when test="data[@name='event']/@value = 'DISCONNECT'">
        <xsl:apply-templates select="." mode="event_type_closed"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'connected': records where event = CONNECT. Writes a Network/Connect event (TypeId from event) from
       client_addr, client_port, proto, server_addr, server_port, conn_id, source_zone, destination_zone,
       destination_key, tls_ja3, .... -->
  <xsl:template match="node()" mode="event_type_connected">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Network>
          <Connect>
            <Source>
              <xsl:apply-templates select="." mode="device"/>
              <TransportProtocol>
                <xsl:value-of select="if (data[@name='proto']/@value = 'TCP') then 'TCP' else if (data[@name='proto']/@value = 'UDP') then 'UDP' else 'Other'"/>
              </TransportProtocol>
              <xsl:apply-templates select="." mode="port"/>
              <xsl:sequence select="mcp:data('source_zone', data[@name='source_zone']/@value)"/>
            </Source>
            <xsl:apply-templates select="." mode="destination"/>
            <xsl:sequence select="mcp:data('conn_id', data[@name='conn_id']/@value)"/>
            <xsl:apply-templates select="." mode="data_tls_ja_3_to_bytes_out"/>
          </Connect>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'closed': records where event = DISCONNECT. Writes a Network/Close event (TypeId from event) from
       client_addr, client_port, proto, server_addr, server_port, conn_id, source_zone, destination_zone,
       destination_key, tls_ja3, .... -->
  <xsl:template match="node()" mode="event_type_closed">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Network>
          <Close>
            <Source>
              <xsl:apply-templates select="." mode="device"/>
              <TransportProtocol>
                <xsl:value-of select="if (data[@name='proto']/@value = 'TCP') then 'TCP' else 'Other'"/>
              </TransportProtocol>
              <xsl:apply-templates select="." mode="port"/>
              <xsl:sequence select="mcp:data('source_zone', data[@name='source_zone']/@value)"/>
            </Source>
            <xsl:apply-templates select="." mode="destination"/>
            <xsl:sequence select="mcp:data('conn_id', data[@name='conn_id']/@value)"/>
            <xsl:apply-templates select="." mode="data_tls_ja_3_to_bytes_out"/>
          </Close>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from event) from conn_id, proto,
       source_zone, destination_zone, destination_key, tls_ja3, policy, bytes_in, bytes_out. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='event']/@value)) then data[@name='event']/@value else 'other'"/>
        </TypeId>
        <xsl:if test="normalize-space(data[@name='conn_id']/@value) or normalize-space(data[@name='proto']/@value) or normalize-space(data[@name='source_zone']/@value) or normalize-space(data[@name='destination_zone']/@value) or normalize-space(data[@name='destination_key']/@value) or normalize-space(data[@name='tls_ja3']/@value) or normalize-space(data[@name='policy']/@value) or normalize-space(data[@name='bytes_in']/@value) or normalize-space(data[@name='bytes_out']/@value)">
          <Unknown>
            <xsl:sequence select="mcp:data('conn_id', data[@name='conn_id']/@value)"/>
            <xsl:sequence select="mcp:data('proto', data[@name='proto']/@value)"/>
            <xsl:sequence select="mcp:data('source_zone', data[@name='source_zone']/@value)"/>
            <xsl:sequence select="mcp:data('destination_zone', data[@name='destination_zone']/@value)"/>
            <xsl:sequence select="mcp:data('destination_key', data[@name='destination_key']/@value)"/>
            <xsl:apply-templates select="." mode="data_tls_ja_3_to_bytes_out"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'connected', 'closed', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='timestamp']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='timestamp']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'connected', 'closed', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Broker</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>Connection Broker</Generator>
      <xsl:if test="normalize-space(data[@name='sensor']/@value)">
        <Device>
          <HostName>
            <xsl:value-of select="data[@name='sensor']/@value"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="client_port" select="data[@name='client_port']/@value[normalize-space(.)]"/>
      <xsl:variable name="client_addr" select="data[@name='client_addr']/@value[normalize-space(.)]"/>
      <xsl:if test="$client_addr or $client_port">
        <Client>
          <xsl:if test="$client_addr">
            <IPAddress>
              <xsl:value-of select="$client_addr"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$client_port">
            <Port>
              <xsl:value-of select="$client_port"/>
            </Port>
          </xsl:if>
        </Client>
      </xsl:if>
      <xsl:variable name="server_port" select="data[@name='server_port']/@value[normalize-space(.)]"/>
      <xsl:variable name="server_addr" select="data[@name='server_addr']/@value[normalize-space(.)]"/>
      <xsl:if test="$server_addr or $server_port">
        <Server>
          <xsl:if test="$server_addr">
            <IPAddress>
              <xsl:value-of select="$server_addr"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$server_port">
            <Port>
              <xsl:value-of select="$server_port"/>
            </Port>
          </xsl:if>
        </Server>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'connected', 'closed' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(data[@name='event']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='event']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- device: shared by the rules 'connected', 'closed' -->
  <xsl:template match="node()" mode="device">
    <xsl:if test="normalize-space(data[@name='client_addr']/@value)">
      <Device>
        <IPAddress>
          <xsl:value-of select="data[@name='client_addr']/@value"/>
        </IPAddress>
      </Device>
    </xsl:if>
  </xsl:template>
  <!-- port: shared by the rules 'connected', 'closed' -->
  <xsl:template match="node()" mode="port">
    <xsl:if test="normalize-space(data[@name='client_port']/@value)">
      <Port>
        <xsl:value-of select="data[@name='client_port']/@value"/>
      </Port>
    </xsl:if>
  </xsl:template>
  <!-- destination: shared by the rules 'connected', 'closed' -->
  <xsl:template match="node()" mode="destination">
    <xsl:variable name="server_port" select="data[@name='server_port']/@value[normalize-space(.)]"/>
    <xsl:variable name="server_addr" select="data[@name='server_addr']/@value[normalize-space(.)]"/>
    <xsl:if test="$server_addr or $server_port or normalize-space(data[@name='destination_zone']/@value) or normalize-space(data[@name='destination_key']/@value)">
      <Destination>
        <xsl:if test="$server_addr">
          <Device>
            <IPAddress>
              <xsl:value-of select="$server_addr"/>
            </IPAddress>
          </Device>
        </xsl:if>
        <xsl:if test="$server_port">
          <Port>
            <xsl:value-of select="$server_port"/>
          </Port>
        </xsl:if>
        <xsl:sequence select="mcp:data('destination_zone', data[@name='destination_zone']/@value)"/>
        <xsl:sequence select="mcp:data('destination_key', data[@name='destination_key']/@value)"/>
      </Destination>
    </xsl:if>
  </xsl:template>
  <!-- data_tls_ja_3_to_bytes_out: shared by the rules 'connected', 'closed', 'other' -->
  <xsl:template match="node()" mode="data_tls_ja_3_to_bytes_out">
    <xsl:sequence select="mcp:data('tls_ja3', data[@name='tls_ja3']/@value)"/>
    <xsl:sequence select="mcp:data('policy', data[@name='policy']/@value)"/>
    <xsl:sequence select="mcp:data('bytes_in', data[@name='bytes_in']/@value)"/>
    <xsl:sequence select="mcp:data('bytes_out', data[@name='bytes_out']/@value)"/>
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
