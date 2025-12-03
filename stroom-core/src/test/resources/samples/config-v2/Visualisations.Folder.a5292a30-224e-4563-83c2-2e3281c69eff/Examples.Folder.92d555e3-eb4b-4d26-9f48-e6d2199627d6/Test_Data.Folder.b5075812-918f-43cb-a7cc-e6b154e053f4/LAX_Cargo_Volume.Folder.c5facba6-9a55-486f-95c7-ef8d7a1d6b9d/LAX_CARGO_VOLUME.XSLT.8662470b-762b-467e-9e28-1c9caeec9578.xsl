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

<xsl:stylesheet xpath-default-namespace="records:2" xmlns="records:2" xmlns:stroom="stroom"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                version="2.0">

  <!-- This test data is public data from https://www.kaggle.com/cityofLA/los-angeles-international-airport-data -->

  <!-- Ingest the Evts tree -->
  <xsl:template match="records">
    <records xsi:schemaLocation="records:2 file://records-v2.0.xsd">
      <xsl:apply-templates />
    </records>
  </xsl:template>

  <!-- Main record template for single Evt event -->
  <xsl:template match="record">

    <!-- Build the record element -->
    <record>
      <xsl:apply-templates select="./data" />
    </record>
  </xsl:template>

  <!-- re-format the date-->
  <xsl:template match="data[@name='DataExtractDate' or @name='ReportPeriod']">
    <data>
      <xsl:attribute name="name" select="./@name" />
      <xsl:attribute name="value" select="stroom:format-date(./@value, 'yyyy-MM-dd''T''HH:mm:ss')" />
    </data>
  </xsl:template>

  <!-- copy data elements-->
  <xsl:template match="data">
    <xsl:copy-of select="." />
  </xsl:template>
</xsl:stylesheet>
