<?xml version="1.1" encoding="UTF-8" ?>

<!-- UK Crown Copyright © 2016 -->
<xsl:stylesheet xpath-default-namespace="records:2" xmlns="records:2" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.0">
  <xsl:template match="records">
    <records xsi:schemaLocation="records:2 file://records-v2.0.0.xsd">
      <xsl:apply-templates />
    </records>
  </xsl:template>
  <xsl:template match="record">
    <record>
      <xsl:variable name="user" select="data[@name='user']/@value" />
      <xsl:variable name="time" select="data[@name='time']/@value" />
      <xsl:variable name="formattedTime" select="stroom:format-date($time, 'yyyy-MM-dd''T''HH:mm:ss', 'Z', 'yyyy-MM-dd''T''HH:mm:ss.SSSXX', 'Z')" />
      <xsl:copy-of select="data[@name='user']" />
      <xsl:copy-of select="data[@name='time']" />
      <data>
        <xsl:attribute name="name">formattedTime</xsl:attribute>
        <xsl:attribute name="value">
          <xsl:value-of select="$formattedTime" />
        </xsl:attribute>
      </data>
      <data>
        <xsl:attribute name="name">lookupResult</xsl:attribute>
        <xsl:attribute name="value">

          <!-- We have 10 different ref streams each with a different eff date -->
          <xsl:value-of select="stroom:lookup('USER_TO_EFF_DATE_MAP', $user, $formattedTime)" />
        </xsl:attribute>
      </data>
    </record>
  </xsl:template>
</xsl:stylesheet>
