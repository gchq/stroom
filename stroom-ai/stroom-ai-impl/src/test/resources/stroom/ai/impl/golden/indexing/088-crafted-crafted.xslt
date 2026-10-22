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
      <xsl:call-template name="Copy"/>
      <xsl:call-template name="Move"/>
      <xsl:call-template name="View"/>
      <xsl:call-template name="Delete"/>
    </map>
  </xsl:template>
  <!-- Copy: Copy.Outcome.Success from EventDetail/Copy/Outcome/Success; Copy.Outcome.Reason from
       EventDetail/Copy/Outcome/Reason; Copy.Source.Resource.Id from EventDetail/Copy/Source/Resource/Id;
       Copy.Destination.Resource.Id from EventDetail/Copy/Destination/Resource/Id -->
  <xsl:template name="Copy">
    <xsl:if test="EventDetail/Copy/Outcome/Success or EventDetail/Copy/Outcome/Reason or EventDetail/Copy/Source/Resource/Id or EventDetail/Copy/Destination/Resource/Id">
      <map key="Copy">
        <xsl:apply-templates select="(EventDetail/Copy/Outcome)[1]" mode="Outcome"/>
        <xsl:apply-templates select="(EventDetail/Copy/Source)[1]" mode="Source"/>
        <xsl:apply-templates select="(EventDetail/Copy/Destination)[1]" mode="Destination"/>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- Move: Move.Outcome.Success from EventDetail/Move/Outcome/Success; Move.Outcome.Reason from
       EventDetail/Move/Outcome/Reason; Move.Source.Resource.Id from EventDetail/Move/Source/Resource/Id;
       Move.Destination.Resource.Id from EventDetail/Move/Destination/Resource/Id -->
  <xsl:template name="Move">
    <xsl:if test="EventDetail/Move/Outcome/Success or EventDetail/Move/Outcome/Reason or EventDetail/Move/Source/Resource/Id or EventDetail/Move/Destination/Resource/Id">
      <map key="Move">
        <xsl:apply-templates select="(EventDetail/Move/Outcome)[1]" mode="Outcome"/>
        <xsl:apply-templates select="(EventDetail/Move/Source)[1]" mode="Source"/>
        <xsl:apply-templates select="(EventDetail/Move/Destination)[1]" mode="Destination"/>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- View: View.Outcome.Success from EventDetail/View/Outcome/Success; View.Outcome.Reason from
       EventDetail/View/Outcome/Reason -->
  <xsl:template name="View">
    <xsl:if test="EventDetail/View/Outcome/Success or EventDetail/View/Outcome/Reason">
      <map key="View">
        <xsl:apply-templates select="(EventDetail/View/Outcome)[1]" mode="Outcome"/>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- Delete: Delete.Outcome.Success from EventDetail/Delete/Outcome/Success; Delete.Outcome.Reason from
       EventDetail/Delete/Outcome/Reason -->
  <xsl:template name="Delete">
    <xsl:if test="EventDetail/Delete/Outcome/Success or EventDetail/Delete/Outcome/Reason">
      <map key="Delete">
        <xsl:apply-templates select="(EventDetail/Delete/Outcome)[1]" mode="Outcome"/>
      </map>
    </xsl:if>
  </xsl:template>
  <xsl:template match="*" mode="Outcome">
    <xsl:variable name="CopyOutcomeSuccess" select="Success"/>
    <xsl:variable name="CopyOutcomeReason" select="Reason"/>
    <xsl:if test="$CopyOutcomeSuccess or $CopyOutcomeReason">
      <map key="Outcome">
        <xsl:if test="$CopyOutcomeSuccess">
          <boolean key="Success">
            <xsl:value-of select="$CopyOutcomeSuccess"/>
          </boolean>
        </xsl:if>
        <xsl:if test="$CopyOutcomeReason">
          <string key="Reason">
            <xsl:value-of select="$CopyOutcomeReason"/>
          </string>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <xsl:template match="*" mode="Resource">
    <xsl:if test="Id">
      <map key="Resource">
        <string key="Id">
          <xsl:value-of select="Id"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <xsl:template match="*" mode="Source">
    <xsl:if test="Resource/Id">
      <map key="Source">
        <xsl:apply-templates select="(Resource)[1]" mode="Resource"/>
      </map>
    </xsl:if>
  </xsl:template>
  <xsl:template match="*" mode="Destination">
    <xsl:if test="Resource/Id">
      <map key="Destination">
        <xsl:apply-templates select="(Resource)[1]" mode="Resource"/>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
