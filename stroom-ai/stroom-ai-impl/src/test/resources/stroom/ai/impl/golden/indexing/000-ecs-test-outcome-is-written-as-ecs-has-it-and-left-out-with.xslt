<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="3.0">
  <xsl:template match="/Events">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="Event"/>
    </array>
  </xsl:template>
  <!-- Each Event: one document for the index ecs-acme-v1 -->
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
      <xsl:apply-templates select="." mode="event"/>
    </map>
  </xsl:template>
  <!-- event: event.action from EventDetail/*/Action; event.outcome from EventDetail/*/Outcome/Success -->
  <xsl:template match="Event" mode="event">
    <xsl:variable name="event_action" select="EventDetail/*/Action"/>
    <xsl:variable name="event_outcome" select="EventDetail/*/Outcome/Success"/>
    <xsl:if test="$event_action or $event_outcome">
      <map key="event">
        <xsl:if test="$event_action">
          <string key="action">
            <xsl:value-of select="$event_action"/>
          </string>
        </xsl:if>
        <xsl:if test="$event_outcome">
          <string key="outcome">
            <xsl:value-of select="$event_outcome ! (if (. = 'false') then 'failure' else 'success')"/>
          </string>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
