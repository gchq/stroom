<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="urn:example:fileaudit" exclude-result-prefixes="stroom xs">
  <xsl:template match="audit">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="entry" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="op" select="(op)[normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$op = 'read'">
        <xsl:apply-templates select="." mode="event_type_read"/>
      </xsl:when>
      <xsl:when test="$op = 'delete'">
        <xsl:apply-templates select="." mode="event_type_delete"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('op=', string(($op)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'read': records where op = read. Writes a View event (TypeId File-Read) from path. -->
  <xsl:template match="node()" mode="event_type_read">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>File-Read</TypeId>
        <xsl:if test="(path)[normalize-space(.)]">
          <View>
            <xsl:apply-templates select="." mode="file"/>
          </View>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'delete': records where op = delete. Writes a Delete event (TypeId File-Delete) from path. -->
  <xsl:template match="node()" mode="event_type_delete">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>File-Delete</TypeId>
        <xsl:if test="(path)[normalize-space(.)]">
          <Delete>
            <xsl:apply-templates select="." mode="file"/>
          </Delete>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'read', 'delete' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="(when)[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(when[1], 'yyyy-MM-dd''T''HH:mm:ssXXX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'read', 'delete' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>File Server</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>fileaudit</Generator>
      <xsl:if test="(machine)[normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="machine"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="(account)[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="account"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- file: shared by the rules 'read', 'delete' -->
  <xsl:template match="node()" mode="file">
    <xsl:if test="(path)[normalize-space(.)]">
      <File>
        <Path>
          <xsl:value-of select="path"/>
        </Path>
      </File>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
