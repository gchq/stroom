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
      <xsl:variable name="user" select="mcp:quoted_value(normalize-space(*[@key='body']), 'user')[normalize-space(.)]"/>
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
  <xsl:function name="mcp:quoted_value" as="xs:string?">
    <!-- The value of a key in the text: the regex (?:^|[\s,;])<key>="([^"]*)", its first group -->
    <xsl:param name="text" as="item()*"/>
    <xsl:param name="key" as="xs:string"/>
    <xsl:sequence select="for $g in (analyze-string(string($text[1]), concat('(?:^|[\s,;])', $key, '=&quot;([^&quot;]*)&quot;'))//fn:group[@nr=1])[1] return string($g)"/>
  </xsl:function>
</xsl:stylesheet>
