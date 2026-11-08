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
      <xsl:when test="$action = 'accept'">
        <xsl:apply-templates select="." mode="event_type_permitted"/>
      </xsl:when>
      <xsl:when test="$action = 'deny'">
        <xsl:apply-templates select="." mode="event_type_denied"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
        <xsl:call-template name="mcp_log_kind">
          <xsl:with-param name="group" select="concat('other|', string-join((string(($action)[1])), '|'))"/>
          <xsl:with-param name="line" select="concat('Caught by rule ''other'': record ', $record-no, ' (', string-join((concat('action=', string(($action)[1]))), ' | '), ')')"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('action', 'date', 'devname', 'dstip', 'dstport', 'logid', 'srcip', 'time'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- mcp_log_kind: a record a rule keeps as Unknown on purpose, or a catch-all rule takes, logged once a stream for
       each kind (the rule and the values the rules test) -->
  <xsl:template name="mcp_log_kind">
    <xsl:param name="group"/>
    <xsl:param name="line"/>
    <xsl:if test="not(string(stroom:get(concat('mcp-kind:', $group))))">
      <xsl:value-of select="stroom:put(concat('mcp-kind:', $group), 'y')"/>
      <xsl:sequence select="stroom:log('INFO', $line)"/>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'permitted': records where action = accept. Writes a Network/Permit event (TypeId from action) from srcip,
       dstip, dstport, logid. -->
  <xsl:template match="node()" mode="event_type_permitted">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="logid" select="data[@name='logid']/@value[normalize-space(.)]"/>
      <xsl:if test="data[@name='action']/@value[normalize-space(.)] or data[@name='srcip']/@value[normalize-space(.)] or data[@name='dstip']/@value[normalize-space(.)] or data[@name='dstport']/@value[normalize-space(.)] or $logid">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:if test="data[@name='srcip']/@value[normalize-space(.)] or data[@name='dstip']/@value[normalize-space(.)] or data[@name='dstport']/@value[normalize-space(.)] or $logid">
            <Network>
              <Permit>
                <xsl:apply-templates select="." mode="source"/>
                <xsl:apply-templates select="." mode="destination"/>
                <xsl:sequence select="mcp:data('logid', $logid)"/>
              </Permit>
            </Network>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'denied': records where action = deny. Writes a Network/Deny event (TypeId from action) from srcip, dstip,
       dstport, logid. -->
  <xsl:template match="node()" mode="event_type_denied">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="logid" select="data[@name='logid']/@value[normalize-space(.)]"/>
      <xsl:if test="data[@name='action']/@value[normalize-space(.)] or data[@name='srcip']/@value[normalize-space(.)] or data[@name='dstip']/@value[normalize-space(.)] or data[@name='dstport']/@value[normalize-space(.)] or $logid">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:if test="data[@name='srcip']/@value[normalize-space(.)] or data[@name='dstip']/@value[normalize-space(.)] or data[@name='dstport']/@value[normalize-space(.)] or $logid">
            <Network>
              <Deny>
                <xsl:apply-templates select="." mode="source"/>
                <xsl:apply-templates select="." mode="destination"/>
                <xsl:sequence select="mcp:data('logid', $logid)"/>
              </Deny>
            </Network>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from action) from logid. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (data[@name='action']/@value[normalize-space(.)]) then (data[@name='action']/@value)[1] else 'other'"/>
        </TypeId>
        <Unknown>
          <Data Name="logid" Value="{(data[@name='logid']/@value)[1]}"/>
        </Unknown>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'permitted', 'denied', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:variable name="event_time_time_created" select="concat(data[@name='date']/@value, ' ', data[@name='time']/@value)[normalize-space(.)]"/>
    <xsl:if test="exists($event_time_time_created)">
      <EventTime>
        <TimeCreated>
          <xsl:if test="count($event_time_time_created) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($event_time_time_created), ') for ', 'EventTime/TimeCreated, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="stroom:format-date($event_time_time_created[1], 'yyyy-MM-dd HH:mm:ss', 'UTC')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'permitted', 'denied', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>FortiOS</Name>
        <Environment>TODO e.g. Prod</Environment>
      </System>
      <Generator>FortiOS</Generator>
      <xsl:if test="data[@name='devname']/@value[normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(data[@name='devname']/@value)[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="data[@name='srcip']/@value[normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="(data[@name='srcip']/@value)[1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:variable name="dstport" select="data[@name='dstport']/@value[normalize-space(.)]"/>
      <xsl:variable name="dstip" select="data[@name='dstip']/@value[normalize-space(.)]"/>
      <xsl:if test="$dstip or $dstport">
        <Server>
          <xsl:if test="$dstip">
            <IPAddress>
              <xsl:value-of select="$dstip[1]"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$dstport">
            <Port>
              <xsl:value-of select="$dstport[1]"/>
            </Port>
          </xsl:if>
        </Server>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'permitted', 'denied' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="data[@name='action']/@value[normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="(data[@name='action']/@value)[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- source: shared by the rules 'permitted', 'denied' -->
  <xsl:template match="node()" mode="source">
    <xsl:if test="data[@name='srcip']/@value[normalize-space(.)]">
      <Source>
        <Device>
          <IPAddress>
            <xsl:value-of select="(data[@name='srcip']/@value)[1]"/>
          </IPAddress>
        </Device>
      </Source>
    </xsl:if>
  </xsl:template>
  <!-- destination: shared by the rules 'permitted', 'denied' -->
  <xsl:template match="node()" mode="destination">
    <xsl:variable name="dstport" select="data[@name='dstport']/@value[normalize-space(.)]"/>
    <xsl:variable name="dstip" select="data[@name='dstip']/@value[normalize-space(.)]"/>
    <xsl:if test="$dstip or $dstport">
      <Destination>
        <xsl:if test="$dstip">
          <Device>
            <IPAddress>
              <xsl:value-of select="$dstip[1]"/>
            </IPAddress>
          </Device>
        </xsl:if>
        <xsl:if test="$dstport">
          <Port>
            <xsl:value-of select="$dstport[1]"/>
          </Port>
        </xsl:if>
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
