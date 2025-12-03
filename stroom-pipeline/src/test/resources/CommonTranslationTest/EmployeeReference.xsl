<?xml version="1.1" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="records:2" xmlns="reference-data:2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
   <xsl:template match="records">
      <referenceData xsi:schemaLocation="reference-data:2 file://reference-data-v2.0.1.xsd" version="2.0.1">
         <xsl:apply-templates/>
      </referenceData>
   </xsl:template>

   <xsl:template match="record">
      <xsl:if test="data[@name='Number']/@value and data[@name='UserId']/@value">
         <reference>
            <map>NUMBER_TO_ID</map>
            <key><xsl:value-of select="number(data[@name='Number']/@value)"/></key>
            <value><xsl:value-of select="data[@name='UserId']/@value"/></value>
         </reference>
      </xsl:if>
   </xsl:template>
</xsl:stylesheet>
