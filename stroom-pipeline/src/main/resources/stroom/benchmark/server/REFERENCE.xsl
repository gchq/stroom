<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet 
   xpath-default-namespace="records:2"
   xmlns="reference-data:2"
   xmlns:evt="event-logging:3"
   xmlns:stroom="stroom"
   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
   xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
   version="2.0">
    
    <!--
       ~ Copyright 2016 Crown Copyright
       ~
       ~ Licensed under the Apache License, Version 2.0 (the "License");
       ~ you may not use this file except in compliance with the License.
       ~ You may obtain a copy of the License at
       ~
       ~     http://www.apache.org/licenses/LICENSE-2.0
       ~
       ~ Unless required by applicable law or agreed to in writing, software
       ~ distributed under the License is distributed on an "AS IS" BASIS,
       ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
       ~ See the License for the specific language governing permissions and
       ~ limitations under the License.
       -->

    <xsl:template match="records">
      <referenceData xsi:schemaLocation="reference-data:2 file://reference-data-v2.0.1.xsd event-logging:3 file://event-logging-v3.0.0.xsd" version="2.0.1">
         <xsl:apply-templates/>
      </referenceData>
   </xsl:template>

   <xsl:template match="record">
      <reference>
         <map>FILENO_TO_LOCATION_MAP</map>
         <key><xsl:value-of select="data[@name='FileNo']/@value"/></key>
         <value>
            <evt:Location>
              <evt:Country><xsl:value-of select="data[@name='Country']/@value"/></evt:Country>
              <evt:Site><xsl:value-of select="data[@name='Site']/@value"/></evt:Site>
              <evt:Building><xsl:value-of select="data[@name='Building']/@value"/></evt:Building>
              <evt:Floor><xsl:value-of select="data[@name='Floor']/@value"/></evt:Floor>              
              <evt:Room><xsl:value-of select="data[@name='Room']/@value"/></evt:Room>
              <evt:Desk><xsl:value-of select="data[@name='Desk']/@value"/></evt:Desk>
            </evt:Location>
         </value>
      </reference>   
   </xsl:template>
   
</xsl:stylesheet>
