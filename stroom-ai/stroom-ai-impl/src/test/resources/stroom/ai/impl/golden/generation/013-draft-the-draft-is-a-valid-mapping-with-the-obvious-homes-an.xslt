<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="event_type" select="*[@key='event_type'][normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$event_type = 'TRAFFIC' and *[@key='action'] = 'ALLOW'">
        <xsl:apply-templates select="." mode="event_type_traffic_permitted"/>
      </xsl:when>
      <xsl:when test="$event_type = 'LOGIN'">
        <xsl:apply-templates select="." mode="event_type_login"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
        <xsl:sequence select="stroom:log('INFO', concat('Kept as Unknown by rule ''other'': record ', $record-no, ' (', string-join((concat('event_type=', string(($event_type)[1])), concat('action=', string((*[@key='action'][normalize-space(.)])[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:for-each select="*[@key][not(@key = ('action', 'bytes_received', 'bytes_sent', 'device', 'dst_ip', 'dst_port', 'event_type', 'message', 'protocol', 'rule_id', 'severity', 'src_ip', 'src_port', 'timestamp', 'username'))][normalize-space(string(.))]">
      <!-- A field no rule reads: logged once a stream (ignored_fields: left out on purpose) -->
      <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
      <xsl:if test="not(string(stroom:get($mcp_unread)))">
        <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
        <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  <!-- Rule 'traffic_permitted': records where event_type = TRAFFIC and action = ALLOW. Writes a Network/Permit event
       (TypeId from event_type) from src_ip, src_port, protocol, dst_ip, dst_port, severity, rule_id,
       bytes_sent, bytes_received. -->
  <xsl:template match="node()" mode="event_type_traffic_permitted">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Network>
          <Permit>
            <Source>
              <xsl:if test="*[@key='src_ip'][normalize-space(.)]">
                <Device>
                  <IPAddress>
                    <xsl:value-of select="(*[@key='src_ip'])[1]"/>
                  </IPAddress>
                </Device>
              </xsl:if>
              <TransportProtocol>
                <xsl:value-of select="if ((*[@key='protocol'])[1] = 'TCP') then 'TCP' else 'Other'"/>
              </TransportProtocol>
              <xsl:if test="*[@key='src_port'][normalize-space(.)]">
                <Port>
                  <xsl:value-of select="(*[@key='src_port'])[1]"/>
                </Port>
              </xsl:if>
            </Source>
            <xsl:variable name="dst_port" select="*[@key='dst_port'][normalize-space(.)]"/>
            <xsl:variable name="dst_ip" select="*[@key='dst_ip'][normalize-space(.)]"/>
            <xsl:if test="$dst_ip or $dst_port">
              <Destination>
                <xsl:if test="$dst_ip">
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$dst_ip[1]"/>
                    </IPAddress>
                  </Device>
                </xsl:if>
                <xsl:if test="$dst_port">
                  <Port>
                    <xsl:value-of select="$dst_port[1]"/>
                  </Port>
                </xsl:if>
              </Destination>
            </xsl:if>
            <xsl:sequence select="mcp:data('severity', *[@key='severity'])"/>
            <xsl:sequence select="mcp:data('rule_id', *[@key='rule_id'])"/>
            <xsl:sequence select="mcp:data('bytes_sent', *[@key='bytes_sent'])"/>
            <xsl:sequence select="mcp:data('bytes_received', *[@key='bytes_received'])"/>
          </Permit>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'login': records where event_type = LOGIN. Writes an Authenticate event (TypeId from event_type) from
       username, severity, protocol, action, rule_id, bytes_sent, bytes_received. -->
  <xsl:template match="node()" mode="event_type_login">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="*[@key='username'][normalize-space(.)]">
            <User>
              <Id>
                <xsl:value-of select="(*[@key='username'])[1]"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:apply-templates select="." mode="data_severity_to_bytes_received"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from event_type) from severity,
       protocol, action, rule_id, bytes_sent, bytes_received. Unknown on purpose: kept unknown for the test. -->
  <xsl:template match="node()" mode="event_type_other">
    <!-- other: EventDetail/Unknown on purpose: kept unknown for the test -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (*[@key='event_type'][normalize-space(.)]) then (*[@key='event_type'])[1] else 'other'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Unknown>
          <xsl:apply-templates select="." mode="data_severity_to_bytes_received"/>
        </Unknown>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'traffic_permitted', 'login', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="*[@key='timestamp'][normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((*[@key='timestamp'])[1], 'yyyy-MM-dd''T''HH:mm:ssXXX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'traffic_permitted', 'login', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>FortiOS firewall</Name>
        <Environment>TODO e.g. Prod</Environment>
      </System>
      <Generator>FortiOS firewall</Generator>
      <xsl:if test="*[@key='device'][normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(*[@key='device'])[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="src_port" select="*[@key='src_port'][normalize-space(.)]"/>
      <xsl:variable name="src_ip" select="*[@key='src_ip'][normalize-space(.)]"/>
      <xsl:if test="$src_ip or $src_port">
        <Client>
          <xsl:if test="$src_ip">
            <IPAddress>
              <xsl:value-of select="$src_ip[1]"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$src_port">
            <Port>
              <xsl:value-of select="$src_port[1]"/>
            </Port>
          </xsl:if>
        </Client>
      </xsl:if>
      <xsl:variable name="dst_port" select="*[@key='dst_port'][normalize-space(.)]"/>
      <xsl:variable name="dst_ip" select="*[@key='dst_ip'][normalize-space(.)]"/>
      <xsl:if test="$dst_ip or $dst_port">
        <Server>
          <xsl:if test="$dst_ip">
            <IPAddress>
              <xsl:value-of select="$dst_ip[1]"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$dst_port">
            <Port>
              <xsl:value-of select="$dst_port[1]"/>
            </Port>
          </xsl:if>
        </Server>
      </xsl:if>
      <xsl:if test="*[@key='username'][normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(*[@key='username'])[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'traffic_permitted', 'login' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="*[@key='event_type'][normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="(*[@key='event_type'])[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'traffic_permitted', 'login', 'other' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="*[@key='message'][normalize-space(.)]">
      <Description>
        <xsl:value-of select="(*[@key='message'])[1]"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- data_severity_to_bytes_received: shared by the rules 'login', 'other' -->
  <xsl:template match="node()" mode="data_severity_to_bytes_received">
    <xsl:sequence select="mcp:data('severity', *[@key='severity'])"/>
    <xsl:sequence select="mcp:data('protocol', *[@key='protocol'])"/>
    <xsl:sequence select="mcp:data('action', *[@key='action'])"/>
    <xsl:sequence select="mcp:data('rule_id', *[@key='rule_id'])"/>
    <xsl:sequence select="mcp:data('bytes_sent', *[@key='bytes_sent'])"/>
    <xsl:sequence select="mcp:data('bytes_received', *[@key='bytes_received'])"/>
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
