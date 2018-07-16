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
    </Device>
  </xsl:template>
</xsl:stylesheet>