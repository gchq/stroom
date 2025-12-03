<?xml version="1.1" encoding="UTF-8" ?>

<!-- UK Crown Copyright © 2016 -->
<xsl:stylesheet xpath-default-namespace="http://www.w3.org/2013/XSL/json" xmlns="http://www.w3.org/2013/XSL/json" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:j="http://www.w3.org/2013/XSLT/xml-to-json" version="3.0">
  <xsl:variable name="quot" visibility="private">"</xsl:variable>
  <xsl:param name="indent-spaces" select="2" />
  <xsl:template match="/map">(root)<spans>
      <xsl:apply-templates />
    </spans>
  </xsl:template>
  <xsl:template match="//string[@key='traceId']">
    <span>
      <xsl:apply-templates select="parent::map" mode="span" />
    </span>
  </xsl:template>
  <xsl:template match="map" mode="span">
    <xsl:variable name="depth" select="count(ancestor::*) + 1" />
    <xsl:if test="@key">
      <xsl:value-of select="concat($quot, @key, $quot)" />
      <xsl:text>: </xsl:text>
    </xsl:if>
    <xsl:text>{</xsl:text>
    <xsl:for-each select="node()">
      <xsl:if test="position() gt 1">
        <xsl:text>, </xsl:text>
        <xsl:value-of select="j:indent($depth)" />
      </xsl:if>
      <xsl:apply-templates select="." mode="span" />
    </xsl:for-each>
    <xsl:text>}</xsl:text>
  </xsl:template>
  <xsl:template match="array" mode="span">
    <xsl:variable name="depth" select="count(ancestor::*) + 1" />
    <xsl:if test="@key">
      <xsl:value-of select="concat($quot, @key, $quot)" />
      <xsl:text>: </xsl:text>
    </xsl:if>
    <xsl:value-of>
      <xsl:text>[</xsl:text>
      <xsl:for-each select="*">
        <xsl:if test="position() gt 1">
          <xsl:text>, </xsl:text>
          <xsl:value-of select="j:indent($depth)" />
        </xsl:if>
        <xsl:apply-templates select="." mode="span" />
      </xsl:for-each>
      <xsl:text>]</xsl:text>
    </xsl:value-of>
  </xsl:template>
  <xsl:template match="string|string[@key='stringValue']" mode="span">
    <xsl:value-of select="concat($quot, @key, $quot)" />
    <xsl:text>: </xsl:text>
    <xsl:sequence select="concat($quot, j:escape(.), $quot)" />
  </xsl:template>
  <xsl:template match="string[@key='intValue' or @key='boolValue' or @key='doubleValue']" mode="span">
    <xsl:value-of select="concat($quot, @key, $quot)" />
    <xsl:text>: </xsl:text>
    <xsl:sequence select="." />
  </xsl:template>
  <xsl:template match="number" mode="span">
    <xsl:value-of select="concat($quot, @key, $quot)" />
    <xsl:text>: </xsl:text>
    <xsl:value-of select="text()" />
  </xsl:template>
  <xsl:template match="text()" mode="span"></xsl:template>
  <xsl:template match="text()"></xsl:template>

  <!-- Function to escape special characters -->
  <xsl:function name="j:escape" as="xs:string" visibility="final">
    <xsl:param name="in" as="xs:string" />
    <xsl:value-of>
      <xsl:for-each select="string-to-codepoints($in)">
        <xsl:choose>
          <xsl:when test=". gt 65535">
            <xsl:value-of select="concat('\u', j:hex4((. - 65536) idiv 1024 + 55296))" />
            <xsl:value-of select="concat('\u', j:hex4((. - 65536) mod 1024 + 56320))" />
          </xsl:when>
          <xsl:when test=". = 34">\"</xsl:when>
          <xsl:when test=". = 92">\\</xsl:when>
          <xsl:when test=". = 08">\b</xsl:when>
          <xsl:when test=". = 09">\t</xsl:when>
          <xsl:when test=". = 10">\n</xsl:when>
          <xsl:when test=". = 12">\f</xsl:when>
          <xsl:when test=". = 13">\r</xsl:when>
          <xsl:when test=". lt 32 or (. ge 127 and . le 160)">
            <xsl:value-of select="concat('\u', j:hex4(.))" />
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="codepoints-to-string(.)" />
          </xsl:otherwise>
        </xsl:choose>
      </xsl:for-each>
    </xsl:value-of>
  </xsl:function>

  <!-- Function to convert a UTF16 codepoint into a string of four hex digits -->
  <xsl:function name="j:hex4" as="xs:string" visibility="final">
    <xsl:param name="ch" as="xs:integer" />
    <xsl:variable name="hex" select="'0123456789abcdef'" />
    <xsl:value-of>
      <xsl:value-of select="substring($hex, $ch idiv 4096 + 1, 1)" />
      <xsl:value-of select="substring($hex, $ch idiv 256 mod 16 + 1, 1)" />
      <xsl:value-of select="substring($hex, $ch idiv 16 mod 16 + 1, 1)" />
      <xsl:value-of select="substring($hex, $ch mod 16 + 1, 1)" />
    </xsl:value-of>
  </xsl:function>

  <!-- Function to output whitespace indentation based on 
  the depth of the node supplied as a parameter -->
  <xsl:function name="j:indent" as="text()" visibility="public">
    <xsl:param name="depth" as="xs:integer" />
    <xsl:value-of select="'&#xa;', string-join((1 to ($depth + 1) * $indent-spaces) ! ' ', '')" />
  </xsl:function>
</xsl:stylesheet>
