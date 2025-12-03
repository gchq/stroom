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

<xsl:stylesheet xpath-default-namespace="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="event-logging:3" xmlns:stroom="stroom" version="1.0">
  <xsl:template match="node() | @*">
    <xsl:copy>
      <xsl:apply-templates select="node() | @*" />
    </xsl:copy>
  </xsl:template>
  <xsl:template match="Device">
    <Device>
      <xsl:copy-of select="IPAddress" />
      <xsl:copy-of select="MACAddress" />
      <xsl:if test="Data[@Name='FileNo']/@Value">
        <xsl:variable name="location" select="stroom:lookup('FILENO_TO_LOCATION_MAP', Data[@Name='FileNo']/@Value)" />
        <xsl:if test="$location">
          <xsl:copy-of select="$location" />
        </xsl:if>
      </xsl:if>
    </Device>
  </xsl:template>
</xsl:stylesheet>
