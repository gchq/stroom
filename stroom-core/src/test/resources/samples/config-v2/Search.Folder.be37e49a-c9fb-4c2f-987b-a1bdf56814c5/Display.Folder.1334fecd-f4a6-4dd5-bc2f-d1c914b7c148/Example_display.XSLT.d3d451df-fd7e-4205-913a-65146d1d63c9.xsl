<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="event-logging:3" xmlns:stroom="stroom"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
  <xsl:template match="/Events">
    <xsl:apply-templates />
  </xsl:template>
  <xsl:template match="Event">
    <xsl:variable name="href">
      <xsl:apply-templates select="Meta/stroom-meta:source" />
    </xsl:variable>
    <div>
      <div style="padding: 5px;">
        <span style="text-decoration:underline;color:blue;cursor:pointer">
          <xsl:attribute name="link" select="concat('[link](', $href, '){data}')" />
          <xsl:text>Show Source</xsl:text>
        </span>
        <xsl:text> </xsl:text>
        <xsl:value-of select="$href" />
      </div>
      <div style="padding: 5px;">
        <span style="text-decoration:underline;color:blue;cursor:pointer">
          <xsl:attribute name="link" select="concat('[link](', $href, '){stepping}')" />
          <xsl:text>Step Source</xsl:text>
        </span>
        <xsl:text> </xsl:text>
        <xsl:value-of select="$href" />
      </div>

      <xsl:call-template name="line">
        <xsl:with-param name="tag" select="'Feed'" />
        <xsl:with-param name="value" select="stroom:feed-name()" />
      </xsl:call-template>

      <xsl:apply-templates select="*[starts-with(local-name(), 'Event')]" />
    </div>
  </xsl:template>

  <xsl:template match="Event/Meta/stroom-meta:source/*">
    <xsl:value-of select="name()" />
    <xsl:text>=</xsl:text>
    <xsl:value-of select="text()" />
    <xsl:text>&amp;</xsl:text>
  </xsl:template>

  <!-- Index the action -->
  <xsl:template match="EventDetail">
    <xsl:for-each select="*">
      <xsl:if test="(name()!='TypeId' and name()!='Description')">
        <xsl:call-template name="line">
          <xsl:with-param name="tag" select="'Type'" />
          <xsl:with-param name="value" select="name()" />
        </xsl:call-template>
      </xsl:if>
    </xsl:for-each>
    <xsl:apply-templates />
  </xsl:template>

  <!-- Index the time -->
  <xsl:template match="node()">
    <xsl:variable name="text">
      <xsl:value-of select="text()" />
    </xsl:variable>
    <xsl:if test="string-length(normalize-space($text)) gt 0">
      <xsl:call-template name="line">
        <xsl:with-param name="tag" select="concat(../name(), ' ', name())" />
        <xsl:with-param name="value" select="normalize-space($text)" />
      </xsl:call-template>
    </xsl:if>
    <xsl:apply-templates />
  </xsl:template>
  <xsl:template name="line">
    <xsl:param name="tag" />
    <xsl:param name="value" />
    <div style="padding: 5px;">
      <b>
        <xsl:value-of select="replace(replace($tag, '(\S)([A-Z][a-z])', concat('$1', ' ', '$2')), '(\S+) \1', '$1')" />
        <xsl:text>:</xsl:text>
        <xsl:text> </xsl:text>
      </b>
      <xsl:value-of select="$value" />
    </div>
  </xsl:template>
</xsl:stylesheet>
