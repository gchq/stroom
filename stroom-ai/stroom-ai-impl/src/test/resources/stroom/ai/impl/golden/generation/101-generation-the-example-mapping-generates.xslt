<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="*/Event" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="EventID = '4663'">
        <xsl:apply-templates select="." mode="event_type_view"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('EventID=', string(((EventID)[normalize-space(.)])[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'view': records where EventID = 4663. Writes a View event (TypeId from EventID) from EventID. -->
  <xsl:template match="node()" mode="event_type_view">
    <Event>
      <xsl:variable name="event_time_time_created" select="(System/TimeCreated/@SystemTime)[normalize-space(.)]"/>
      <xsl:if test="exists($event_time_time_created)">
        <EventTime>
          <TimeCreated>
            <xsl:if test="count($event_time_time_created) gt 1">
              <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($event_time_time_created), ') for ', 'EventTime/TimeCreated, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
            </xsl:if>
            <xsl:value-of select="stroom:format-date($event_time_time_created[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>AppAudit</Name>
          <Environment>Prod</Environment>
        </System>
        <Generator>AppAudit</Generator>
        <xsl:if test="(Computer)[normalize-space(.)]">
          <Device>
            <HostName>
              <xsl:value-of select="(Computer)[1]"/>
            </HostName>
          </Device>
        </xsl:if>
      </EventSource>
      <xsl:variable name="document_id" select="(EventData/Data[@Name=&quot;ObjectName&quot;])[normalize-space(.)]"/>
      <xsl:variable name="event_id" select="(EventID)[normalize-space(.)]"/>
      <xsl:if test="$event_id or exists($document_id)">
        <EventDetail>
          <xsl:if test="$event_id">
            <TypeId>
              <xsl:value-of select="$event_id[1]"/>
            </TypeId>
          </xsl:if>
          <xsl:if test="exists($document_id)">
            <View>
              <Document>
                <Id>
                  <xsl:if test="count($document_id) gt 1">
                    <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($document_id), ') for ', 'EventDetail/View/Document/Id, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
                  </xsl:if>
                  <xsl:value-of select="$document_id[1]"/>
                </Id>
              </Document>
            </View>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
</xsl:stylesheet>
