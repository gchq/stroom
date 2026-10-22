<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:variable name="severity_to_severity" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'INFO'" select="'Info'"/>
      <xsl:map-entry key="'NOTICE'" select="'Info'"/>
      <xsl:map-entry key="'WARNING'" select="'Minor'"/>
      <xsl:map-entry key="'ERROR'" select="'Major'"/>
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
      <xsl:when test="$event_type = 'TRAFFIC'">
        <xsl:apply-templates select="." mode="event_type_connection"/>
      </xsl:when>
      <xsl:when test="$event_type = 'ADMIN' and $action = ('LOGIN_SUCCESS', 'LOGIN_FAILED')">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$event_type = 'ADMIN' and $action = 'LOGOUT'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:when test="$event_type = 'ADMIN' and $action = 'CONFIG_CHANGE'">
        <xsl:apply-templates select="." mode="event_type_config_change"/>
      </xsl:when>
      <xsl:when test="$event_type = 'ADMIN' and $action = 'CONFIG_BACKUP'">
        <xsl:apply-templates select="." mode="event_type_config_backup"/>
      </xsl:when>
      <xsl:when test="$event_type = 'SYSTEM'">
        <xsl:apply-templates select="." mode="event_type_system"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('event_type=', string(($event_type)[1])), concat('action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'connection': records where event_type = TRAFFIC. Writes a Network/Open event (TypeId from action) from
       src_ip, src_port, dst_ip, protocol, dst_port, rule_id, action, bytes_sent, bytes_received. -->
  <xsl:template match="node()" mode="event_type_connection">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action else 'connection'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:variable name="rule_id" select="data[@name='rule_id']/@value[normalize-space(.)]"/>
        <xsl:variable name="dst_port" select="data[@name='dst_port']/@value[normalize-space(.)]"/>
        <xsl:variable name="protocol" select="data[@name='protocol']/@value[normalize-space(.)]"/>
        <xsl:variable name="dst_ip" select="data[@name='dst_ip']/@value[normalize-space(.)]"/>
        <xsl:variable name="src_port" select="data[@name='src_port']/@value[normalize-space(.)]"/>
        <xsl:variable name="src_ip" select="data[@name='src_ip']/@value[normalize-space(.)]"/>
        <xsl:if test="$src_ip or $src_port or $dst_ip or $protocol or $dst_port or $rule_id or $action = ('ALLOW', 'DENY') or normalize-space(data[@name='bytes_sent']/@value) or normalize-space(data[@name='bytes_received']/@value)">
          <Network>
            <Open>
              <xsl:if test="$src_ip or $src_port">
                <Source>
                  <xsl:if test="$src_ip">
                    <Device>
                      <IPAddress>
                        <xsl:value-of select="$src_ip"/>
                      </IPAddress>
                    </Device>
                  </xsl:if>
                  <xsl:if test="$src_port">
                    <Port>
                      <xsl:value-of select="$src_port"/>
                    </Port>
                  </xsl:if>
                </Source>
              </xsl:if>
              <xsl:if test="$dst_ip or $protocol or $dst_port">
                <Destination>
                  <xsl:if test="$dst_ip">
                    <Device>
                      <IPAddress>
                        <xsl:value-of select="$dst_ip"/>
                      </IPAddress>
                    </Device>
                  </xsl:if>
                  <xsl:if test="$protocol">
                    <TransportProtocol>
                      <xsl:value-of select="$protocol"/>
                    </TransportProtocol>
                  </xsl:if>
                  <xsl:if test="$dst_port">
                    <Port>
                      <xsl:value-of select="$dst_port"/>
                    </Port>
                  </xsl:if>
                </Destination>
              </xsl:if>
              <xsl:if test="$rule_id">
                <Rule>
                  <xsl:value-of select="$rule_id"/>
                </Rule>
              </xsl:if>
              <xsl:if test="$action = ('ALLOW', 'DENY')">
                <Outcome>
                  <Permitted>
                    <xsl:value-of select="if ($action = 'ALLOW') then 'true' else 'false'"/>
                  </Permitted>
                </Outcome>
              </xsl:if>
              <xsl:sequence select="mcp:data('bytes_sent', data[@name='bytes_sent']/@value)"/>
              <xsl:sequence select="mcp:data('bytes_received', data[@name='bytes_received']/@value)"/>
            </Open>
          </Network>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logon': records where event_type = ADMIN and action in LOGIN_SUCCESS, LOGIN_FAILED. Writes an
       Authenticate event (TypeId from action) from username, src_ip, action. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:if test="data[@name='action']/@value = ('LOGIN_SUCCESS', 'LOGIN_FAILED')">
            <Outcome>
              <Success>
                <xsl:value-of select="if (data[@name='action']/@value = 'LOGIN_SUCCESS') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where event_type = ADMIN and action = LOGOUT. Writes an Authenticate event (TypeId from
       action) from username, src_ip. -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'config change': records where event_type = ADMIN and action = CONFIG_CHANGE. Writes an Update event
       (TypeId from action) from username, src_ip. -->
  <xsl:template match="node()" mode="event_type_config_change">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Update>
          <After>
            <Configuration>
              <Name>Firewall rules</Name>
            </Configuration>
          </After>
        </Update>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'config backup': records where event_type = ADMIN and action = CONFIG_BACKUP. Writes an Export event
       (TypeId from action) from username, src_ip. -->
  <xsl:template match="node()" mode="event_type_config_backup">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Export>
          <Source>
            <Configuration>
              <Name>Running configuration</Name>
            </Configuration>
          </Source>
        </Export>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'system': records where event_type = SYSTEM. Writes an Alert event (TypeId from action) from action,
       severity. -->
  <xsl:template match="node()" mode="event_type_system">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action else 'system'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Alert>
          <Type>
            <xsl:value-of select="if ($action = 'VPN_TUNNEL_DOWN') then 'Network' else if ($action = 'VPN_TUNNEL_UP') then 'Network' else if ($action = 'CONFIG_SAVED') then 'Change' else 'Other'"/>
          </Type>
          <xsl:if test="exists($severity_to_severity?(normalize-space(data[@name='severity']/@value)))">
            <Severity>
              <xsl:value-of select="$severity_to_severity?(normalize-space(data[@name='severity']/@value))[1]"/>
            </Severity>
          </xsl:if>
        </Alert>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'connection', 'logon', 'logoff', 'config change', 'config backup', 'system' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='timestamp']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='timestamp']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssXXX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'connection', 'system' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <xsl:apply-templates select="." mode="system"/>
      <Generator>firewall</Generator>
      <xsl:apply-templates select="." mode="device"/>
    </EventSource>
  </xsl:template>
  <!-- system: shared by the rules 'connection', 'logon', 'logoff', 'config change', 'config backup', 'system' -->
  <xsl:template match="node()" mode="system">
    <System>
      <Name>Edge Firewall</Name>
      <Environment>Eval</Environment>
    </System>
  </xsl:template>
  <!-- device: shared by the rules 'connection', 'logon', 'logoff', 'config change', 'config backup', 'system' -->
  <xsl:template match="node()" mode="device">
    <xsl:if test="normalize-space(data[@name='device']/@value)">
      <Device>
        <HostName>
          <xsl:value-of select="data[@name='device']/@value"/>
        </HostName>
      </Device>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'connection', 'logon', 'logoff', 'config change', 'config backup', 'system' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="normalize-space(data[@name='message']/@value)">
      <Description>
        <xsl:value-of select="data[@name='message']/@value"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- event_source_2: shared by the rules 'logon', 'logoff', 'config change', 'config backup' -->
  <xsl:template match="node()" mode="event_source_2">
    <EventSource>
      <xsl:apply-templates select="." mode="system"/>
      <Generator>firewall</Generator>
      <xsl:apply-templates select="." mode="device"/>
      <xsl:if test="normalize-space(data[@name='src_ip']/@value)">
        <Client>
          <IPAddress>
            <xsl:value-of select="data[@name='src_ip']/@value"/>
          </IPAddress>
        </Client>
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
  <!-- type_id: shared by the rules 'logon', 'logoff', 'config change', 'config backup' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(data[@name='action']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='action']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
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
