<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="records:2" xmlns="reference-data:2"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version="2.0">
    <xsl:template match="records">
        <referenceData xsi:schemaLocation="reference-data:2 file://reference-data-v2.0.1.xsd event-logging:3 file://event-logging-v3.0.0.xsd" version="2.0.1">
            <xsl:apply-templates/>
        </referenceData>
    </xsl:template>

    <xsl:template match="record">
        <reference>
            <map>USER_ID_TO_STAFF_NO_MAP</map>
            <key><xsl:value-of select="data[@name='userId']/@value"/></key>
            <value><xsl:value-of select="data[@name='staffNo']/@value"/></value>
        </reference>
    </xsl:template>
    
</xsl:stylesheet>
