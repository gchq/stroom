<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_send"/>
  </xsl:template>
  <!-- Rule 'send': every record. Writes a Send event (TypeId send) from from, rcpt. -->
  <xsl:template match="node()" mode="event_type_send">
    <Event>
      <xsl:if test="normalize-space(data[@name='time']/@value)">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>MX</Name>
          <Environment>T</Environment>
        </System>
        <Generator>mx</Generator>
        <Device>
          <HostName>mx</HostName>
        </Device>
      </EventSource>
      <EventDetail>
        <TypeId>send</TypeId>
        <xsl:variable name="from" select="data[@name='from']/@value[normalize-space(.)]"/>
        <xsl:if test="$from or (data[@name='rcpt']/@value)[normalize-space(.)]">
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
          </Send>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
