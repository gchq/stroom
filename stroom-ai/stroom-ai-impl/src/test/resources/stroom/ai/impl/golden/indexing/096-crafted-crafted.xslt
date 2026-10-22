<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:fn="http://www.w3.org/2005/xpath-functions"
    xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:d="urn:stroom-mcp:discovery" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="records:2" exclude-result-prefixes="fn stroom xs d" version="2.0">
  <!-- Discovery index disc-@@INDEX@@-v1: each raw delimited record as it is, mapped dynamically by Elasticsearch. -->
  <xsl:template match="/">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="records/record" mode="record" />
    </array>
  </xsl:template>
  <xsl:template match="*" mode="record">
    <map>
      <number key="StreamId"><xsl:value-of select="stroom:stream-id()" /></number>
      <number key="EventId"><xsl:value-of select="stroom:record-no()" /></number>
      <xsl:if test="data[@name='when']/@value"><string key="@timestamp"><xsl:value-of select="string(data[@name='when']/@value)" /></string></xsl:if>

      <xsl:for-each select="data[@name][not(@name = ('secret'))]">
        <xsl:call-template name="leaf">
          <xsl:with-param name="key" select="d:top(@name)" />
          <xsl:with-param name="value" select="string(@value)" />
        </xsl:call-template>
      </xsl:for-each>
    </map>
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
