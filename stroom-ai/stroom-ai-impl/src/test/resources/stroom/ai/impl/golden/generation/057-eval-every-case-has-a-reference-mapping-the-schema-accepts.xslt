<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="" exclude-result-prefixes="stroom xs">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_badge"/>
  </xsl:template>
  <!-- Rule 'badge': every record. Writes an Authorise event (TypeId badge) from door, granted. -->
  <xsl:template match="node()" mode="event_type_badge">
    <Event>
      <xsl:if test="(when)[normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(when[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="door" select="(door)[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>Door Control</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>door-controller</Generator>
        <xsl:if test="$door">
          <Device>
            <Name>
              <xsl:value-of select="$door"/>
            </Name>
          </Device>
        </xsl:if>
        <xsl:if test="(holder)[normalize-space(.)]">
          <User>
            <Id>
              <xsl:value-of select="holder"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>badge</TypeId>
        <Authorise>
          <xsl:if test="$door">
            <Resource>
              <Name>
                <xsl:value-of select="$door"/>
              </Name>
            </Resource>
          </xsl:if>
          <Action>Request</Action>
          <xsl:if test="(granted)[normalize-space(.)]">
            <Outcome>
              <Success>
                <xsl:value-of select="granted"/>
              </Success>
            </Outcome>
          </xsl:if>
        </Authorise>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
