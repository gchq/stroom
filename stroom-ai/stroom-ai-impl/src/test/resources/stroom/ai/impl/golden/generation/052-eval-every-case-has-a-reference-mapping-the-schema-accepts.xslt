<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
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
      <xsl:when test="$action = 'create'">
        <xsl:apply-templates select="." mode="event_type_created"/>
      </xsl:when>
      <xsl:when test="$action = 'view'">
        <xsl:apply-templates select="." mode="event_type_viewed"/>
      </xsl:when>
      <xsl:when test="$action = ('update', 'close')">
        <xsl:apply-templates select="." mode="event_type_updated"/>
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
      <xsl:for-each select="data[@name][not(@name = ('action', 'agent', 'comment', 'customer', 'ticket', 'time'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'created': records where action = create. Writes a Create event (TypeId from action) from ticket,
       customer. -->
  <xsl:template match="node()" mode="event_type_created">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Create>
          <Object>
            <Type>Ticket</Type>
            <xsl:apply-templates select="." mode="id"/>
            <xsl:if test="data[@name='customer']/@value[normalize-space(.)]">
              <Data Name="customer" Value="{replace((data[@name='customer']/@value)[1], '&quot;&quot;', '&quot;')}"/>
            </xsl:if>
          </Object>
        </Create>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'viewed': records where action = view. Writes a View event (TypeId from action) from ticket. -->
  <xsl:template match="node()" mode="event_type_viewed">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <View>
          <Object>
            <Type>Ticket</Type>
            <xsl:apply-templates select="." mode="id"/>
          </Object>
        </View>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'updated': records where action in update, close. Writes an Update event (TypeId from action) from ticket,
       action. -->
  <xsl:template match="node()" mode="event_type_updated">
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
              <xsl:if test="(data[@name='action']/@value)[1] = ('close', 'update')">
                <State>
                  <xsl:value-of select="if ((data[@name='action']/@value)[1] = 'close') then 'Closed' else 'Open'"/>
                </State>
              </xsl:if>
            </Object>
          </After>
        </Update>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'created', 'viewed', 'updated' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((data[@name='time']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'created', 'viewed', 'updated' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Helpdesk</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>helpdesk-audit</Generator>
      <Device>
        <HostName>helpdesk.example.org</HostName>
      </Device>
      <xsl:if test="data[@name='agent']/@value[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(data[@name='agent']/@value)[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'created', 'viewed', 'updated' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="data[@name='action']/@value[normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="(data[@name='action']/@value)[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'created', 'viewed', 'updated' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="data[@name='comment']/@value[normalize-space(.)]">
      <Description>
        <xsl:value-of select="replace((data[@name='comment']/@value)[1], '&quot;&quot;', '&quot;')"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- id: shared by the rules 'created', 'viewed' -->
  <xsl:template match="node()" mode="id">
    <xsl:if test="data[@name='ticket']/@value[normalize-space(.)]">
      <Id>
        <xsl:value-of select="(data[@name='ticket']/@value)[1]"/>
      </Id>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
