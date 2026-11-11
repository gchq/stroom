      <xsl:for-each select="@*@@KEEP@@">
        <xsl:call-template name="leaf">
          <xsl:with-param name="key" select="d:top(local-name())" />
          <xsl:with-param name="value" select="string(.)" />
        </xsl:call-template>
      </xsl:for-each>
      <xsl:if test="not(*) and normalize-space(.) != ''">
        <xsl:call-template name="leaf">
          <xsl:with-param name="key" select="'value'" />
          <xsl:with-param name="value" select="normalize-space(.)" />
        </xsl:call-template>
      </xsl:if>
      <xsl:call-template name="children">
        <xsl:with-param name="of" select="*@@KEEP@@" />
        <xsl:with-param name="top" select="true()" />
      </xsl:call-template>