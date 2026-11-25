<?xml version="1.1" encoding="UTF-8"?>
<!-- A hand-written pre-processor before a VPN's translation: each record's operation code (op) written as the action
     the translation reads. Records to records: JSON in, the same JSON out with the action added. -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.w3.org/2013/XSL/json" xpath-default-namespace="http://www.w3.org/2013/XSL/json" version="3.0">

  <xsl:template match="@*|node()">
    <xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy>
  </xsl:template>

  <xsl:template match="map[string[@key='op']]">
    <map>
      <xsl:apply-templates select="@*|node()"/>
      <string key="action">
        <xsl:value-of select="if (string[@key='op'] = 'LGN') then 'login'
                              else if (string[@key='op'] = 'LGT') then 'logout'
                              else if (string[@key='op'] = 'CFG') then 'config_change'
                              else 'other'"/>
      </string>
    </map>
  </xsl:template>
</xsl:stylesheet>
