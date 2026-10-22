<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:variable name="protocol_to_transport_protocol" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'TCP'" select="'TCP'"/>
      <xsl:map-entry key="'UDP'" select="'UDP'"/>
    </xsl:map>
  </xsl:variable>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
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
      <xsl:when test="$action = 'LOGIN_SUCCESS'">
        <xsl:apply-templates select="." mode="event_type_login_success"/>
      </xsl:when>
      <xsl:when test="$action = 'LOGIN_FAILED'">
        <xsl:apply-templates select="." mode="event_type_login_failed"/>
      </xsl:when>
      <xsl:when test="$action = 'CONFIG_CHANGE'">
        <xsl:apply-templates select="." mode="event_type_config_change"/>
      </xsl:when>
      <xsl:when test="$event_type = 'SYSTEM' and $action = 'VPN_TUNNEL_DOWN'">
        <xsl:apply-templates select="." mode="event_type_system_alert"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'traffic_permitted': records where event_type = TRAFFIC and action = ALLOW. Writes a Network/Permit event
       (TypeId from event_type) from src_ip, src_port, protocol, dst_ip, dst_port, rule_id. -->
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
            <xsl:sequence select="mcp:data('rule_id', data[@name='rule_id']/@value)"/>
          </Permit>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'traffic_denied': records where event_type = TRAFFIC and action = DENY. Writes a Network/Deny event
       (TypeId from event_type) from src_ip, src_port, protocol, dst_ip, dst_port, rule_id. -->
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
            <xsl:sequence select="mcp:data('rule_id', data[@name='rule_id']/@value)"/>
          </Deny>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'login_success': records where action = LOGIN_SUCCESS. Writes an Authenticate event (TypeId from action)
       from action, username, protocol, rule_id. -->
  <xsl:template match="node()" mode="event_type_login_success">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="event_detail_type_id"/>
        <Description>Administrator logged in</Description>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <Outcome>
            <Success>true</Success>
          </Outcome>
          <xsl:sequence select="mcp:data('protocol', data[@name='protocol']/@value)"/>
          <xsl:sequence select="mcp:data('rule_id', data[@name='rule_id']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'login_failed': records where action = LOGIN_FAILED. Writes an Authenticate event (TypeId from action)
       from action, username, protocol, rule_id. -->
  <xsl:template match="node()" mode="event_type_login_failed">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="event_detail_type_id"/>
        <Description>Administrator failed to log in</Description>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <Outcome>
            <Success>false</Success>
          </Outcome>
          <xsl:sequence select="mcp:data('protocol', data[@name='protocol']/@value)"/>
          <xsl:sequence select="mcp:data('rule_id', data[@name='rule_id']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'config_change': records where action = CONFIG_CHANGE. Writes an Update event (TypeId from action) from
       action, message, protocol, rule_id. -->
  <xsl:template match="node()" mode="event_type_config_change">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="event_detail_type_id"/>
        <Description>Configuration modified</Description>
        <xsl:variable name="message" select="data[@name='message']/@value[normalize-space(.)]"/>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
        <xsl:if test="$action or $message or normalize-space(data[@name='protocol']/@value) or normalize-space(data[@name='rule_id']/@value)">
          <Update>
            <xsl:if test="$action or $message">
              <After>
                <Configuration>
                  <xsl:if test="$action">
                    <Type>
                      <xsl:value-of select="$action"/>
                    </Type>
                  </xsl:if>
                  <xsl:if test="$message">
                    <Description>
                      <xsl:value-of select="$message"/>
                    </Description>
                  </xsl:if>
                </Configuration>
              </After>
            </xsl:if>
            <xsl:sequence select="mcp:data('protocol', data[@name='protocol']/@value)"/>
            <xsl:sequence select="mcp:data('rule_id', data[@name='rule_id']/@value)"/>
          </Update>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'system_alert': records where event_type = SYSTEM and action = VPN_TUNNEL_DOWN. Writes an Alert event
       (TypeId from event_type) from action, protocol, rule_id. -->
  <xsl:template match="node()" mode="event_type_system_alert">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="rule_id" select="data[@name='rule_id']/@value[normalize-space(.)]"/>
      <xsl:variable name="protocol" select="data[@name='protocol']/@value[normalize-space(.)]"/>
      <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
      <xsl:if test="normalize-space(data[@name='message']/@value) or normalize-space(data[@name='event_type']/@value) or $action = ('VPN_TUNNEL_DOWN') or $protocol or $rule_id">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:apply-templates select="." mode="description"/>
          <xsl:if test="$action = ('VPN_TUNNEL_DOWN') or $protocol or $rule_id">
            <Alert>
              <xsl:if test="$action = ('VPN_TUNNEL_DOWN')">
                <Type>
                  <xsl:value-of select="'Network'"/>
                </Type>
              </xsl:if>
              <xsl:sequence select="mcp:data('protocol', $protocol)"/>
              <xsl:sequence select="mcp:data('rule_id', $rule_id)"/>
            </Alert>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from event_type) from protocol,
       action, rule_id. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='event_type']/@value)) then data[@name='event_type']/@value else 'other'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:if test="normalize-space(data[@name='protocol']/@value) or normalize-space(data[@name='action']/@value) or normalize-space(data[@name='rule_id']/@value)">
          <Unknown>
            <xsl:sequence select="mcp:data('protocol', data[@name='protocol']/@value)"/>
            <xsl:sequence select="mcp:data('action', data[@name='action']/@value)"/>
            <xsl:sequence select="mcp:data('rule_id', data[@name='rule_id']/@value)"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'traffic_permitted', 'traffic_denied', 'login_success', 'login_failed',
       'config_change', 'system_alert', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='timestamp']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='timestamp']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssXXX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'traffic_permitted', 'traffic_denied', 'login_success', 'login_failed',
       'config_change', 'system_alert', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>FW</Name>
        <Environment>Eval</Environment>
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
  <!-- type_id: shared by the rules 'traffic_permitted', 'traffic_denied', 'system_alert' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(data[@name='event_type']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='event_type']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'traffic_permitted', 'traffic_denied', 'system_alert', 'other' -->
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
  <!-- event_detail_type_id: shared by the rules 'login_success', 'login_failed', 'config_change' -->
  <xsl:template match="node()" mode="event_detail_type_id">
    <xsl:if test="normalize-space(data[@name='action']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='action']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'login_success', 'login_failed' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="normalize-space(data[@name='username']/@value)">
      <User>
        <Id>
          <xsl:value-of select="data[@name='username']/@value"/>
        </Id>
      </User>
    </xsl:if>
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
