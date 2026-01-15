<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="event-logging:3"
                xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:stroom="stroom" version="2.0">
  <xsl:character-map name="tab">
    <xsl:output-character character="&#009;" string="&#009;"/>
  </xsl:character-map>
  <xsl:output method="text" omit-xml-declaration="yes" use-character-maps="tab"/>
  <xsl:variable name="tab" select="''"/>
  <xsl:strip-space elements="*"/>

  <xsl:variable name="searchId" select="stroom:search-id()"/>
  <xsl:variable name="startTime"
                select="format-dateTime(current-dateTime(), '[Y,4]-[M,2]-[D,2]T[H,2]:[m,2]:[s,2].[f,3]Z')"/>

  <!-- Template for producing final XML output -->
  <xsl:template match="node()">
    <xsl:copy-of select="."/>
  </xsl:template>
</xsl:stylesheet>
