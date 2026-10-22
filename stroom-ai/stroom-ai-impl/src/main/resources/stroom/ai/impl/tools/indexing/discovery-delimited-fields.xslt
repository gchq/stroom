      <xsl:for-each select="data[@name]@@KEEP@@">
        <xsl:call-template name="leaf">
          <xsl:with-param name="key" select="d:top(@name)" />
          <xsl:with-param name="value" select="string(@value)" />
        </xsl:call-template>
      </xsl:for-each>