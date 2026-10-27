<?xml version="1.1" encoding="UTF-8" ?>
<!-- Translates the typed test events into the JSON XML documents to index to Elasticsearch -->
<xsl:stylesheet
    xmlns="http://www.w3.org/2005/xpath-functions"
    xmlns:stroom="stroom"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="2.0">

  <xsl:template match="/Events">
    <array>
      <xsl:apply-templates select="Event" />
    </array>
  </xsl:template>

  <!-- An empty document, which the indexing filter skips. The warning is non-fatal, so the other events are still
       indexed, but it gives the stream an Error stream, which a reprocessing filter can select. -->
  <xsl:template match="Event[Skip = 'true']">
    <xsl:value-of select="stroom:log('WARN', 'Skipping an event marked to be skipped')" />
    <map />
  </xsl:template>

  <xsl:template match="Event">
    <map>
      <number key="StreamId"><xsl:value-of select="@StreamId" /></number>
      <number key="EventId"><xsl:value-of select="@EventId" /></number>
      <string key="Time"><xsl:value-of select="Time" /></string>
      <string key="TimeNotIndexed"><xsl:value-of select="Time" /></string>
      <string key="Host"><xsl:value-of select="Host" /></string>
      <string key="Ip"><xsl:value-of select="Ip" /></string>
      <number key="Bytes"><xsl:value-of select="Bytes" /></number>
      <string key="Message"><xsl:value-of select="Message" /></string>
      <!-- Gives the name of the index the document is written to -->
      <number key="year"><xsl:value-of select="Year" /></number>
      <!-- Mapped as `nested`, with a second level of `nested` objects in each user's sessions -->
      <array key="Users">
        <xsl:for-each select="Users/User">
          <map>
            <string key="Name"><xsl:value-of select="Name" /></string>
            <string key="Role"><xsl:value-of select="Role" /></string>
            <xsl:if test="Sessions/Session">
              <array key="Sessions">
                <xsl:for-each select="Sessions/Session">
                  <map>
                    <string key="Device"><xsl:value-of select="Device" /></string>
                  </map>
                </xsl:for-each>
              </array>
            </xsl:if>
          </map>
        </xsl:for-each>
      </array>
      <!-- A nested property with the same name as the index name variable, which must not be used for it -->
      <map key="Meta">
        <number key="year">1999</number>
      </map>
    </map>
  </xsl:template>

  <xsl:template match="text()" />
</xsl:stylesheet>
