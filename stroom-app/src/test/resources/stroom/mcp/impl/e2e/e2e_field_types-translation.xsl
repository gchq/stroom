<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xpath-default-namespace="http://www.w3.org/2013/XSL/json" xmlns="event-logging:3" xmlns:stroom="stroom"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="3.0">
  <xsl:template match="/array">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd" Version="4.1.0">
      <xsl:apply-templates />
    </Events>
  </xsl:template>
  <xsl:template match="map">
    <Event>
      <EventTime><TimeCreated><xsl:value-of select="stroom:format-date(string[@key='ts'], 'yyyy-MM-dd''T''HH:mm:ssX')" /></TimeCreated></EventTime>
      <EventSource>
        <System><Name>E2E</Name><Environment>Dev</Environment></System>
        <Generator>e2e-field-types</Generator>
        <Device><HostName><xsl:value-of select="string[@key='host']" /></HostName>
                <IPAddress><xsl:value-of select="string[@key='src']" /></IPAddress></Device>
        <User><Id><xsl:value-of select="string[@key='user']" /></Id></User>
      </EventSource>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <Description><xsl:value-of select="string[@key='msg']" /></Description>
        <Authenticate>
          <Action>Logon</Action>
          <User><Id><xsl:value-of select="string[@key='user']" /></Id></User>
          <Outcome><Success><xsl:value-of select="boolean[@key='ok']" /></Success></Outcome>
          <Data Name="port" Value="{number[@key='port']}" />
          <Data Name="ratio" Value="{number[@key='ratio']}" />
          <Data Name="secret" Value="{string[@key='secret']}" />
          <xsl:for-each select="array[@key='tags']/string"><Data Name="tag" Value="{.}" /></xsl:for-each>
          <xsl:for-each select="array[@key='ports']/number"><Data Name="ports" Value="{.}" /></xsl:for-each>
          <xsl:for-each select="array[@key='peers']/string"><Data Name="peer" Value="{.}" /></xsl:for-each>
          <xsl:for-each select="array[@key='seen']/string">
            <Data Name="seen" Value="{stroom:format-date(., 'yyyy-MM-dd''T''HH:mm:ssX')}" />
          </xsl:for-each>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
