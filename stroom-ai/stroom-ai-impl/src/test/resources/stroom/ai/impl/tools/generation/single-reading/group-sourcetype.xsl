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
      <xsl:for-each select="*[@key][not(@key = ('event', 'hostname', 'id', 'sourcetype', 'timestamp'))][normalize-space(string(.))]">
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
    <!-- The source type a record is of: the first registry row its sourcetype matches (= a value, ~ an anchored
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
    <!-- A record's sourcetype as the tools read it: stripped, and '' for none or a nil value -->
    <xsl:param name="record" as="element()"/>
    <xsl:variable name="stripped" select="replace(string(($record/*[@key='sourcetype'])[1]), '^[ \t\n\r]+|[ \t\n\r]+$', '')"/>
    <xsl:sequence select="if (lower-case($stripped) = ('-', 'null', 'none', 'n/a')) then '' else $stripped"/>
  </xsl:function>
</xsl:stylesheet>
