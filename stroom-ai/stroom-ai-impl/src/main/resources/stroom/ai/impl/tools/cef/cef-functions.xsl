  <!-- A header field, escaped: backslash and pipe, on one line. -->
  <xsl:function name="cef:h" as="xs:string">
    <xsl:param name="v" as="item()*" />
    <xsl:param name="max" as="xs:integer" />
    <xsl:sequence select="replace(replace(substring(normalize-space(string(($v)[1])), 1, $max), '\\', '\\\\'), '\|', '\\|')" />
  </xsl:function>
  <!-- key=value, the value escaped (backslash, equals sign, line breaks) and cut to the key's length; nothing when empty. -->
  <xsl:function name="cef:kv" as="xs:string*">
    <xsl:param name="key" as="xs:string" />
    <xsl:param name="v" as="item()*" />
    <xsl:param name="max" as="xs:integer" />
    <xsl:variable name="s" select="replace(string(($v)[1]), '^\s+|\s+$', '')" />
    <xsl:if test="$s != ''">
      <xsl:sequence select="concat($key, '=', replace(replace(replace(substring($s, 1, $max), '\\', '\\\\'), '=', '\\='), '\r\n|\n|\r', '\\n'))" />
    </xsl:if>
  </xsl:function>
  <!-- A custom slot: key=value and its label, only when there is a value. -->
  <xsl:function name="cef:kv" as="xs:string*">
    <xsl:param name="key" as="xs:string" />
    <xsl:param name="v" as="item()*" />
    <xsl:param name="max" as="xs:integer" />
    <xsl:param name="label" as="xs:string" />
    <xsl:variable name="pair" select="cef:kv($key, $v, $max)" />
    <xsl:if test="exists($pair)">
      <xsl:sequence select="($pair, cef:kv(concat($key, 'Label'), $label, 1023))" />
    </xsl:if>
  </xsl:function>
  <!-- A time as milliseconds since 1970 (UTC). -->
  <xsl:function name="cef:ms" as="xs:string?">
    <xsl:param name="t" as="item()*" />
    <xsl:sequence select="if (string(($t)[1]) castable as xs:dateTime)
                          then string(xs:integer((xs:dateTime(string(($t)[1])) - xs:dateTime('1970-01-01T00:00:00Z')) div xs:dayTimeDuration('PT0.001S')))
                          else ()" />
  </xsl:function>
  <!-- Outcome/Success as CEF's outcome: no Outcome means success, as in event-logging. -->
  <xsl:function name="cef:outcome" as="xs:string">
    <xsl:param name="s" as="item()*" />
    <xsl:sequence select="if (string(($s)[1]) = 'false') then 'failure' else 'success'" />
  </xsl:function>
