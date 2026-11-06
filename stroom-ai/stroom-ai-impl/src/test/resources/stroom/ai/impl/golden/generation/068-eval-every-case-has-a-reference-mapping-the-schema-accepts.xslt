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
    <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$action = 'create'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_create"/>
      </xsl:when>
      <xsl:when test="$action = ('update', 'close', 'reopen')">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_change"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('action', 'comment', 'priority', 'ticket', 'time', 'user'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'create': records where action = create. Writes a Create event (TypeId from action) from ticket, priority. -->
  <xsl:template match="node()" mode="event_type_create">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Create>
          <Object>
            <Type>Ticket</Type>
            <xsl:if test="data[@name='ticket']/@value[normalize-space(.)]">
              <Id>
                <xsl:value-of select="(data[@name='ticket']/@value)[1]"/>
              </Id>
            </xsl:if>
          </Object>
          <xsl:sequence select="mcp:data('priority', data[@name='priority']/@value)"/>
        </Create>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'change': records where action in update, close, reopen. Writes an Update event (TypeId from action) from
       ticket, action, priority. -->
  <xsl:template match="node()" mode="event_type_change">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Update>
          <After>
            <Object>
              <Type>Ticket</Type>
              <xsl:if test="data[@name='ticket']/@value[normalize-space(.)]">
                <Id>
                  <xsl:value-of select="(data[@name='ticket']/@value)[1]"/>
                </Id>
              </xsl:if>
            </Object>
          </After>
          <xsl:sequence select="mcp:data('action', data[@name='action']/@value)"/>
          <xsl:sequence select="mcp:data('priority', data[@name='priority']/@value)"/>
        </Update>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'create', 'change' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((data[@name='time']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'create', 'change' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Service Desk</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>service-desk-export</Generator>
      <Device>
        <HostName>servicedesk01</HostName>
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
  <!-- type_id: shared by the rules 'create', 'change' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="data[@name='action']/@value[normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="(data[@name='action']/@value)[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'create', 'change' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="data[@name='comment']/@value[normalize-space(.)]">
      <Description>
        <xsl:value-of select="replace((data[@name='comment']/@value)[1], '&quot;&quot;', '&quot;')"/>
      </Description>
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
