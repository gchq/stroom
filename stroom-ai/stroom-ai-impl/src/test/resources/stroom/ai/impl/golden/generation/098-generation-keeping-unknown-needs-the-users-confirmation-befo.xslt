<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
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
    <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$action = 'login'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$action = 'status'">
        <xsl:apply-templates select="." mode="event_type_status"/>
        <xsl:call-template name="mcp_kept_unknown">
          <xsl:with-param name="group" select="concat('status|', string-join((string(($action)[1])), '|'))"/>
          <xsl:with-param name="line" select="concat('Kept as Unknown by rule ''status'': record ', $record-no, ' (', string-join((concat('action=', string(($action)[1]))), ' | '), ')')"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('action', 'host', 'result', 'sid', 'time', 'user'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- mcp_kept_unknown: a record a rule keeps as Unknown on purpose, logged once a stream for each kind (the rule and
       the values the rules test) -->
  <xsl:template name="mcp_kept_unknown">
    <xsl:param name="group"/>
    <xsl:param name="line"/>
    <xsl:variable name="mcp_kept" select="concat('mcp-unknown:', $group)"/>
    <xsl:if test="not(string(stroom:get($mcp_kept)))">
      <xsl:value-of select="stroom:put($mcp_kept, 'y')"/>
      <xsl:sequence select="stroom:log('INFO', $line)"/>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'logon': records where action = login. Writes an Authenticate event (TypeId Logon) from user, result, sid. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="data[@name='user']/@value[normalize-space(.)]">
            <User>
              <Id>
                <xsl:value-of select="(data[@name='user']/@value)[1]"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="(data[@name='result']/@value)[1] = ('ok', 'fail')">
            <Outcome>
              <Success>
                <xsl:value-of select="if ((data[@name='result']/@value)[1] = 'ok') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
          <xsl:sequence select="mcp:data('session', data[@name='sid']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'status': records where action = status. Writes an Unknown event (TypeId status) from action. Unknown on
       purpose: status lines carry no activity. -->
  <xsl:template match="node()" mode="event_type_status">
    <!-- status: EventDetail/Unknown on purpose: status lines carry no activity -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>status</TypeId>
        <xsl:apply-templates select="." mode="unknown"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from action) from action. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (data[@name='action']/@value[normalize-space(.)]) then (data[@name='action']/@value)[1] else 'other'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="unknown"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'status', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="(data[@name='time']/@value)[1]"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'status', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Acme VPN</Name>
        <Environment>Dev</Environment>
      </System>
      <Generator>vpnd</Generator>
      <Device>
        <HostName>
          <xsl:value-of select="if (data[@name='host']/@value[normalize-space(.)]) then (data[@name='host']/@value)[1] else 'unknown'"/>
        </HostName>
      </Device>
      <xsl:if test="data[@name='user']/@value[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(data[@name='user']/@value)[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- unknown: shared by the rules 'status', 'other' -->
  <xsl:template match="node()" mode="unknown">
    <xsl:if test="data[@name='action']/@value[normalize-space(.)]">
      <Unknown>
        <Data Name="action" Value="{(data[@name='action']/@value)[1]}"/>
      </Unknown>
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
