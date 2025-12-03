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

<xsl:stylesheet xpath-default-namespace="http://www.w3.org/2013/XSL/json" xmlns="reference-data:2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">

  <!-- -->
  <xsl:template match="/">
    <referenceData xsi:schemaLocation="reference-data:2 file://reference-data-v2.0.1.xsd event-logging:3 file://event-logging-v3.0.0.xsd" version="2.0.1">
      <xsl:apply-templates />
    </referenceData>
  </xsl:template>

  <!-- -->
  <xsl:template match="/map/array/map">
    <reference>
      <map>ALL_WORK_NO_PLAY_LANGUAGE_MAP</map>
      <key>
        <xsl:value-of select="lower-case(string[@key='language'])" />
      </key>
      <value>
        <xsl:value-of select="string[@key='text']" />
      </value>
    </reference>
    <reference>
      <map>ALL_WORK_NO_PLAY_SYMBOL_MAP</map>
      <key>
        <xsl:value-of select="lower-case(string[@key='symbol'])" />
      </key>
      <value>
        <xsl:value-of select="string[@key='text']" />
      </value>
    </reference>
  </xsl:template>
</xsl:stylesheet>
