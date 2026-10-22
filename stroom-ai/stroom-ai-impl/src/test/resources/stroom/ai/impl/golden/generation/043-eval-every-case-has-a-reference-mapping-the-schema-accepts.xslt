<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$action = 'login'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$action = ('logout', 'timeout')">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where action = login. Writes an Authenticate event (TypeId VPN-Logon) from result. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>VPN-Logon</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:if test="data[@name='result']/@value = ('ok', 'fail')">
            <Outcome>
              <Success>
                <xsl:value-of select="if (data[@name='result']/@value = 'ok') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where action in logout, timeout. Writes an Authenticate event (TypeId from action) from
       action. -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (data[@name='action']/@value = 'logout') then 'VPN-Logoff' else if (data[@name='action']/@value = 'timeout') then 'VPN-Timeout' else 'logoff'"/>
        </TypeId>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="exists(((data[@name='time']/@value, data[@name='timestamp']/@value)[normalize-space(.)][1])[normalize-space(.)])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(((data[@name='time']/@value, data[@name='timestamp']/@value)[normalize-space(.)][1])[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Acme VPN</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>vpnd</Generator>
      <Device>
        <HostName>
          <xsl:value-of select="if (normalize-space(data[@name='device']/@value)) then data[@name='device']/@value else 'vpn-gw-1'"/>
        </HostName>
      </Device>
      <xsl:if test="normalize-space(data[@name='src_ip']/@value)">
        <Client>
          <IPAddress>
            <xsl:value-of select="data[@name='src_ip']/@value"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="exists(((data[@name='user']/@value, data[@name='username']/@value)[normalize-space(.)][1])[normalize-space(.)])">
        <User>
          <Id>
            <xsl:value-of select="((data[@name='user']/@value, data[@name='username']/@value)[normalize-space(.)][1])"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="exists(((data[@name='user']/@value, data[@name='username']/@value)[normalize-space(.)][1])[normalize-space(.)])">
      <User>
        <Id>
          <xsl:value-of select="((data[@name='user']/@value, data[@name='username']/@value)[normalize-space(.)][1])"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
