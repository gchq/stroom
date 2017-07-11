<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet
        xmlns="reference-data:2"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        version="2.0">
   
   <xsl:template match="SomeContext">
      <referenceData 
          xsi:schemaLocation="event-logging:3 file://event-logging-v3.0.0.xsd reference-data:2 file://reference-data-v2.0.1.xsd"
          version="2.0.1">
             
         <xsl:apply-templates/>
      </referenceData>
   </xsl:template>

   <xsl:template match="Machine">
      <reference>
         <map>CONTEXT</map>
         <key>Machine</key>
         <value><xsl:value-of select="."/></value>
      </reference>
   </xsl:template>
   
</xsl:stylesheet>
