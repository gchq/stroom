<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:apply-templates select="." mode="event_type_message"/>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('action', 'from', 'host', 'msgid', 'rcpt', 'size', 'subject', 'time'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'message': every record. Writes a Send event (TypeId from action) from from, rcpt, msgid, subject, size,
       action. -->
  <xsl:template match="node()" mode="event_type_message">
    <Event>
      <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date((data[@name='time']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="from" select="data[@name='from']/@value[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>Mail Gateway</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>mailgw</Generator>
        <xsl:if test="data[@name='host']/@value[normalize-space(.)]">
          <Device>
            <HostName>
              <xsl:value-of select="(data[@name='host']/@value)[1]"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="$from">
          <User>
            <EmailAddress>
              <xsl:value-of select="$from[1]"/>
            </EmailAddress>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action[1] else 'message'"/>
        </TypeId>
        <Send>
          <xsl:if test="$from">
            <Source>
              <User>
                <EmailAddress>
                  <xsl:value-of select="$from[1]"/>
                </EmailAddress>
              </User>
            </Source>
          </xsl:if>
          <xsl:if test="exists((data[@name='rcpt']/@value)[normalize-space(.)])">
            <Destination>
              <xsl:for-each select="data[@name='rcpt']/@value">
                <User>
                  <EmailAddress>
                    <xsl:value-of select="."/>
                  </EmailAddress>
                </User>
              </xsl:for-each>
            </Destination>
          </xsl:if>
          <xsl:variable name="subject" select="data[@name='subject']/@value[normalize-space(.)]"/>
          <xsl:variable name="msgid" select="data[@name='msgid']/@value[normalize-space(.)]"/>
          <xsl:if test="$msgid or $subject">
            <Payload>
              <Email>
                <xsl:if test="$msgid">
                  <Id>
                    <xsl:value-of select="$msgid[1]"/>
                  </Id>
                </xsl:if>
                <xsl:if test="$subject">
                  <Name>
                    <xsl:value-of select="$subject[1]"/>
                  </Name>
                </xsl:if>
              </Email>
            </Payload>
          </xsl:if>
          <Outcome>
            <Success>
              <xsl:value-of select="if ($action[1] = 'deliver') then 'true' else 'false'"/>
            </Success>
          </Outcome>
          <xsl:sequence select="mcp:data('size', data[@name='size']/@value)"/>
          <xsl:sequence select="mcp:data('action', $action)"/>
        </Send>
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
</xsl:stylesheet>
