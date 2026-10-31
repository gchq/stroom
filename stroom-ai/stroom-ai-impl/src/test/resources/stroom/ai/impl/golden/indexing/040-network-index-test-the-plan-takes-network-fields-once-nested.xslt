<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="3.0">
  <xsl:template match="/Events">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="Event"/>
    </array>
  </xsl:template>
  <!-- Each Event: one document for the index fw -->
  <xsl:template match="Event">
    <map>
      <xsl:if test="@StreamId">
        <number key="StreamId">
          <xsl:value-of select="@StreamId"/>
        </number>
      </xsl:if>
      <xsl:if test="EventTime/TimeCreated">
        <string key="@timestamp">
          <xsl:value-of select="EventTime/TimeCreated"/>
        </string>
      </xsl:if>
      <xsl:if test="EventSource/Device/HostName">
        <string key="HostName">
          <xsl:value-of select="EventSource/Device/HostName"/>
        </string>
      </xsl:if>
      <xsl:apply-templates select="." mode="source"/>
      <xsl:apply-templates select="." mode="destination"/>
      <xsl:apply-templates select="." mode="user"/>
      <xsl:if test="EventDetail/TypeId">
        <string key="TypeId">
          <xsl:value-of select="EventDetail/TypeId"/>
        </string>
      </xsl:if>
      <xsl:if test="EventDetail/*/Action">
        <string key="*Action">
          <xsl:value-of select="EventDetail/*/Action"/>
        </string>
      </xsl:if>
    </map>
  </xsl:template>
  <!-- source: Source.IPAddress from EventDetail/Network/*/Source/Device/IPAddress; Source.Port from
       EventDetail/Network/*/Source/Port -->
  <xsl:template match="Event" mode="source">
    <xsl:variable name="source_ip_address" select="EventDetail/Network/*/Source/Device/IPAddress"/>
    <xsl:variable name="source_port" select="EventDetail/Network/*/Source/Port"/>
    <xsl:if test="$source_ip_address or $source_port">
      <map key="Source">
        <xsl:if test="$source_ip_address">
          <string key="IPAddress">
            <xsl:value-of select="$source_ip_address"/>
          </string>
        </xsl:if>
        <xsl:if test="$source_port">
          <number key="Port">
            <xsl:value-of select="$source_port"/>
          </number>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- destination: Destination.IPAddress from EventDetail/Network/*/Destination/Device/IPAddress -->
  <xsl:template match="Event" mode="destination">
    <xsl:if test="EventDetail/Network/*/Destination/Device/IPAddress">
      <map key="Destination">
        <string key="IPAddress">
          <xsl:value-of select="EventDetail/Network/*/Destination/Device/IPAddress"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- user: User.Id from EventSource/User/Id -->
  <xsl:template match="Event" mode="user">
    <xsl:if test="EventSource/User/Id">
      <map key="User">
        <string key="Id">
          <xsl:value-of select="EventSource/User/Id"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
