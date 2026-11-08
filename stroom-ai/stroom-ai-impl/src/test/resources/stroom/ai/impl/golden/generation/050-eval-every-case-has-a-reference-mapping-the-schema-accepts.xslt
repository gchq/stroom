<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:variable name="proto_to_transport_protocol" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'TCP'" select="'TCP'"/>
      <xsl:map-entry key="'UDP'" select="'UDP'"/>
    </xsl:map>
  </xsl:variable>
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <!-- Fields of the record no rule reads are logged (mcp_unread_fields) -->
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:variable name="event" select="data[@name='event']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$event = 'CONNECT'">
        <xsl:apply-templates select="." mode="event_type_connected"/>
      </xsl:when>
      <xsl:when test="$event = 'DISCONNECT'">
        <xsl:apply-templates select="." mode="event_type_closed"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('event=', string(($event)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('bytes_in', 'bytes_out', 'client_addr', 'client_port', 'conn_id', 'destination_key', 'destination_zone', 'event', 'policy', 'proto', 'sensor', 'server_addr', 'server_port', 'source_zone', 'timestamp', 'tls_ja3'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'connected': records where event = CONNECT. Writes a Network/Connect event (TypeId from event) from
       client_addr, client_port, proto, source_zone, tls_ja3, server_addr, server_port, destination_zone,
       destination_key, conn_id, .... -->
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
              <xsl:apply-templates select="." mode="transport_protocol"/>
              <xsl:apply-templates select="." mode="port"/>
              <xsl:sequence select="mcp:data('source_zone', data[@name='source_zone']/@value)"/>
              <xsl:sequence select="mcp:data('tls_ja3', data[@name='tls_ja3']/@value)"/>
            </Source>
            <xsl:apply-templates select="." mode="destination"/>
            <xsl:sequence select="mcp:data('conn_id', data[@name='conn_id']/@value)"/>
            <xsl:sequence select="mcp:data('policy', data[@name='policy']/@value)"/>
          </Connect>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'closed': records where event = DISCONNECT. Writes a Network/Close event (TypeId from event) from
       client_addr, client_port, proto, source_zone, server_addr, server_port, destination_zone,
       destination_key, conn_id, bytes_in, .... -->
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
              <xsl:apply-templates select="." mode="transport_protocol"/>
              <xsl:apply-templates select="." mode="port"/>
              <xsl:sequence select="mcp:data('source_zone', data[@name='source_zone']/@value)"/>
            </Source>
            <xsl:apply-templates select="." mode="destination"/>
            <xsl:sequence select="mcp:data('conn_id', data[@name='conn_id']/@value)"/>
            <xsl:sequence select="mcp:data('bytes_in', data[@name='bytes_in']/@value)"/>
            <xsl:sequence select="mcp:data('bytes_out', data[@name='bytes_out']/@value)"/>
          </Close>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'connected', 'closed' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='timestamp']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((data[@name='timestamp']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssXXX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'connected', 'closed' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Connection Broker</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>broker</Generator>
      <xsl:if test="data[@name='sensor']/@value[normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(data[@name='sensor']/@value)[1]"/>
          </HostName>
        </Device>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'connected', 'closed' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="data[@name='event']/@value[normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="(data[@name='event']/@value)[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- device: shared by the rules 'connected', 'closed' -->
  <xsl:template match="node()" mode="device">
    <xsl:if test="data[@name='client_addr']/@value[normalize-space(.)]">
      <Device>
        <IPAddress>
          <xsl:value-of select="(data[@name='client_addr']/@value)[1]"/>
        </IPAddress>
      </Device>
    </xsl:if>
  </xsl:template>
  <!-- transport_protocol: shared by the rules 'connected', 'closed' -->
  <xsl:template match="node()" mode="transport_protocol">
    <TransportProtocol>
      <xsl:value-of select="($proto_to_transport_protocol?((data[@name='proto']/@value)[1]), 'Other')[1]"/>
    </TransportProtocol>
  </xsl:template>
  <!-- port: shared by the rules 'connected', 'closed' -->
  <xsl:template match="node()" mode="port">
    <xsl:if test="data[@name='client_port']/@value[normalize-space(.)]">
      <Port>
        <xsl:value-of select="(data[@name='client_port']/@value)[1]"/>
      </Port>
    </xsl:if>
  </xsl:template>
  <!-- destination: shared by the rules 'connected', 'closed' -->
  <xsl:template match="node()" mode="destination">
    <xsl:variable name="server_port" select="data[@name='server_port']/@value[normalize-space(.)]"/>
    <xsl:variable name="server_addr" select="data[@name='server_addr']/@value[normalize-space(.)]"/>
    <xsl:if test="$server_addr or $server_port or data[@name='destination_zone']/@value[normalize-space(.)] or data[@name='destination_key']/@value[normalize-space(.)]">
      <Destination>
        <xsl:if test="$server_addr">
          <Device>
            <IPAddress>
              <xsl:value-of select="$server_addr[1]"/>
            </IPAddress>
          </Device>
        </xsl:if>
        <xsl:if test="$server_port">
          <Port>
            <xsl:value-of select="$server_port[1]"/>
          </Port>
        </xsl:if>
        <xsl:sequence select="mcp:data('destination_zone', data[@name='destination_zone']/@value)"/>
        <xsl:sequence select="mcp:data('destination_key', data[@name='destination_key']/@value)"/>
      </Destination>
    </xsl:if>
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
