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

<xsl:stylesheet xpath-default-namespace="event-logging:3"
                xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:stroom="stroom" version="2.0">
  <xsl:character-map name="tab">
    <xsl:output-character character="&#009;" string="&#009;"/>
  </xsl:character-map>
  <xsl:output method="text" omit-xml-declaration="yes" use-character-maps="tab"/>
  <xsl:variable name="tab" select="''"/>
  <xsl:strip-space elements="*"/>

  <xsl:variable name="searchId" select="stroom:search-id()"/>
  <xsl:variable name="startTime"
                select="format-dateTime(current-dateTime(), '[Y,4]-[M,2]-[D,2]T[H,2]:[m,2]:[s,2].[f,3]Z')"/>

  <!-- Template for producing final XML output -->
  <xsl:template match="node()">
    <xsl:copy-of select="."/>
  </xsl:template>
</xsl:stylesheet>
