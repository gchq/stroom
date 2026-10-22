<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_logon"/>
  </xsl:template>
  <!-- Rule 'logon': every record. Writes an Authenticate event (TypeId Logon) from user, result, sid (user extracted
       from body). -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:if test="normalize-space(*[@key='time'])">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="*[@key='time']"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="user_parts" select="analyze-string(string(*[@key='body'][1]), '(?:^|[\s,;])user=&quot;([^&quot;]*)&quot;')"/>
      <xsl:variable name="user" select="$user_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>FortiOS</Name>
          <Environment>Test</Environment>
        </System>
        <Generator>fortigate</Generator>
        <xsl:if test="normalize-space(*[@key='host'])">
          <Device>
            <HostName>
              <xsl:value-of select="*[@key='host']"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="$user">
          <User>
            <Id>
              <xsl:value-of select="$user"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="$user">
            <User>
              <Id>
                <xsl:value-of select="$user"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="*[@key='result'] = ('ok', 'fail')">
            <Outcome>
              <Success>
                <xsl:value-of select="if (*[@key='result'] = 'ok') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
          <xsl:sequence select="mcp:data('session', *[@key='sid'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
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
