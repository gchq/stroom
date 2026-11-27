<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
      <xsl:for-each select="distinct-values((/map/map)/mcp:source_type_value(.))[. ne '' and not(mcp:seen_source_type(.))]">
        <xsl:if test="not(string(stroom:get(concat('mcp-unseen:', .))))">
          <xsl:value-of select="stroom:put(concat('mcp-unseen:', .), 'y')"/>
          <xsl:sequence select="stroom:log('INFO', concat('New source type ''', ., ''' in sourcetype, not seen when this feed was surveyed: survey the feed again to cover it'))"/>
        </xsl:if>
      </xsl:for-each>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:apply-templates select="." mode="event_type_line"/>
    <xsl:call-template name="mcp_log_kind">
      <xsl:with-param name="group" select="concat('line|', '')"/>
      <xsl:with-param name="line" select="concat('Kept as Unknown by rule ''line'': record ', $record-no)"/>
    </xsl:call-template>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="*[@key][not(@key = ('event', 'hostname', 'id', 'timestamp'))][normalize-space(string(.))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- mcp_log_kind: a record a rule keeps as Unknown on purpose, or a catch-all rule takes, logged once a stream for
       each kind (the rule and the values the rules test) -->
  <xsl:template name="mcp_log_kind">
    <xsl:param name="group"/>
    <xsl:param name="line"/>
    <xsl:if test="not(string(stroom:get(concat('mcp-kind:', $group))))">
      <xsl:value-of select="stroom:put(concat('mcp-kind:', $group), 'y')"/>
      <xsl:sequence select="stroom:log('INFO', $line)"/>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'line': every record. Writes an Unknown event (TypeId from id) from id, event. Unknown on purpose: a test. -->
  <xsl:template match="node()" mode="event_type_line">
    <!-- line: EventDetail/Unknown on purpose: a test -->
    <Event>
      <xsl:if test="*[@key='timestamp'][normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="(*[@key='timestamp'])[1]"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>Held</Name>
          <Environment>Prod</Environment>
        </System>
        <Generator>Held</Generator>
        <xsl:if test="*[@key='hostname'][normalize-space(.)]">
          <Device>
            <HostName>
              <xsl:value-of select="(*[@key='hostname'])[1]"/>
            </HostName>
          </Device>
        </xsl:if>
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
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value[1]}"/>
    </xsl:if>
  </xsl:function>
  <xsl:function name="mcp:source_type_value" as="xs:string">
    <!-- A record's sourcetype as the tools read it: stripped (controls, white space), and '' for none or a nil value -->
    <xsl:param name="record" as="element()"/>
    <xsl:variable name="stripped" select="replace(replace(string(($record/*[@key='sourcetype'])[1]), '[\p{Cc}�-[\t\n\r]]', ''), '^[ \t\n\r]+|[ \t\n\r]+$', '')"/>
    <xsl:sequence select="if (lower-case($stripped) = ('-', 'null', 'none', 'n/a')) then '' else $stripped"/>
  </xsl:function>
  <xsl:function name="mcp:seen_source_type" as="xs:boolean">
    <!-- True for a value of sourcetype seen when the feed was surveyed -->
    <xsl:param name="value" as="xs:string"/>
    <xsl:sequence select="$value = ('com.x.Auth') or matches($value, '^/var/log/mail/') or (some $item in (for $part in tokenize($value, ';') return replace($part, '^[ \t\n\r]+|[ \t\n\r]+$', '')) satisfies $item = ('Directory Services'))"/>
  </xsl:function>
</xsl:stylesheet>
