<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="http://www.w3.org/2013/XSL/json" xmlns="reference-data:2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

  <!-- -->
  <xsl:template match="/">
      <referenceData xsi:schemaLocation="reference-data:2 file://reference-data-v2.0.1.xsd" version="2.0.1">
      <xsl:apply-templates />
    </referenceData>
  </xsl:template>

  <!-- -->
  <xsl:template match="/map/array/map">
    <reference>
      <map>ALL_WORK_NO_PLAY_LANGUAGE_MAP</map>
      <key>
        <xsl:value-of select="lower-case(string[@key='language'])" />
      </key>
      <value>
        <xsl:value-of select="string[@key='text']" />
      </value>
    </reference>
    <reference>
      <map>ALL_WORK_NO_PLAY_SYMBOL_MAP</map>
      <key>
        <xsl:value-of select="lower-case(string[@key='symbol'])" />
      </key>
      <value>
        <xsl:value-of select="string[@key='text']" />
      </value>
    </reference>
  </xsl:template>
</xsl:stylesheet>
