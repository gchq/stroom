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
      <xsl:apply-templates select="." mode="copy"/>
      <xsl:apply-templates select="." mode="move"/>
      <xsl:apply-templates select="." mode="view"/>
      <xsl:apply-templates select="." mode="delete"/>
      <xsl:apply-templates select="." mode="alert"/>
    </map>
  </xsl:template>
  <!-- copy: Copy.Source.Resource.Id from EventDetail/Copy/Source/Resource/Id; Copy.Source.Resource.Type from
       EventDetail/Copy/Source/Resource/Type; Copy.Destination.Resource.Id from
       EventDetail/Copy/Destination/Resource/Id; Copy.Destination.Resource.Type from
       EventDetail/Copy/Destination/Resource/Type; Copy.Outcome.Success from EventDetail/Copy/Outcome/Success -->
  <xsl:template match="Event" mode="copy">
    <xsl:if test="EventDetail/Copy/Source/Resource/Id or EventDetail/Copy/Source/Resource/Type or EventDetail/Copy/Destination/Resource/Id or EventDetail/Copy/Destination/Resource/Type or EventDetail/Copy/Outcome/Success">
      <map key="Copy">
        <xsl:apply-templates select="(EventDetail/Copy/Source)[1]" mode="source"/>
        <xsl:apply-templates select="(EventDetail/Copy/Destination)[1]" mode="destination"/>
        <xsl:apply-templates select="(EventDetail/Copy/Outcome)[1]" mode="outcome"/>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- move: Move.Source.Resource.Id from EventDetail/Move/Source/Resource/Id; Move.Source.Resource.Type from
       EventDetail/Move/Source/Resource/Type; Move.Destination.Resource.Id from
       EventDetail/Move/Destination/Resource/Id; Move.Destination.Resource.Type from
       EventDetail/Move/Destination/Resource/Type; Move.Outcome.Success from EventDetail/Move/Outcome/Success -->
  <xsl:template match="Event" mode="move">
    <xsl:if test="EventDetail/Move/Source/Resource/Id or EventDetail/Move/Source/Resource/Type or EventDetail/Move/Destination/Resource/Id or EventDetail/Move/Destination/Resource/Type or EventDetail/Move/Outcome/Success">
      <map key="Move">
        <xsl:apply-templates select="(EventDetail/Move/Source)[1]" mode="source"/>
        <xsl:apply-templates select="(EventDetail/Move/Destination)[1]" mode="destination"/>
        <xsl:apply-templates select="(EventDetail/Move/Outcome)[1]" mode="outcome"/>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- view: View.Resource.Id from EventDetail/View/Resource/Id; View.Resource.Type from
       EventDetail/View/Resource/Type; View.Outcome.Success from EventDetail/View/Outcome/Success -->
  <xsl:template match="Event" mode="view">
    <xsl:if test="EventDetail/View/Resource/Id or EventDetail/View/Resource/Type or EventDetail/View/Outcome/Success">
      <map key="View">
        <xsl:apply-templates select="(EventDetail/View/Resource)[1]" mode="resource"/>
        <xsl:apply-templates select="(EventDetail/View/Outcome)[1]" mode="outcome"/>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- delete: Delete.Resource.Id from EventDetail/Delete/Resource/Id; Delete.Resource.Type from
       EventDetail/Delete/Resource/Type; Delete.Outcome.Success from EventDetail/Delete/Outcome/Success -->
  <xsl:template match="Event" mode="delete">
    <xsl:if test="EventDetail/Delete/Resource/Id or EventDetail/Delete/Resource/Type or EventDetail/Delete/Outcome/Success">
      <map key="Delete">
        <xsl:apply-templates select="(EventDetail/Delete/Resource)[1]" mode="resource"/>
        <xsl:apply-templates select="(EventDetail/Delete/Outcome)[1]" mode="outcome"/>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- alert: Alert.Severity from EventDetail/Alert/Severity -->
  <xsl:template match="Event" mode="alert">
    <xsl:if test="EventDetail/Alert/Severity">
      <map key="Alert">
        <string key="Severity">
          <xsl:value-of select="EventDetail/Alert/Severity"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <xsl:template match="*" mode="resource">
    <xsl:variable name="copy_source_resource_id" select="Id"/>
    <xsl:variable name="copy_source_resource_type" select="Type"/>
    <xsl:if test="$copy_source_resource_id or $copy_source_resource_type">
      <map key="Resource">
        <xsl:if test="$copy_source_resource_id">
          <string key="Id">
            <xsl:value-of select="$copy_source_resource_id"/>
          </string>
        </xsl:if>
        <xsl:if test="$copy_source_resource_type">
          <string key="Type">
            <xsl:value-of select="$copy_source_resource_type"/>
          </string>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <xsl:template match="*" mode="source">
    <xsl:if test="Resource/Id or Resource/Type">
      <map key="Source">
        <xsl:apply-templates select="(Resource)[1]" mode="resource"/>
      </map>
    </xsl:if>
  </xsl:template>
  <xsl:template match="*" mode="destination">
    <xsl:if test="Resource/Id or Resource/Type">
      <map key="Destination">
        <xsl:apply-templates select="(Resource)[1]" mode="resource"/>
      </map>
    </xsl:if>
  </xsl:template>
  <xsl:template match="*" mode="outcome">
    <xsl:if test="Success">
      <map key="Outcome">
        <boolean key="Success">
          <xsl:value-of select="Success"/>
        </boolean>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
