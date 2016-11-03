<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="event-logging:3"
	xmlns="records:2" xmlns:stroom="stroom"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	version="2.0">
	<xsl:template match="/Events">
		<records xsi:schemaLocation="records:2 file://records-v2.0.xsd"
			version="2.0">
			<xsl:apply-templates />
		</records>
	</xsl:template>
	<xsl:template match="EventDetail">
		<xsl:apply-templates mode="text" />
	</xsl:template>
	<xsl:template match="text()" mode="text">
		<record>
			<data name="Text">
				<xsl:attribute name="value" select="." />
			</data>
		</record>
	</xsl:template>
</xsl:stylesheet>
