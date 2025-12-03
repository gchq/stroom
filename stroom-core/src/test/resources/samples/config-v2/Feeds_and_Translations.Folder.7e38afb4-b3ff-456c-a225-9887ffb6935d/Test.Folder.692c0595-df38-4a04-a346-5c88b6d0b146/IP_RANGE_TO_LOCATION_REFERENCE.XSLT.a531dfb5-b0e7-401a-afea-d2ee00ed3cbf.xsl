<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xpath-default-namespace="records:2" xmlns="reference-data:2" xmlns:stroom="stroom"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version="2.0">
  <xsl:template match="records">
      <referenceData xsi:schemaLocation="reference-data:2 file://reference-data-v2.0.xsd" version="2.0.1">
      <xsl:apply-templates />
    </referenceData>
  </xsl:template>
  <xsl:template match="record">
    <xsl:variable name="zone" select="data[@name = 'zone']/@value" />
    <xsl:choose>
      <xsl:when test="data[@name = 'from']">
        <xsl:variable name="from" select="stroom:numeric-ip(data[@name = 'from']/@value)" />
        <xsl:variable name="to" select="stroom:numeric-ip(data[@name = 'to']/@value)" />
        <xsl:choose>
          <xsl:when test="$from le $to">
            <reference>
              <map>IPToLocation</map>
              <range>
                <from><xsl:value-of select="$from" /></from>
                <to><xsl:value-of select="$to" /></to>
              </range>
              <value><xsl:value-of select="$zone" /></value>
            </reference>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="stroom:log('ERROR', concat('Range from ', $from, ' must be less than or equal to range to ', $to, ' for zone ''', $zone, ''''))" />
          </xsl:otherwise>
        </xsl:choose>
      </xsl:when>
      <xsl:otherwise>
        <reference>
          <map>IPToLocation</map>
          <key><xsl:value-of select="stroom:numeric-ip(data[@name = 'key']/@value)" /></key>
          <value><xsl:value-of select="$zone" /></value>
        </reference>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>
