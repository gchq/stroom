<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="action" select="*[@key='_action'][normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$action = 'login'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$action = 'logout'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:when test="$action = 'disk_alert'">
        <xsl:apply-templates select="." mode="event_type_disk"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('_action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where _action = login. Writes an Authenticate event (TypeId from _action) from _user. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where _action = logout. Writes an Authenticate event (TypeId from _action) from _user. -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
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
  <!-- Rule 'disk': records where _action = disk_alert. Writes an Alert event (TypeId from _action) from
       short_message, _disk, _pct. -->
  <xsl:template match="node()" mode="event_type_disk">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Alert>
          <Type>Other</Type>
          <xsl:if test="normalize-space(*[@key='short_message'])">
            <Description>
              <xsl:value-of select="*[@key='short_message']"/>
            </Description>
          </xsl:if>
          <xsl:sequence select="mcp:data('disk', *[@key='_disk'])"/>
          <xsl:sequence select="mcp:data('percent_used', *[@key='_pct'])"/>
        </Alert>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff', 'disk' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(*[@key='timestamp'])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(string(xs:integer(floor(xs:decimal(*[@key='timestamp'][1]) * 1000))))"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'logoff', 'disk' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Billing App</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>gelf</Generator>
      <xsl:if test="normalize-space(*[@key='host'])">
        <Device>
          <HostName>
            <xsl:value-of select="*[@key='host']"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="normalize-space(*[@key='_src_ip'])">
        <Client>
          <IPAddress>
            <xsl:value-of select="*[@key='_src_ip']"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="normalize-space(*[@key='_user'])">
        <User>
          <Id>
            <xsl:value-of select="*[@key='_user']"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'logoff', 'disk' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(*[@key='_action'])">
      <TypeId>
        <xsl:value-of select="*[@key='_action']"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'logon', 'logoff', 'disk' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="exists(((*[@key='full_message'], *[@key='short_message'])[normalize-space(.)][1])[normalize-space(.)])">
      <Description>
        <xsl:value-of select="((*[@key='full_message'], *[@key='short_message'])[normalize-space(.)][1])"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="normalize-space(*[@key='_user'])">
      <User>
        <Id>
          <xsl:value-of select="*[@key='_user']"/>
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
