<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="data[@name='action']/@value = 'login'">
        <xsl:apply-templates select="." mode="eventTypeLogon"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="eventTypeOther"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where action = login. Writes an Authenticate event (TypeId Logon) from user, result, sid. -->
  <xsl:template match="node()" mode="eventTypeLogon">
    <Event>
      <xsl:apply-templates select="." mode="eventTime"/>
      <xsl:apply-templates select="." mode="eventSource"/>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="normalize-space(data[@name='user']/@value)">
            <User>
              <Id>
                <xsl:value-of select="data[@name='user']/@value"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="data[@name='result']/@value = ('ok', 'fail')">
            <Outcome>
              <Success>
                <xsl:value-of select="if (data[@name='result']/@value = 'ok') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
          <xsl:sequence select="mcp:data('session', data[@name='sid']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from action) from action. -->
  <xsl:template match="node()" mode="eventTypeOther">
    <Event>
      <xsl:apply-templates select="." mode="eventTime"/>
      <xsl:apply-templates select="." mode="eventSource"/>
      <EventDetail>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action else 'other'"/>
        </TypeId>
        <xsl:if test="$action">
          <Unknown>
            <Data Name="action" Value="{$action}"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- eventTime: shared by the rules 'logon', 'other' -->
  <xsl:template match="node()" mode="eventTime">
    <xsl:if test="normalize-space(data[@name='time']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="data[@name='time']/@value"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- eventSource: shared by the rules 'logon', 'other' -->
  <xsl:template match="node()" mode="eventSource">
    <EventSource>
      <System>
        <Name>Acme VPN</Name>
        <Environment>Dev</Environment>
      </System>
      <Generator>vpnd</Generator>
      <Device>
        <HostName>
          <xsl:value-of select="if (normalize-space(data[@name='host']/@value)) then data[@name='host']/@value else 'unknown'"/>
        </HostName>
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
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
