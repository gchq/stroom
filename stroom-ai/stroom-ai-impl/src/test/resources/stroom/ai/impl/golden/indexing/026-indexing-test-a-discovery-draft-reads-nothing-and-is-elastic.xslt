<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:fn="http://www.w3.org/2005/xpath-functions"
    xmlns:j="http://www.w3.org/2013/XSL/json" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:d="urn:stroom-mcp:discovery"
    xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="j fn stroom xs d"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="3.0">
  <!-- Discovery index stroom-discovery-acme-v1: each raw record as it is, mapped dynamically by Elasticsearch. -->
  <xsl:template match="/">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="array/map | map" mode="record" />
    </array>
  </xsl:template>
  <xsl:template match="map" mode="record">
    <map>
      <number key="StreamId"><xsl:value-of select="stroom:stream-id()" /></number>
      <number key="EventId"><xsl:value-of select="stroom:record-no()" /></number>
      <xsl:if test="*[@key='ts']"><string key="@timestamp"><xsl:value-of select="string(*[@key='ts'])" /></string></xsl:if>

      <xsl:apply-templates select="*" mode="copy" />
    </map>
  </xsl:template>
  <!-- Top-level keys Elasticsearch refuses (its metadata fields), that Stroom's indexing filter drops (any starting
       with _), or that would repeat a field written here: kept, as <key>_original without the leading _. -->
  <xsl:template match="map[parent::array/parent::document-node() or parent::document-node()]/*[@key = ('_id', '_index', '_source', '_routing', '_ignored', '_ignored_source', '_seq_no', '_primary_term', '_version', '_field_names', '_doc_count', '_tier', '_data_stream_timestamp', '_meta', '_nested_path', '_tsid', '_size', '_feature', 'StreamId', 'EventId', '@timestamp')
                       or starts-with(@key, '_')]" mode="copy" priority="3">
    <xsl:variable name="bare" select="replace(@key, '^_+', '')" />
    <xsl:element name="{local-name()}">
      <xsl:attribute name="key" select="concat(if ($bare = '') then 'underscore' else $bare, '_original')" />
      <xsl:apply-templates mode="copy" />
    </xsl:element>
  </xsl:template>
  <xsl:template match="*" mode="copy">
    <xsl:element name="{local-name()}">
      <xsl:if test="@key"><xsl:attribute name="key" select="d:key(@key)" /></xsl:if>
      <xsl:apply-templates mode="copy" />
    </xsl:element>
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
  <!-- A string holding a JSON object is also parsed, into a sibling <key>_json object: the string is kept, so a
       field that is only sometimes JSON keeps one type. Text that is not valid JSON is left as it is. -->
  <xsl:template match="j:string[@key][starts-with(normalize-space(.), '{')][ends-with(normalize-space(.), '}')]" mode="copy">
    <string key="{@key}"><xsl:value-of select="." /></string>
    <xsl:try>
      <xsl:variable name="parsed" select="json-to-xml(string(.))" />
      <map key="{@key}_json"><xsl:copy-of select="$parsed/fn:map/*" /></map>
      <xsl:catch />
    </xsl:try>
  </xsl:template>

</xsl:stylesheet>
