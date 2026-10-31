<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="3.0">
  <xsl:template match="/Events">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="Event"/>
    </array>
  </xsl:template>
  <!-- Each Event: one document for the index metrics-v1 -->
  <xsl:template match="Event">
    <map>
      <xsl:if test="@StreamId">
        <number key="StreamId">
          <xsl:value-of select="@StreamId"/>
        </number>
      </xsl:if>
      <xsl:if test="@EventId">
        <number key="EventId">
          <xsl:value-of select="@EventId"/>
        </number>
      </xsl:if>
      <xsl:if test="EventTime/TimeCreated">
        <string key="@timestamp">
          <xsl:value-of select="EventTime/TimeCreated"/>
        </string>
      </xsl:if>
      <xsl:if test="EventDetail/Data[@Name=&quot;time&quot;]/@Value">
        <string key="time">
          <xsl:value-of select="EventDetail/Data[@Name=&quot;time&quot;]/@Value"/>
        </string>
      </xsl:if>
      <xsl:if test="EventDetail/Data[@Name=&quot;min&quot;]/@Value">
        <string key="time.min">
          <xsl:value-of select="EventDetail/Data[@Name=&quot;min&quot;]/@Value"/>
        </string>
      </xsl:if>
      <xsl:apply-templates select="." mode="user"/>
    </map>
  </xsl:template>
  <!-- user: user.id from EventSource/User/Id -->
  <xsl:template match="Event" mode="user">
    <xsl:if test="EventSource/User/Id">
      <map key="user">
        <string key="id">
          <xsl:value-of select="EventSource/User/Id"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
