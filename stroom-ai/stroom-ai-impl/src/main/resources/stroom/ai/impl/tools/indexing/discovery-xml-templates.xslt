  <!-- XML as JSON: an element with children or attributes is an object, a repeated element an array, an
       attribute a field, an element holding only text a value. -->
  <xsl:template name="children">
    <xsl:param name="of" as="element()*" />
    <xsl:param name="top" as="xs:boolean" select="false()" />
    <xsl:for-each-group select="$of" group-by="local-name()">
      <xsl:variable name="key" select="if ($top) then d:top(current-grouping-key()) else d:key(current-grouping-key())" />
      <xsl:choose>
        <xsl:when test="count(current-group()) gt 1">
          <array key="{$key}">
            <xsl:apply-templates select="current-group()" mode="xml" />
          </array>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates select="." mode="xml">
            <xsl:with-param name="key" select="$key" />
          </xsl:apply-templates>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:for-each-group>
  </xsl:template>
  <xsl:template match="*" mode="xml">
    <xsl:param name="key" as="xs:string" select="''" />
    <xsl:choose>
      <xsl:when test="not(*) and not(@*) and $key != ''">
        <xsl:call-template name="leaf">
          <xsl:with-param name="key" select="$key" />
          <xsl:with-param name="value" select="string(.)" />
        </xsl:call-template>
      </xsl:when>
      <xsl:when test="not(*) and not(@*)">
        <string><xsl:value-of select="." /></string>
      </xsl:when>
      <xsl:otherwise>
        <map>
          <xsl:if test="$key != ''"><xsl:attribute name="key" select="$key" /></xsl:if>
          <xsl:for-each select="@*">
            <string key="{d:key(local-name())}"><xsl:value-of select="." /></string>
          </xsl:for-each>
          <xsl:if test="normalize-space(string-join(text(), '')) != ''">
            <string key="value"><xsl:value-of select="normalize-space(string-join(text(), ''))" /></string>
          </xsl:if>
          <xsl:call-template name="children">
            <xsl:with-param name="of" select="*" />
          </xsl:call-template>
        </map>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>