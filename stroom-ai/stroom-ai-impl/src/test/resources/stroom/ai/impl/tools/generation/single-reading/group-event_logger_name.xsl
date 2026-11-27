<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <xsl:import href="uuid='c0000000-0000-0000-0000-000000000001', name='Held common'"/>
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="held_all_log_unmapped_fields" as="xs:boolean" select="true()"/>
  <mcp:module kind="group" name="Held all" mode="held_all">
    <mcp:source-type>Auth</mcp:source-type>
    <mcp:source-type>Mail</mcp:source-type>
  </mcp:module>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="held_all"/>
    </Events>
  </xsl:template>
  <!-- Source type group 'Held all' (Auth, Mail): each record of its source types, in mode held_all -->
  <xsl:template match="*[not(mcp:held_all_in_group(.))]" mode="held_all" priority="1">
    <!-- Not of this group's source types: not written -->
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
  </xsl:template>
  <xsl:template match="*" mode="held_all">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:call-template name="held_all_mcp_unread_fields"/>
    <xsl:apply-templates select="." mode="held_all_event_type_line"/>
    <xsl:call-template name="held_all_mcp_log_kind">
      <xsl:with-param name="group" select="concat('line|', '')"/>
      <xsl:with-param name="line" select="concat('Kept as Unknown by rule ''line'': record ', $record-no)"/>
    </xsl:call-template>
  </xsl:template>
  <!-- held_all_mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out
       on purpose); called by the rules that translate their records -->
  <xsl:template name="held_all_mcp_unread_fields">
    <xsl:if test="$held_all_log_unmapped_fields">
      <xsl:for-each select="*[@key][not(@key = ('event', 'event&gt;logger_name', 'hostname', 'id', 'timestamp'))][normalize-space(string(.))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:held_all:', @key)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- held_all_mcp_log_kind: a record a rule keeps as Unknown on purpose, or a catch-all rule takes, logged once a
       stream for each kind (the rule and the values the rules test) -->
  <xsl:template name="held_all_mcp_log_kind">
    <xsl:param name="group"/>
    <xsl:param name="line"/>
    <xsl:if test="not(string(stroom:get(concat('mcp-kind:held_all:', $group))))">
      <xsl:value-of select="stroom:put(concat('mcp-kind:held_all:', $group), 'y')"/>
      <xsl:sequence select="stroom:log('INFO', $line)"/>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'line': every record. Writes an Unknown event (TypeId from id) from id, event. Unknown on purpose: a test. -->
  <xsl:template match="node()" mode="held_all_event_type_line">
    <!-- line: EventDetail/Unknown on purpose: a test -->
    <Event>
      <xsl:call-template name="held_common_event_time"/>
      <EventSource>
        <xsl:call-template name="held_common_system"/>
        <Generator>Held</Generator>
        <xsl:call-template name="held_common_device"/>
      </EventSource>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (*[@key='id'][normalize-space(.)]) then (*[@key='id'])[1] else 'line'"/>
        </TypeId>
        <Unknown>
          <Data Name="event" Value="{(*[@key='event'])[1]}"/>
        </Unknown>
      </EventDetail>
    </Event>
  </xsl:template>
  <xsl:function name="mcp:held_all_data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value[1]}"/>
    </xsl:if>
  </xsl:function>
  <xsl:function name="mcp:held_all_in_group" as="xs:boolean">
    <!-- True for a record of the group's source types -->
    <xsl:param name="record" as="element()"/>
    <xsl:sequence select="mcp:held_all_source_type($record) = ('Auth', 'Mail')"/>
  </xsl:function>
  <xsl:function name="mcp:held_all_source_type" as="xs:string">
    <!-- The source type a record is of: the first registry row its event>logger_name matches (= a value, ~ an anchored
       pattern, ∈ an item of a list), '' for none -->
    <xsl:param name="record" as="element()"/>
    <xsl:variable name="value" select="mcp:held_all_source_type_value($record)"/>
    <xsl:choose>
      <xsl:when test="$value = 'com.x.Auth'">
        <xsl:sequence select="'Auth'"/>
      </xsl:when>
      <xsl:when test="matches($value, '^/var/log/mail/')">
        <xsl:sequence select="'Mail'"/>
      </xsl:when>
      <xsl:when test="some $item in (for $part in tokenize($value, ';') return replace($part, '^[ \t\n\r]+|[ \t\n\r]+$', '')) satisfies $item = 'Directory Services'">
        <xsl:sequence select="'Directory'"/>
      </xsl:when>
      <xsl:when test="$value = ''">
        <xsl:sequence select="'(no value)'"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="''"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  <xsl:function name="mcp:held_all_source_type_value" as="xs:string">
    <!-- A record's source type value, read as the tools read event>logger_name: logger_name inside the held field event
       (its JSON, XML, syslog header or key=value pairs, told per record); stripped, '' for none or a nil value -->
    <xsl:param name="record" as="element()"/>
    <xsl:sequence select="string((mcp:held_all_reading_value(mcp:held_all_reading_held(string(($record/*[@key='event'])[1][not(*)]), '', '', 'logger_name', '^(|.*?[ \t\n\r,;])logger_name=(&quot;([^&quot;]*)&quot;|([^ \t\n\r,;]*)).*$', ('logger_name'), ('logger_name'), '')))[. ne ''][1])"/>
  </xsl:function>
  <xsl:function name="mcp:held_all_reading_value" as="xs:string">
    <!-- A value read, stripped of control characters, then of XML's white space; '' for a nil value -->
    <xsl:param name="value" as="xs:string?"/>
    <xsl:variable name="stripped" select="replace(replace(string($value), '[\p{Cc}�-[\t\n\r]]', ''), '^[ \t\n\r]+|[ \t\n\r]+$', '')"/>
    <xsl:sequence select="if (lower-case($stripped) = ('-', 'null', 'none', 'n/a')) then '' else $stripped"/>
  </xsl:function>
  <xsl:function name="mcp:held_all_reading_held" as="xs:string">
    <!-- One step into a held value, as the tools take it: its format told per record (JSON, XML, a syslog header,
       key=value pairs, else text, never read), then the step read in it -->
    <xsl:param name="text" as="xs:string?"/>
    <xsl:param name="format" as="xs:string"/>
    <xsl:param name="special" as="xs:string"/>
    <xsl:param name="key" as="xs:string"/>
    <xsl:param name="pair" as="xs:string"/>
    <xsl:param name="names" as="xs:string*"/>
    <xsl:param name="local" as="xs:string*"/>
    <xsl:param name="attribute" as="xs:string"/>
    <xsl:variable name="t" select="replace(string($text), '^[﻿ \t\n\r]+|[ \t\n\r]+$', '')"/>
    <xsl:variable name="json" as="element()?" select="mcp:held_all_reading_json($t)"/>
    <xsl:variable name="xml" as="element()?" select="if (exists($json)) then () else mcp:held_all_reading_xml($t)"/>
    <xsl:variable name="found" as="xs:string" select="if (exists($json)) then 'json' else if (exists($xml)) then 'xml' else if ($t ne '' and (matches($t, '^&lt;[0-9]{1,3}&gt;1 [^ \t\n\r]+ ([^ \t\n\r]+) ([^ \t\n\r]+) [^ \t\n\r]+ [^ \t\n\r]+( (.*))?$', 's') or matches($t, '^(&lt;[0-9]{1,3}&gt;)?[A-Z][a-z]{2} [ 0-9][0-9] [0-9]{2}:[0-9]{2}:[0-9]{2} ([^ \t\n\r]+) (([^ \t\n\r:\[]+)(\[[^\]]*\])?: ?)?(.*)$', 's'))) then 'syslog' else if ($t ne '' and count(tokenize($t, '(^|[ \t\n\r,;])[A-Za-z_][A-Za-z0-9_.\-]*=')) - 1 ge 2) then 'kv' else 'text'"/>
    <xsl:sequence select="if ($t eq '' or ($format ne '' and $format ne $found)) then '' else if ($special eq '#format') then $found else if ($found eq 'json') then (if ($special ne '' or $attribute ne '') then '' else mcp:held_all_reading_json_at($json, $names)) else if ($found eq 'xml') then (if ($special eq '#root') then local-name($xml) else if ($special ne '') then '' else mcp:held_all_reading_xml_at($xml, $local, $attribute)) else if ($found eq 'syslog') then (if ($special ne '' or $attribute ne '') then '' else if ($key eq 'host') then (if (matches($t, '^&lt;[0-9]{1,3}&gt;1 [^ \t\n\r]+ ([^ \t\n\r]+) ([^ \t\n\r]+) [^ \t\n\r]+ [^ \t\n\r]+( (.*))?$', 's')) then replace($t, '^&lt;[0-9]{1,3}&gt;1 [^ \t\n\r]+ ([^ \t\n\r]+) ([^ \t\n\r]+) [^ \t\n\r]+ [^ \t\n\r]+( (.*))?$', '$1', 's') else replace($t, '^(&lt;[0-9]{1,3}&gt;)?[A-Z][a-z]{2} [ 0-9][0-9] [0-9]{2}:[0-9]{2}:[0-9]{2} ([^ \t\n\r]+) (([^ \t\n\r:\[]+)(\[[^\]]*\])?: ?)?(.*)$', '$2', 's')) else if ($key eq 'app') then (if (matches($t, '^&lt;[0-9]{1,3}&gt;1 [^ \t\n\r]+ ([^ \t\n\r]+) ([^ \t\n\r]+) [^ \t\n\r]+ [^ \t\n\r]+( (.*))?$', 's')) then replace($t, '^&lt;[0-9]{1,3}&gt;1 [^ \t\n\r]+ ([^ \t\n\r]+) ([^ \t\n\r]+) [^ \t\n\r]+ [^ \t\n\r]+( (.*))?$', '$2', 's') else replace($t, '^(&lt;[0-9]{1,3}&gt;)?[A-Z][a-z]{2} [ 0-9][0-9] [0-9]{2}:[0-9]{2}:[0-9]{2} ([^ \t\n\r]+) (([^ \t\n\r:\[]+)(\[[^\]]*\])?: ?)?(.*)$', '$4', 's')) else if ($key eq 'message') then (if (matches($t, '^&lt;[0-9]{1,3}&gt;1 [^ \t\n\r]+ ([^ \t\n\r]+) ([^ \t\n\r]+) [^ \t\n\r]+ [^ \t\n\r]+( (.*))?$', 's')) then replace($t, '^&lt;[0-9]{1,3}&gt;1 [^ \t\n\r]+ ([^ \t\n\r]+) ([^ \t\n\r]+) [^ \t\n\r]+ [^ \t\n\r]+( (.*))?$', '$4', 's') else replace($t, '^(&lt;[0-9]{1,3}&gt;)?[A-Z][a-z]{2} [ 0-9][0-9] [0-9]{2}:[0-9]{2}:[0-9]{2} ([^ \t\n\r]+) (([^ \t\n\r:\[]+)(\[[^\]]*\])?: ?)?(.*)$', '$6', 's')) else '') else if ($found eq 'kv') then (if ($special ne '' or $attribute ne '' or $pair eq '') then '' else if (matches($t, $pair, 's')) then replace($t, $pair, '$3$4', 's') else '') else ''"/>
  </xsl:function>
  <xsl:function name="mcp:held_all_reading_json" as="element()?">
    <!-- The JSON object a held text is (or a JSON string holding one), or nothing -->
    <xsl:param name="t" as="xs:string"/>
    <xsl:try>
      <xsl:sequence select="if (starts-with($t, '{')) then json-to-xml($t)/*[local-name() eq 'map'] else if (starts-with($t, '&quot;')) then (let $inner := replace(string(json-to-xml($t)/*[local-name() eq 'string']), '^[﻿ \t\n\r]+|[ \t\n\r]+$', '') return if (starts-with($inner, '{')) then json-to-xml($inner)/*[local-name() eq 'map'] else ()) else ()"/>
      <xsl:catch>
        <xsl:sequence select="()"/>
      </xsl:catch>
    </xsl:try>
  </xsl:function>
  <xsl:function name="mcp:held_all_reading_xml" as="element()?">
    <!-- The root element of the XML document a held text is, or nothing -->
    <xsl:param name="t" as="xs:string"/>
    <xsl:if test="starts-with($t, '&lt;') and not(contains($t, '&lt;!DOCTYPE'))">
      <xsl:try>
        <xsl:sequence select="parse-xml($t)/*"/>
        <xsl:catch>
          <xsl:sequence select="()"/>
        </xsl:catch>
      </xsl:try>
    </xsl:if>
  </xsl:function>
  <xsl:function name="mcp:held_all_reading_json_at" as="xs:string">
    <!-- A held JSON object's value at the keys given: a string, number or boolean, else '' -->
    <xsl:param name="node" as="element()?"/>
    <xsl:param name="names" as="xs:string*"/>
    <xsl:sequence select="if (empty($node)) then '' else if (empty($names)) then (if (local-name($node) = ('string', 'number', 'boolean')) then string($node) else '') else mcp:held_all_reading_json_at(($node/*[@key eq $names[1]])[last()], subsequence($names, 2))"/>
  </xsl:function>
  <xsl:function name="mcp:held_all_reading_xml_at" as="xs:string">
    <!-- A held XML document's value at the elements given (by local name, below the root), then an attribute: the
       first's in document order -->
    <xsl:param name="nodes" as="element()*"/>
    <xsl:param name="names" as="xs:string*"/>
    <xsl:param name="attribute" as="xs:string"/>
    <xsl:sequence select="if (exists($names)) then mcp:held_all_reading_xml_at($nodes/*[local-name() eq $names[1]], subsequence($names, 2), $attribute) else if ($attribute ne '') then string(($nodes/@*[local-name() eq $attribute])[1]) else string($nodes[1])"/>
  </xsl:function>
</xsl:stylesheet>
