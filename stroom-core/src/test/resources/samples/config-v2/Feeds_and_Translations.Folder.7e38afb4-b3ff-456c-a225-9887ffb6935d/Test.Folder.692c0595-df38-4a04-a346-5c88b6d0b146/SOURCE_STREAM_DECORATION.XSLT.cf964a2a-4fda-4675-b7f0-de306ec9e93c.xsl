<xsl:stylesheet xpath-default-namespace="event-logging:3" xmlns:sm="stroom-meta" xmlns="event-logging:3"
                xmlns:stroom="stroom" version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()" />
    </xsl:copy>
  </xsl:template>
  <xsl:template match="Event/Meta[not(sm:source)]">
    <xsl:copy>
      <xsl:apply-templates />
      <xsl:copy-of select="stroom:source()" />
    </xsl:copy>
  </xsl:template>
  <!-- Need to handle multiple Meta elmements, 
  see https://github.com/gchq/event-logging-schema/issues/62 -->
  <xsl:template match="Event[not(Meta)]">
    <xsl:copy>
      <xsl:element name="Meta">
        <xsl:copy-of select="stroom:source()" />
      </xsl:element>
      <xsl:apply-templates />
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>
