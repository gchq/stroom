<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_message"/>
  </xsl:template>
  <!-- Rule 'message': every record. Writes a Send event (TypeId from action) from from, rcpt, msgid, subject, size,
       action. -->
  <xsl:template match="node()" mode="event_type_message">
    <Event>
      <xsl:if test="normalize-space(data[@name='time']/@value)">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
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
        <xsl:if test="normalize-space(data[@name='host']/@value)">
          <Device>
            <HostName>
              <xsl:value-of select="data[@name='host']/@value"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="$from">
          <User>
            <EmailAddress>
              <xsl:value-of select="$from"/>
            </EmailAddress>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action else 'message'"/>
        </TypeId>
        <Send>
          <xsl:if test="$from">
            <Source>
              <User>
                <EmailAddress>
                  <xsl:value-of select="$from"/>
                </EmailAddress>
              </User>
            </Source>
          </xsl:if>
          <xsl:if test="(data[@name='rcpt']/@value)[normalize-space(.)]">
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
                    <xsl:value-of select="$msgid"/>
                  </Id>
                </xsl:if>
                <xsl:if test="$subject">
                  <Name>
                    <xsl:value-of select="$subject"/>
                  </Name>
                </xsl:if>
              </Email>
            </Payload>
          </xsl:if>
          <Outcome>
            <Success>
              <xsl:value-of select="if ($action = 'deliver') then 'true' else 'false'"/>
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
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
