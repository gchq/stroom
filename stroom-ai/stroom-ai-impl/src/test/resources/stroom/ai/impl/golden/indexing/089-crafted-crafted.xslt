<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="3.0">
  <xsl:template match="/Events">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="Event"/>
    </array>
  </xsl:template>
  <!-- Each Event: one document for the index x -->
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
      <xsl:if test="EventDetail/Copy/Outcome/Success or EventDetail/Copy/Outcome/Reason or EventDetail/Copy/Source/Resource/Id or EventDetail/Copy/Destination/Resource/Id">
        <map key="Copy">
          <xsl:apply-templates select="(EventDetail/Copy/Outcome)[1]" mode="outcome"/>
          <xsl:apply-templates select="(EventDetail/Copy/Source)[1]" mode="source"/>
          <xsl:apply-templates select="(EventDetail/Copy/Destination)[1]" mode="destination"/>
        </map>
      </xsl:if>
      <xsl:if test="EventDetail/Move/Outcome/Success or EventDetail/Move/Outcome/Reason or EventDetail/Move/Source/Resource/Id or EventDetail/Move/Destination/Resource/Id">
        <map key="Move">
          <xsl:apply-templates select="(EventDetail/Move/Outcome)[1]" mode="outcome"/>
          <xsl:apply-templates select="(EventDetail/Move/Source)[1]" mode="source"/>
          <xsl:apply-templates select="(EventDetail/Move/Destination)[1]" mode="destination"/>
        </map>
      </xsl:if>
      <xsl:if test="EventDetail/View/Outcome/Success or EventDetail/View/Outcome/Reason">
        <map key="View">
          <xsl:apply-templates select="(EventDetail/View/Outcome)[1]" mode="outcome"/>
        </map>
      </xsl:if>
      <xsl:if test="EventDetail/Delete/Outcome/Success or EventDetail/Delete/Outcome/Reason">
        <map key="Delete">
          <xsl:apply-templates select="(EventDetail/Delete/Outcome)[1]" mode="outcome"/>
        </map>
      </xsl:if>
    </map>
  </xsl:template>
  <xsl:template match="*" mode="outcome">
    <xsl:variable name="copy_outcome_success" select="Success"/>
    <xsl:variable name="copy_outcome_reason" select="Reason"/>
    <xsl:if test="$copy_outcome_success or $copy_outcome_reason">
      <map key="Outcome">
        <xsl:if test="$copy_outcome_success">
          <boolean key="Success">
            <xsl:value-of select="$copy_outcome_success"/>
          </boolean>
        </xsl:if>
        <xsl:if test="$copy_outcome_reason">
          <string key="Reason">
            <xsl:value-of select="$copy_outcome_reason"/>
          </string>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <xsl:template match="*" mode="resource">
    <xsl:if test="Id">
      <map key="Resource">
        <string key="Id">
          <xsl:value-of select="Id"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <xsl:template match="*" mode="source">
    <xsl:if test="Resource/Id">
      <map key="Source">
        <xsl:apply-templates select="(Resource)[1]" mode="resource"/>
      </map>
    </xsl:if>
  </xsl:template>
  <xsl:template match="*" mode="destination">
    <xsl:if test="Resource/Id">
      <map key="Destination">
        <xsl:apply-templates select="(Resource)[1]" mode="resource"/>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
