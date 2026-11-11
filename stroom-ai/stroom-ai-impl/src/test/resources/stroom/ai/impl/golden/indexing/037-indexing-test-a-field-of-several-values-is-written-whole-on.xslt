<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="records:2" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="3.0">
  <xsl:template match="/Events">
    <records xsi:schemaLocation="records:2 file://records-v2.0.xsd" version="2.0">
      <xsl:apply-templates select="Event"/>
    </records>
  </xsl:template>
  <!-- Each Event: one record for the index x -->
  <xsl:template match="Event">
    <record>
      <data name="StreamId" value="{@StreamId}"/>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:apply-templates select="." mode="event_detail"/>
    </record>
  </xsl:template>
  <!-- event_time: @timestamp from EventTime/TimeCreated -->
  <xsl:template match="Event" mode="event_time">
    <data name="@timestamp" value="{EventTime/TimeCreated}"/>
  </xsl:template>
  <!-- event_source: user.name from EventSource/User/Id -->
  <xsl:template match="Event" mode="event_source">
    <data name="user.name" value="{EventSource/User/Id}"/>
  </xsl:template>
  <!-- event_detail: acme.port from EventDetail/Authenticate/Data[@Name='port']/@Value; acme.tag from
       EventDetail/Authenticate/Data[@Name='tag']/@Value; acme.none from
       EventDetail/Authenticate/Data[@Name='none']/@Value -->
  <xsl:template match="Event" mode="event_detail">
    <xsl:for-each select="EventDetail/Authenticate/Data[@Name='port']/@Value">
      <data name="acme.port" value="{.}"/>
    </xsl:for-each>
    <xsl:for-each select="EventDetail/Authenticate/Data[@Name='tag']/@Value">
      <data name="acme.tag" value="{.}"/>
    </xsl:for-each>
    <xsl:for-each select="EventDetail/Authenticate/Data[@Name='none']/@Value">
      <data name="acme.none" value="{.}"/>
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>
