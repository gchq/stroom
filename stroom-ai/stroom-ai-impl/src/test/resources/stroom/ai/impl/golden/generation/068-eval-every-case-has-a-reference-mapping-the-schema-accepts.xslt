<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
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
        <xsl:apply-templates select="." mode="event_type_create"/>
      </xsl:when>
      <xsl:when test="$action = ('update', 'close', 'reopen')">
        <xsl:apply-templates select="." mode="event_type_change"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
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
            <xsl:if test="normalize-space(data[@name='ticket']/@value)">
              <Id>
                <xsl:value-of select="data[@name='ticket']/@value"/>
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
              <xsl:if test="normalize-space(data[@name='ticket']/@value)">
                <Id>
                  <xsl:value-of select="data[@name='ticket']/@value"/>
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
    <xsl:if test="normalize-space(data[@name='time']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
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
      <xsl:if test="normalize-space(data[@name='user']/@value)">
        <User>
          <Id>
            <xsl:value-of select="data[@name='user']/@value"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'create', 'change' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(data[@name='action']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='action']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'create', 'change' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="normalize-space(data[@name='comment']/@value)">
      <Description>
        <xsl:value-of select="replace(data[@name='comment']/@value[1], '&quot;&quot;', '&quot;')"/>
      </Description>
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
