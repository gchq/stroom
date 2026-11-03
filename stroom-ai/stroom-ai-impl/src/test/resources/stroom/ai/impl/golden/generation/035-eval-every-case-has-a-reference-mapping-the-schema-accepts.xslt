<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_ssh_logon"/>
  </xsl:template>
  <!-- Rule 'ssh logon': every record. Writes an Authenticate event (TypeId SSH-Logon) from user, result, method. -->
  <xsl:template match="node()" mode="event_type_ssh_logon">
    <Event>
      <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date((data[@name='time']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="user" select="data[@name='user']/@value[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>Bastion</Name>
          <Environment>Eval</Environment>
        </System>
        <xsl:if test="data[@name='app']/@value[normalize-space(.)]">
          <Generator>
            <xsl:value-of select="(data[@name='app']/@value)[1]"/>
          </Generator>
        </xsl:if>
        <xsl:if test="data[@name='host']/@value[normalize-space(.)]">
          <Device>
            <HostName>
              <xsl:value-of select="(data[@name='host']/@value)[1]"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:variable name="port" select="data[@name='port']/@value[normalize-space(.)]"/>
        <xsl:variable name="ip" select="data[@name='ip']/@value[normalize-space(.)]"/>
        <xsl:if test="$ip or $port">
          <Client>
            <xsl:if test="$ip">
              <IPAddress>
                <xsl:value-of select="$ip[1]"/>
              </IPAddress>
            </xsl:if>
            <xsl:if test="$port">
              <Port>
                <xsl:value-of select="$port[1]"/>
              </Port>
            </xsl:if>
          </Client>
        </xsl:if>
        <xsl:if test="$user">
          <User>
            <Id>
              <xsl:value-of select="$user[1]"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>SSH-Logon</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="$user">
            <User>
              <Id>
                <xsl:value-of select="$user[1]"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="(data[@name='result']/@value)[1] = ('Accepted', 'Failed')">
            <Outcome>
              <Success>
                <xsl:value-of select="if ((data[@name='result']/@value)[1] = 'Accepted') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
          <xsl:sequence select="mcp:data('method', data[@name='method']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value[1]}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
