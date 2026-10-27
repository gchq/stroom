<?xml version="1.1" encoding="UTF-8" ?>
<!-- Extracts the field values of conditions-events.xml, each element of an event being a field -->
<xsl:stylesheet
    xmlns="records:2"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    version="2.0">

  <xsl:template match="/Events">
    <records xsi:schemaLocation="records:2 file://records-v2.0.xsd" version="2.0">
      <xsl:apply-templates select="Event" />
    </records>
  </xsl:template>

  <xsl:template match="Event">
    <record>
      <data name="StreamId" value="{@StreamId}" />
      <data name="EventId" value="{@EventId}" />
      <xsl:for-each select="*">
        <data name="{local-name()}" value="{.}" />
      </xsl:for-each>
    </record>
  </xsl:template>
</xsl:stylesheet>
