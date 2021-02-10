<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="records:2" xmlns="records:2" xmlns:stroom="stroom"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                version="2.0">

  <!-- Ingest the Evts tree -->
  <xsl:template match="records">
    <records xsi:schemaLocation="records:2 file://records-v2.0.xsd">
      <xsl:apply-templates />
    </records>
  </xsl:template>

  <!-- Main record template for single Evt event -->
  <xsl:template match="record">

    <!-- Build the record element -->
    <record>

      <!-- now copy the existing data -->
      <xsl:apply-templates select="./data" />

      <!-- Add stream ID -->
      <data name="StreamId">
        <xsl:attribute name="value" select="@StreamId" />
      </data>

      <!-- Add event ID -->
      <data name="EventId">
        <xsl:attribute name="value" select="@EventId" />
      </data>

      <!-- Add feed -->
      <data name="Feed">
        <xsl:attribute name="value" select="stroom:feed-name()" />
      </data>
      <data name="Feed (Keyword)">
        <xsl:attribute name="value" select="stroom:feed-name()" />
      </data>
    </record>
  </xsl:template>

  <!-- ignore certain data elements-->
  <xsl:template match="data[@name='id']">
  </xsl:template>
  <xsl:template match="data[@name='seattle_blkgrpce10']">
  </xsl:template>
  <xsl:template match="data[@name='cost_of_service']">
  </xsl:template>

  <!-- copy data elements-->
  <xsl:template match="data">
    <xsl:copy-of select="." />
  </xsl:template>
</xsl:stylesheet>
