<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="*/Event" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="system_event_id" select="(System/EventID)[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$system_event_id = '4624'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4625'">
        <xsl:apply-templates select="." mode="event_type_failed_logon"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4634'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('System/EventID=', string(($system_event_id)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where System/EventID = 4624. Writes an Authenticate event (TypeId from System/EventID). -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Description>An account was successfully logged on</Description>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:sequence select="mcp:data('LogonType', EventData/Data[@Name='LogonType'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'failed logon': records where System/EventID = 4625. Writes an Authenticate event (TypeId from
       System/EventID). -->
  <xsl:template match="node()" mode="event_type_failed_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Description>An account failed to log on</Description>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <Outcome>
            <Success>false</Success>
          </Outcome>
          <xsl:sequence select="mcp:data('LogonType', EventData/Data[@Name='LogonType'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where System/EventID = 4634. Writes an Authenticate event (TypeId from System/EventID). -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Description>An account was logged off</Description>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:sequence select="mcp:data('LogonType', EventData/Data[@Name='LogonType'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'failed logon', 'logoff' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="(System/TimeCreated/@SystemTime)[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(System/TimeCreated/@SystemTime[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'failed logon', 'logoff' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Workstations</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>Security</Generator>
      <xsl:if test="(System/Computer)[normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="System/Computer"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="(EventData/Data[@Name='IpAddress'])[normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="EventData/Data[@Name='IpAddress']"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="EventData/Data[@Name='TargetUserName']"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'failed logon', 'logoff' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="(System/EventID)[normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="System/EventID"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'failed logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="EventData/Data[@Name='TargetUserName']"/>
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
