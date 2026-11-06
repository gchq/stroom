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
    <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
    <xsl:variable name="event_type" select="data[@name='event_type']/@value[normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$event_type = 'TRAFFIC'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_connection"/>
      </xsl:when>
      <xsl:when test="$event_type = 'ADMIN' and $action = ('LOGIN_SUCCESS', 'LOGIN_FAILED')">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$event_type = 'ADMIN' and $action = 'LOGOUT'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:when test="$event_type = 'ADMIN' and $action = 'CONFIG_CHANGE'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_config_change"/>
      </xsl:when>
      <xsl:when test="$event_type = 'ADMIN' and $action = 'CONFIG_BACKUP'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_config_backup"/>
      </xsl:when>
      <xsl:when test="$event_type = 'SYSTEM'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_system"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('event_type=', string(($event_type)[1])), concat('action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('action', 'bytes_received', 'bytes_sent', 'device', 'dst_ip', 'dst_port', 'event_type', 'message', 'protocol', 'rule_id', 'severity', 'src_ip', 'src_port', 'timestamp', 'username'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
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
          <xsl:value-of select="if ($action) then $action[1] else 'connection'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:variable name="rule_id" select="data[@name='rule_id']/@value[normalize-space(.)]"/>
        <xsl:variable name="dst_port" select="data[@name='dst_port']/@value[normalize-space(.)]"/>
        <xsl:variable name="protocol" select="data[@name='protocol']/@value[normalize-space(.)]"/>
        <xsl:variable name="dst_ip" select="data[@name='dst_ip']/@value[normalize-space(.)]"/>
        <xsl:variable name="src_port" select="data[@name='src_port']/@value[normalize-space(.)]"/>
        <xsl:variable name="src_ip" select="data[@name='src_ip']/@value[normalize-space(.)]"/>
        <xsl:if test="$src_ip or $src_port or $dst_ip or $protocol or $dst_port or $rule_id or $action[1] = ('ALLOW', 'DENY') or data[@name='bytes_sent']/@value[normalize-space(.)] or data[@name='bytes_received']/@value[normalize-space(.)]">
          <Network>
            <Open>
              <xsl:if test="$src_ip or $src_port">
                <Source>
                  <xsl:if test="$src_ip">
                    <Device>
                      <IPAddress>
                        <xsl:value-of select="$src_ip[1]"/>
                      </IPAddress>
                    </Device>
                  </xsl:if>
                  <xsl:if test="$src_port">
                    <Port>
                      <xsl:value-of select="$src_port[1]"/>
                    </Port>
                  </xsl:if>
                </Source>
              </xsl:if>
              <xsl:if test="$dst_ip or $protocol or $dst_port">
                <Destination>
                  <xsl:if test="$dst_ip">
                    <Device>
                      <IPAddress>
                        <xsl:value-of select="$dst_ip[1]"/>
                      </IPAddress>
                    </Device>
                  </xsl:if>
                  <xsl:if test="$protocol">
                    <TransportProtocol>
                      <xsl:value-of select="$protocol[1]"/>
                    </TransportProtocol>
                  </xsl:if>
                  <xsl:if test="$dst_port">
                    <Port>
                      <xsl:value-of select="$dst_port[1]"/>
                    </Port>
                  </xsl:if>
                </Destination>
              </xsl:if>
              <xsl:if test="$rule_id">
                <Rule>
                  <xsl:value-of select="$rule_id[1]"/>
                </Rule>
              </xsl:if>
              <xsl:if test="$action[1] = ('ALLOW', 'DENY')">
                <Outcome>
                  <Permitted>
                    <xsl:value-of select="if ($action[1] = 'ALLOW') then 'true' else 'false'"/>
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
          <xsl:if test="(data[@name='action']/@value)[1] = ('LOGIN_SUCCESS', 'LOGIN_FAILED')">
            <Outcome>
              <Success>
                <xsl:value-of select="if ((data[@name='action']/@value)[1] = 'LOGIN_SUCCESS') then 'true' else 'false'"/>
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
          <xsl:value-of select="if ($action) then $action[1] else 'system'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Alert>
          <Type>
            <xsl:value-of select="if ($action[1] = 'VPN_TUNNEL_DOWN') then 'Network' else if ($action[1] = 'VPN_TUNNEL_UP') then 'Network' else if ($action[1] = 'CONFIG_SAVED') then 'Change' else 'Other'"/>
          </Type>
          <xsl:if test="exists($severity_to_severity?((data[@name='severity']/@value)[1]))">
            <Severity>
              <xsl:value-of select="$severity_to_severity?((data[@name='severity']/@value)[1])[1]"/>
            </Severity>
          </xsl:if>
        </Alert>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'connection', 'logon', 'logoff', 'config change', 'config backup', 'system' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='timestamp']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((data[@name='timestamp']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssXXX')"/>
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
    <xsl:if test="data[@name='device']/@value[normalize-space(.)]">
      <Device>
        <HostName>
          <xsl:value-of select="(data[@name='device']/@value)[1]"/>
        </HostName>
      </Device>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'connection', 'logon', 'logoff', 'config change', 'config backup', 'system' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="data[@name='message']/@value[normalize-space(.)]">
      <Description>
        <xsl:value-of select="(data[@name='message']/@value)[1]"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- event_source_2: shared by the rules 'logon', 'logoff', 'config change', 'config backup' -->
  <xsl:template match="node()" mode="event_source_2">
    <EventSource>
      <xsl:apply-templates select="." mode="system"/>
      <Generator>firewall</Generator>
      <xsl:apply-templates select="." mode="device"/>
      <xsl:if test="data[@name='src_ip']/@value[normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="(data[@name='src_ip']/@value)[1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="data[@name='username']/@value[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(data[@name='username']/@value)[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'logoff', 'config change', 'config backup' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="data[@name='action']/@value[normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="(data[@name='action']/@value)[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="data[@name='username']/@value[normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="(data[@name='username']/@value)[1]"/>
        </Id>
      </User>
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
