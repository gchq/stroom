<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
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
            <xsl:if test="normalize-space(data[@name='customer']/@value)">
              <Data Name="customer" Value="{replace(data[@name='customer']/@value[1], '&quot;&quot;', '&quot;')}"/>
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
              <xsl:if test="normalize-space(data[@name='ticket']/@value)">
                <Id>
                  <xsl:value-of select="data[@name='ticket']/@value"/>
                </Id>
              </xsl:if>
              <xsl:if test="data[@name='action']/@value = ('close', 'update')">
                <State>
                  <xsl:value-of select="if (data[@name='action']/@value = 'close') then 'Closed' else 'Open'"/>
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
    <xsl:if test="normalize-space(data[@name='time']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
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
      <xsl:if test="normalize-space(data[@name='agent']/@value)">
        <User>
          <Id>
            <xsl:value-of select="data[@name='agent']/@value"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'created', 'viewed', 'updated' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(data[@name='action']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='action']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'created', 'viewed', 'updated' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="normalize-space(data[@name='comment']/@value)">
      <Description>
        <xsl:value-of select="replace(data[@name='comment']/@value[1], '&quot;&quot;', '&quot;')"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- id: shared by the rules 'created', 'viewed' -->
  <xsl:template match="node()" mode="id">
    <xsl:if test="normalize-space(data[@name='ticket']/@value)">
      <Id>
        <xsl:value-of select="data[@name='ticket']/@value"/>
      </Id>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
