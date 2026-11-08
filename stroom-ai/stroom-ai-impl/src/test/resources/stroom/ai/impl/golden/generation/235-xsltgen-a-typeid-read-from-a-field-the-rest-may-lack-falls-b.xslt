<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="/">
    <Events Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <!-- Fields of the record no rule reads are logged (mcp_unread_fields) -->
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:variable name="kind" select="*[@key='kind'][normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$kind = 'LOGON'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
        <xsl:call-template name="mcp_kept_unknown">
          <xsl:with-param name="group" select="concat('other|', string-join((string(($kind)[1])), '|'))"/>
          <xsl:with-param name="line" select="concat('Kept as Unknown by rule ''other'': record ', $record-no, ' (', string-join((concat('kind=', string(($kind)[1]))), ' | '), ')')"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="*[@key][not(@key = ('host', 'kind', 'msg', 'time', 'user'))][normalize-space(string(.))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
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
  <!-- Rule 'logon': records where kind = LOGON. Writes an Authenticate event (TypeId from kind) from user. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:if test="*[@key='kind'][normalize-space(.)]">
          <TypeId>
            <xsl:value-of select="(*[@key='kind'])[1]"/>
          </TypeId>
        </xsl:if>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="*[@key='user'][normalize-space(.)]">
            <User>
              <Id>
                <xsl:value-of select="(*[@key='user'])[1]"/>
              </Id>
            </User>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from kind) from msg. Unknown on
       purpose: no action element describes them. -->
  <xsl:template match="node()" mode="event_type_other">
    <!-- other: EventDetail/Unknown on purpose: no action element describes them -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (*[@key='kind'][normalize-space(.)]) then (*[@key='kind'])[1] else 'other'"/>
        </TypeId>
        <Unknown>
          <Data Name="msg" Value="{(*[@key='msg'])[1]}"/>
        </Unknown>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="*[@key='time'][normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="(*[@key='time'])[1]"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Acme</Name>
        <Environment>Prod</Environment>
      </System>
      <Generator>Acme</Generator>
      <xsl:if test="*[@key='host'][normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(*[@key='host'])[1]"/>
          </HostName>
        </Device>
      </xsl:if>
    </EventSource>
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
