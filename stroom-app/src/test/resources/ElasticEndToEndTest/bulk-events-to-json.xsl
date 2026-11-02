<?xml version="1.1" encoding="UTF-8" ?>
<!-- Translates the generated bulk test events, each with a single Value, into the JSON XML documents to index to
     Elasticsearch -->
<xsl:stylesheet
    xmlns="http://www.w3.org/2005/xpath-functions"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="2.0">

  <xsl:template match="/Events">
    <array>
      <xsl:apply-templates select="Event" />
    </array>
  </xsl:template>

  <xsl:template match="Event">
    <map>
      <number key="StreamId"><xsl:value-of select="@StreamId" /></number>
      <number key="EventId"><xsl:value-of select="@EventId" /></number>
      <!-- Written as a string, so that a value that isn't a number reaches Elasticsearch to be rejected -->
      <string key="Value"><xsl:value-of select="Value" /></string>
    </map>
  </xsl:template>

  <xsl:template match="text()" />
</xsl:stylesheet>
