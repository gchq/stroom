<?xml version="1.1" encoding="UTF-8" ?>
<!-- Translates the sample network monitoring events into the JSON XML documents to index to Elasticsearch -->
<xsl:stylesheet
    xpath-default-namespace="event-logging:3"
    xmlns="http://www.w3.org/2005/xpath-functions"
    xmlns:stroom="stroom"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="2.0">

  <xsl:template match="/Events">
    <array>
      <xsl:apply-templates select="Event" />
    </array>
  </xsl:template>

  <xsl:template match="Event">
    <map>
      <number key="StreamId"><xsl:value-of select="@StreamId" /></number>
      <number key="EventId"><xsl:value-of select="@EventId" /></number>
      <string key="Feed"><xsl:value-of select="stroom:feed-name()" /></string>
      <string key="EventTime"><xsl:value-of select="EventTime/TimeCreated" /></string>
      <string key="UserId"><xsl:value-of select="EventSource/User/Id" /></string>
      <string key="HostName"><xsl:value-of select="EventSource/Device/HostName" /></string>
      <string key="IPAddress"><xsl:value-of select="EventSource/Device/IPAddress" /></string>
      <string key="Action">
        <xsl:value-of select="name(EventDetail/*[not(self::TypeId or self::Description)][1])" />
      </string>
      <xsl:if test="EventDetail/Process/Command">
        <string key="Command"><xsl:value-of select="EventDetail/Process/Command" /></string>
      </xsl:if>
      <string key="Description"><xsl:value-of select="(EventDetail//Outcome/Description)[1]" /></string>
    </map>
  </xsl:template>

  <xsl:template match="text()" />
</xsl:stylesheet>
