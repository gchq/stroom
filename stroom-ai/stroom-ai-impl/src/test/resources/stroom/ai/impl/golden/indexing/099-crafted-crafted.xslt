<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:fn="http://www.w3.org/2005/xpath-functions"
    xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:d="urn:stroom-mcp:discovery" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" exclude-result-prefixes="fn stroom xs d" version="3.0">
  <!-- Discovery index disc-@@INDEX@@-v1: each raw XML record as it is, mapped dynamically by Elasticsearch. -->
  <xsl:template match="/">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="//*[local-name() = 'logon']" mode="record" />
    </array>
  </xsl:template>
  <xsl:template match="*" mode="record">
    <map>
      <number key="StreamId"><xsl:value-of select="stroom:stream-id()" /></number>
      <number key="EventId"><xsl:value-of select="stroom:record-no()" /></number>
      <xsl:if test="@at"><string key="@timestamp"><xsl:value-of select="string(@at)" /></string></xsl:if>

      <xsl:for-each select="@*[not(local-name() = ('token'))]">
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
        <xsl:with-param name="of" select="*[not(local-name() = ('token'))]" />
        <xsl:with-param name="top" select="true()" />
      </xsl:call-template>
    </map>
  </xsl:template>
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
  <!-- A value: kept as a string; one holding a JSON object is also parsed into a sibling <key>_json object. -->
  <xsl:template name="leaf">
    <xsl:param name="key" as="xs:string" />
    <xsl:param name="value" as="xs:string" />
    <string key="{$key}"><xsl:value-of select="$value" /></string>
    <xsl:if test="starts-with(normalize-space($value), '{') and ends-with(normalize-space($value), '}')">
      <xsl:try>
        <map key="{$key}_json"><xsl:copy-of select="json-to-xml($value)/fn:map/*" /></map>
        <xsl:catch />
      </xsl:try>
    </xsl:if>
  </xsl:template>
  <!-- Keys Elasticsearch cannot take as field names (empty, or with an empty dotted part: .a, a..b) are repaired;
       keys starting with _, which Stroom's indexing filter drops at any depth, become <key>_original without it. -->
  <xsl:function name="d:key" as="xs:string">
    <xsl:param name="key" as="xs:string" />
    <xsl:variable name="joined" select="replace(replace($key, '\.{2,}', '.'), '^\.+', '')" />
    <xsl:variable name="bare" select="replace($joined, '^_+', '')" />
    <xsl:sequence select="if (normalize-space($joined) = '') then 'empty_key'
                          else if (starts-with($joined, '_')) then concat(if ($bare = '') then 'underscore' else $bare, '_original')
                          else $joined" />
  </xsl:function>
  <!-- A top-level key that would repeat a field written here is kept as <key>_original. -->
  <xsl:function name="d:top" as="xs:string">
    <xsl:param name="key" as="xs:string" />
    <xsl:variable name="repaired" select="d:key($key)" />
    <xsl:sequence select="if ($repaired = ('StreamId', 'EventId', '@timestamp')) then concat($repaired, '_original') else $repaired" />
  </xsl:function>
</xsl:stylesheet>
