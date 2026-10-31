<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:cef="urn:stroom-mcp:cef" xpath-default-namespace="event-logging:3" exclude-result-prefixes="xs cef" version="3.0">
  <!-- CEF for ArcSight: one flattened CEF line per Event, as text. -->
  <xsl:output method="text"/>
  <xsl:template match="/Events">
    <xsl:for-each select="Event">
      <xsl:apply-templates select="." mode="cef_line"/>
      <xsl:text>
</xsl:text>
    </xsl:for-each>
  </xsl:template>
  <!-- cef_line: header|...|extension, the event kind's own fields then the common ones. -->
  <xsl:template match="Event" mode="cef_line" as="xs:string">
    <xsl:variable name="header" as="xs:string*" select="('CEF:0', cef:h(EventSource/System/Organisation, 63), cef:h(EventSource/System/Name, 63), cef:h(EventSource/System/Version, 31), cef:h((EventDetail/TypeId, string-join((local-name(EventDetail/*[not(self::TypeId or self::Description or self::Classification or self::Purpose or self::Data)][1]), EventDetail/*/Action), ' '))[normalize-space(.) != ''][1], 1023), cef:h((EventDetail/Description, string-join((local-name(EventDetail/*[not(self::TypeId or self::Description or self::Classification or self::Purpose or self::Data)][1]), EventDetail/*/Action), ' '))[normalize-space(.) != ''][1], 512), cef:h('3', 10))"/>
    <xsl:variable name="pairs" as="xs:string*">
      <xsl:apply-templates select="." mode="cef_extension"/>
      <xsl:apply-templates select="." mode="cef_common"/>
    </xsl:variable>
    <!-- One value a key: the event kind's own, where a common field gives the same key. -->
    <xsl:variable name="extension" select="for $i in 1 to count($pairs) return $pairs[$i][not(substring-before(., '=') = (for $j in 1 to $i - 1 return substring-before($pairs[$j], '=')))]"/>
    <xsl:sequence select="concat(string-join($header, '|'), '|', string-join($extension, ' '))"/>
  </xsl:template>
  <!-- Authenticate events: act from EventDetail/Authenticate/Action, cs3 from
       EventDetail/Authenticate/Data[@Name='note']/@Value, cn1 from
       EventDetail/Authenticate/Data[@Name='session_id']/@Value, outcome from
       EventDetail/Authenticate/Outcome/Success, duser from EventDetail/Authenticate/User/Id -->
  <xsl:template match="Event[EventDetail/Authenticate]" mode="cef_extension" as="xs:string*">
    <xsl:sequence select="cef:kv('act', EventDetail/Authenticate/Action, 63)"/>
    <xsl:sequence select="cef:kv('cs3', EventDetail/Authenticate/Data[@Name='note']/@Value, 4000, 'note')"/>
    <xsl:sequence select="cef:kv('cn1', EventDetail/Authenticate/Data[@Name='session_id']/@Value, 1023, 'session_id')"/>
    <xsl:sequence select="cef:kv('outcome', cef:outcome(EventDetail/Authenticate/Outcome/Success), 63)"/>
    <xsl:sequence select="cef:kv('duser', EventDetail/Authenticate/User/Id, 1023)"/>
  </xsl:template>
  <!-- Network events: act from local-name(EventDetail/Network/*[1]), dst from
       EventDetail/Network/*/Destination/Device/IPAddress, dpt from EventDetail/Network/*/Destination/Port, src
       from EventDetail/Network/*/Source/Device/IPAddress, spt from EventDetail/Network/*/Source/Port, proto
       from EventDetail/Network/*/TransportProtocol -->
  <xsl:template match="Event[EventDetail/Network]" mode="cef_extension" as="xs:string*">
    <xsl:sequence select="cef:kv('act', local-name(EventDetail/Network/*[1]), 63)"/>
    <xsl:sequence select="cef:kv('dst', EventDetail/Network/*/Destination/Device/IPAddress, 1023)"/>
    <xsl:sequence select="cef:kv('dpt', EventDetail/Network/*/Destination/Port, 1023)"/>
    <xsl:sequence select="cef:kv('src', EventDetail/Network/*/Source/Device/IPAddress, 1023)"/>
    <xsl:sequence select="cef:kv('spt', EventDetail/Network/*/Source/Port, 1023)"/>
    <xsl:sequence select="cef:kv('proto', EventDetail/Network/*/TransportProtocol, 31)"/>
  </xsl:template>
  <!-- View events: act from 'View', cn1 from EventDetail/View/Resource/Id, cs3 from EventDetail/View/Resource/Name,
       cs4 from EventDetail/View/Resource/Type -->
  <xsl:template match="Event[EventDetail/View]" mode="cef_extension" as="xs:string*">
    <xsl:sequence select="cef:kv('act', 'View', 63)"/>
    <xsl:sequence select="cef:kv('cn1', EventDetail/View/Resource/Id, 1023, 'View Resource Id')"/>
    <xsl:sequence select="cef:kv('cs3', EventDetail/View/Resource/Name, 4000, 'View Resource Name')"/>
    <xsl:sequence select="cef:kv('cs4', EventDetail/View/Resource/Type, 4000, 'View Resource Type')"/>
  </xsl:template>
  <xsl:template match="Event" mode="cef_extension" as="xs:string*"/>
  <!-- cef_common: every event: dvchost from EventSource/Device/HostName, rt from EventTime/TimeCreated, cs1 from
       EventSource/Client/IPAddress, suser from EventSource/User/Id, dvc from EventSource/Device/IPAddress, cs2
       from EventSource/System/Environment -->
  <xsl:template match="Event" mode="cef_common" as="xs:string*">
    <xsl:sequence select="cef:kv('dvchost', EventSource/Device/HostName, 100)"/>
    <xsl:sequence select="cef:kv('rt', cef:ms(EventTime/TimeCreated), 1023)"/>
    <xsl:sequence select="cef:kv('cs1', EventSource/Client/IPAddress, 4000, 'Client IPAddress')"/>
    <xsl:sequence select="cef:kv('suser', EventSource/User/Id, 1023)"/>
    <xsl:sequence select="cef:kv('dvc', EventSource/Device/IPAddress, 1023)"/>
    <xsl:sequence select="cef:kv('cs2', EventSource/System/Environment, 4000, 'System Environment')"/>
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
