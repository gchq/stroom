    <xsl:if test="starts-with(normalize-space($value), '{') and ends-with(normalize-space($value), '}')">
      <xsl:try>
        <map key="{$key}_json"><xsl:copy-of select="json-to-xml($value)/fn:map/*" /></map>
        <xsl:catch />
      </xsl:try>
    </xsl:if>
