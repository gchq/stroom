<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet 
   xmlns="reference-data:2"
   xpath-default-namespace="records:2"
   xmlns:evt="event-logging:3"
   xmlns:stroom="stroom"
   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
   xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
   version="2.0">
   
   <xsl:template match="records">
      <referenceData xsi:schemaLocation="reference-data:2 file://reference-data-v2.0.1.xsd" version="2.0.1">
         <xsl:apply-templates/>
      </referenceData>
   </xsl:template>

   <xsl:template match="record">
      <reference>
         <map>HOSTNAME_TO_IP_MAP</map>
         <key><xsl:value-of select="data[@name='Host Name']/@value"/></key>
         <value><xsl:value-of select="data[@name='IP Address']/@value"/></value>
      </reference>   
   </xsl:template>
   
</xsl:stylesheet>
