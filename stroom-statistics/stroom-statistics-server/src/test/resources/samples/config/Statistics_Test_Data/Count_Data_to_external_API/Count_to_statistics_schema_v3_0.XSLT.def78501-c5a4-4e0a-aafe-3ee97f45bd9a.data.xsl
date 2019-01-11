<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xmlns="statistics:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
  <xsl:template match="data">
    <statistics>
      <xsl:apply-templates />
    </statistics>
  </xsl:template> 
  <xsl:template match="event">
    <statistic>
      <name>
        <xsl:value-of select="./name"/>
      </name>
      <time>
        <xsl:value-of select="./time" />
      </time>
      <count>
        <xsl:value-of select="./value" />
      </count>
      <tags>
        <tag>
          <xsl:attribute name="name">user</xsl:attribute>
          <xsl:attribute name="value">
            <xsl:value-of select="./user" />
          </xsl:attribute>
        </tag>
        <tag>
          <xsl:attribute name="name">colour</xsl:attribute>
          <xsl:attribute name="value">
            <xsl:value-of select="./colour" />
          </xsl:attribute>
        </tag>
        <tag>
          <xsl:attribute name="name">state</xsl:attribute>
          <xsl:attribute name="value">
            <xsl:value-of select="./state" />
          </xsl:attribute>
        </tag>
      </tags>
      <identifiers>
        <compoundIdentifier>
            <xsl:value-of select="./value" />
        </compoundIdentifier>
      </identifiers>
    </statistic>
  </xsl:template>
</xsl:stylesheet>
