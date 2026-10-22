<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="" exclude-result-prefixes="stroom xs">
  <xsl:template match="events">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="event" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="action" select="(@action)[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$action = 'lock'">
        <xsl:apply-templates select="." mode="event_type_lock"/>
      </xsl:when>
      <xsl:when test="$action = 'unlock'">
        <xsl:apply-templates select="." mode="event_type_unlock"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('@action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'lock': records where @action = lock. Writes an Authenticate event (TypeId from @action). -->
  <xsl:template match="node()" mode="event_type_lock">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>ScreenLock</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'unlock': records where @action = unlock. Writes an Authenticate event (TypeId from @action). -->
  <xsl:template match="node()" mode="event_type_unlock">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>ScreenUnlock</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'lock', 'unlock' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="(@time)[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(@time[1], 'yyyy-MM-dd HH:mm:ss', 'UTC')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'lock', 'unlock' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Workstations</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>session-agent</Generator>
      <xsl:if test="(@host)[normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="@host"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="(@user)[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="@user"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'lock', 'unlock' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="(@action)[normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="@action"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'lock', 'unlock' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="(@user)[normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="@user"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
