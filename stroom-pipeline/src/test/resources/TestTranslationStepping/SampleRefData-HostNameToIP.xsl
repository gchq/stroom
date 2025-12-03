<?xml version="1.0" encoding="UTF-8" ?>
<!--
  ~ Copyright 2016-2025 Crown Copyright
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

<xsl:stylesheet
        xmlns="reference-data:2"
        xpath-default-namespace="records:2"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        version="2.0">

   <xsl:template match="records">
      <referenceData
         xsi:schemaLocation="reference-data:2 file://reference-data-v2.0.1.xsd"
         version="2.0.1">

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
