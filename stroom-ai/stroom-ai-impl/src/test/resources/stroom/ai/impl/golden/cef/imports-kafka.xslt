<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="kafka-records:1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:cef="urn:stroom-mcp:cef" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" exclude-result-prefixes="xs cef" version="3.0">
  <xsl:import href="Cef-Common-V1"/>
  <xsl:import href="A&amp;B"/>
  <!-- CEF for ArcSight: one Kafka record per Event (kafka-records:1 takes one a document: split one Event a record),
       its value the flattened CEF line. -->
  <xsl:template match="/Events">
    <kafkaRecords xsi:schemaLocation="kafka-records:1 file://kafka-records-v1.1.xsd">
      <xsl:apply-templates select="Event"/>
    </kafkaRecords>
  </xsl:template>
  <xsl:template match="Event">
    <kafkaRecord topic="t{{x}}">
      <xsl:if test="string(EventTime/TimeCreated) castable as xs:dateTime">
        <xsl:attribute name="timestamp" select="format-dateTime(adjust-dateTime-to-timezone(xs:dateTime(string(EventTime/TimeCreated)), xs:dayTimeDuration('PT0H')), '[Y0001]-[M01]-[D01]T[H01]:[m01]:[s01].[f001]Z')"/>
      </xsl:if>
      <key>
        <xsl:value-of select="EventSource/User/Id"/>
      </key>
      <xsl:variable name="line" as="xs:string">
        <xsl:apply-templates select="." mode="cef_line"/>
      </xsl:variable>
      <value>
        <xsl:value-of select="$line"/>
      </value>
    </kafkaRecord>
  </xsl:template>
  <!-- cef_line: header|...|extension, the event kind's own fields then the common ones. -->
  <xsl:template match="Event" mode="cef_line" as="xs:string">
    <xsl:variable name="header" as="xs:string*" select="('CEF:0', cef:h('Ac''me', 63), cef:h('P', 63), cef:h('1', 31), cef:h((EventDetail/TypeId, 'x')[normalize-space(string(.)) != ''][1], 1023), cef:h('n', 512), cef:h('3', 10))"/>
    <xsl:variable name="pairs" as="xs:string*">
      <xsl:apply-templates select="." mode="cef_extension"/>
      <xsl:apply-templates select="." mode="cef_common"/>
    </xsl:variable>
    <!-- One value a key: the event kind's own, where a common field gives the same key. -->
    <xsl:variable name="extension" select="for $i in 1 to count($pairs) return $pairs[$i][not(substring-before(., '=') = (for $j in 1 to $i - 1 return substring-before($pairs[$j], '=')))]"/>
    <xsl:sequence select="concat(string-join($header, '|'), '|', string-join($extension, ' '))"/>
  </xsl:template>
  <xsl:template match="Event" mode="cef_extension" as="xs:string*"/>
  <!-- cef_common: every event: suser from EventSource/User/Id -->
  <xsl:template match="Event" mode="cef_common" as="xs:string*">
    <xsl:sequence select="cef:kv('suser', EventSource/User/Id, 1023)"/>
  </xsl:template>
  <!-- A header field, escaped: backslash and pipe, on one line. -->
  <xsl:function name="cef:h" as="xs:string">
    <xsl:param name="v" as="item()*"/>
    <xsl:param name="max" as="xs:integer"/>
    <xsl:sequence select="replace(replace(substring(normalize-space(string(($v)[1])), 1, $max), '\\', '\\\\'), '\|', '\\|')"/>
  </xsl:function>
  <!-- key=value, the value escaped (backslash, equals sign, line breaks) and cut to the key's length; nothing when empty. -->
  <xsl:function name="cef:kv" as="xs:string*">
    <xsl:param name="key" as="xs:string"/>
    <xsl:param name="v" as="item()*"/>
    <xsl:param name="max" as="xs:integer"/>
    <xsl:variable name="s" select="replace(string(($v)[1]), '^\s+|\s+$', '')"/>
    <xsl:if test="$s != ''">
      <xsl:sequence select="concat($key, '=', replace(replace(replace(substring($s, 1, $max), '\\', '\\\\'), '=', '\\='), '\r\n|\n|\r', '\\n'))"/>
    </xsl:if>
  </xsl:function>
  <!-- A custom slot: key=value and its label, only when there is a value. -->
  <xsl:function name="cef:kv" as="xs:string*">
    <xsl:param name="key" as="xs:string"/>
    <xsl:param name="v" as="item()*"/>
    <xsl:param name="max" as="xs:integer"/>
    <xsl:param name="label" as="xs:string"/>
    <xsl:variable name="pair" select="cef:kv($key, $v, $max)"/>
    <xsl:if test="exists($pair)">
      <xsl:sequence select="($pair, cef:kv(concat($key, 'Label'), $label, 1023))"/>
    </xsl:if>
  </xsl:function>
  <!-- A time as milliseconds since 1970 (UTC). -->
  <xsl:function name="cef:ms" as="xs:string?">
    <xsl:param name="t" as="item()*"/>
    <xsl:sequence select="if (string(($t)[1]) castable as xs:dateTime)                           then string(xs:integer((xs:dateTime(string(($t)[1])) - xs:dateTime('1970-01-01T00:00:00Z')) div xs:dayTimeDuration('PT0.001S')))                           else ()"/>
  </xsl:function>
  <!-- Outcome/Success as CEF's outcome: no Outcome means success, as in event-logging. -->
  <xsl:function name="cef:outcome" as="xs:string">
    <xsl:param name="s" as="item()*"/>
    <xsl:sequence select="if (string(($s)[1]) = 'false') then 'failure' else 'success'"/>
  </xsl:function>
</xsl:stylesheet>
