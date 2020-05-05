<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="records:2" xmlns="records:2" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="2.0">

  <!-- This test data is public data from https://www.kaggle.com/city-of-seattle/seattle-broadband-speed-test -->

  <!-- Ingest the Records tree -->
  <xsl:template match="records">
    <records xsi:schemaLocation="records:2 file://records-v2.0.xsd">
      <xsl:apply-templates />
    </records>
  </xsl:template>

  <!-- Main record template for single Evt event -->
  <xsl:template match="record">

    <!-- Build the record element -->
    <record>
      <xsl:apply-templates select="./data" />
    </record>
  </xsl:template>

  <!-- re-format the date-->
  <xsl:template match="data[@name='timestamp']">
    <data>
      <xsl:attribute name="name" select="./@name" />

      <!-- timestamp in epoch seconds, but we need millis -->
      <xsl:attribute name="value" select="stroom:format-date((concat(./@value,'000')))" />
    </data>
  </xsl:template>

  <!-- re-format the date-->
  <xsl:template match="data[@name='actual_download' or @name='actual_upload' or @name='advertised_download' or @name='advertised_upload']">
    <data>
      <xsl:attribute name="name" select="./@name" />

      <!-- timestamp in epoch seconds, but we need millis -->
      <xsl:if test="./@value">
        <xsl:attribute name="value" select="xs:integer(xs:decimal(./@value) * 1000000)" />
      </xsl:if>
    </data>
  </xsl:template>

  <!-- drop certain data elements-->
  <xsl:template match="data[@name='date_pretty']">
  </xsl:template>

  <!-- copy data elements-->
  <xsl:template match="data">
    <xsl:copy-of select="." />
  </xsl:template>
</xsl:stylesheet>
