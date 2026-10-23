<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xpath-default-namespace="records:2" xmlns="event-logging:3" xmlns:stroom="stroom"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="3.0">
  <xsl:template match="records">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd" Version="4.1.0">
      <xsl:apply-templates />
    </Events>
  </xsl:template>
  <xsl:template match="record">
    <Event>
      <EventTime><TimeCreated><xsl:value-of select="stroom:format-date(data[@name='time']/@value, 'yyyy-MM-dd''T''HH:mm:ss')" /></TimeCreated></EventTime>
      <EventSource>
        <System><Name>E2E</Name><Environment>Dev</Environment></System>
        <Generator>e2e-translation</Generator>
        <Device><HostName><xsl:value-of select="data[@name='host']/@value" /></HostName><IPAddress><xsl:value-of select="data[@name='ip']/@value" /></IPAddress></Device>
        <User><Id><xsl:value-of select="data[@name='user']/@value" /></Id></User>
      </EventSource>
      <EventDetail>
        <TypeId>Logon</TypeId>
        <Description>User logon</Description>
        <Authenticate>
          <Action>Logon</Action>
          <User><Id><xsl:value-of select="data[@name='user']/@value" /></Id></User>
          <Outcome><Success><xsl:value-of select="data[@name='result']/@value = 'ok'" /></Success></Outcome>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
