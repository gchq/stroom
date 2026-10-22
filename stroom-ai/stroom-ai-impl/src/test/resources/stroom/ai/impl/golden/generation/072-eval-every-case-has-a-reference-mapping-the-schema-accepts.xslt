<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="event_action" select="*[@key='event']/*[@key='action'][normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$event_action = 'user-login'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$event_action = 'file-delete'">
        <xsl:apply-templates select="." mode="event_type_file_delete"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('event.action=', string(($event_action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where event.action = user-login. Writes an Authenticate event (TypeId from event.action)
       from user.name, event.outcome. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="normalize-space(*[@key='user']/*[@key='name'])">
            <User>
              <Id>
                <xsl:value-of select="*[@key='user']/*[@key='name']"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="*[@key='event']/*[@key='outcome'] = ('success', 'failure')">
            <Outcome>
              <Success>
                <xsl:value-of select="if (*[@key='event']/*[@key='outcome'] = 'success') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'file-delete': records where event.action = file-delete. Writes a Delete event (TypeId from event.action)
       from file.path, file.name. -->
  <xsl:template match="node()" mode="event_type_file_delete">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Delete>
          <Object>
            <Type>File</Type>
            <xsl:if test="normalize-space(*[@key='file']/*[@key='path'])">
              <Id>
                <xsl:value-of select="*[@key='file']/*[@key='path']"/>
              </Id>
            </xsl:if>
            <xsl:if test="normalize-space(*[@key='file']/*[@key='name'])">
              <Name>
                <xsl:value-of select="*[@key='file']/*[@key='name']"/>
              </Name>
            </xsl:if>
          </Object>
        </Delete>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'file-delete' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(*[@key='@timestamp'])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(*[@key='@timestamp'][1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'file-delete' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Endpoint Agent</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>ecs-shipper</Generator>
      <xsl:if test="normalize-space(*[@key='host']/*[@key='name'])">
        <Device>
          <HostName>
            <xsl:value-of select="*[@key='host']/*[@key='name']"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="normalize-space(*[@key='source']/*[@key='ip'])">
        <Client>
          <IPAddress>
            <xsl:value-of select="*[@key='source']/*[@key='ip']"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="normalize-space(*[@key='user']/*[@key='name'])">
        <User>
          <Id>
            <xsl:value-of select="*[@key='user']/*[@key='name']"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'file-delete' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(*[@key='event']/*[@key='action'])">
      <TypeId>
        <xsl:value-of select="*[@key='event']/*[@key='action']"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
