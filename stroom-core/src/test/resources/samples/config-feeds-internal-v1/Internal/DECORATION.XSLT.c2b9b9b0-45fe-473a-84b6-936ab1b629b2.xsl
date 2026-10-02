<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="event-logging:3" xmlns:stroom="stroom" version="1.0">
  <xsl:template match="node() | @*">
    <xsl:copy>
      <xsl:apply-templates select="node() | @*" />
    </xsl:copy>
  </xsl:template>
  <xsl:template match="Device">
    <Device>
      <xsl:copy-of select="IPAddress" />
      <xsl:copy-of select="MACAddress" />
      <xsl:if test="Data[@Name='FileNo']/@Value">
        <xsl:variable name="location" select="stroom:lookup('FILENO_TO_LOCATION_MAP', Data[@Name='FileNo']/@Value)" />
        <xsl:if test="$location">
          <xsl:copy-of select="$location" />
        </xsl:if>
      </xsl:if>
    </Device>
  </xsl:template>
</xsl:stylesheet>
