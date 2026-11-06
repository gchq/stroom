<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="*[@key='events']/*" mode="item">
      <xsl:with-param name="record" select="." tunnel="yes"/>
    </xsl:apply-templates>
    <xsl:for-each select="*[@key][not(@key = ('action', 'events', 'host', 'roles', 'ts', 'user'))][normalize-space(string(.))]">
      <!-- A field no rule reads: logged once a stream (ignored_fields: left out on purpose) -->
      <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
      <xsl:if test="not(string(stroom:get($mcp_unread)))">
        <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
        <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  <xsl:template match="*" mode="item">
    <xsl:param name="record" tunnel="yes"/>
    <xsl:choose>
      <xsl:when test="exists($record/*[@key='host'][matches(., '^qa-')])">
        <!-- drop: QA traffic: left untranslated on purpose -->
      </xsl:when>
      <xsl:when test="*[@key='action'] = 'HEARTBEAT'">
        <!-- drop: heartbeats are not events: left untranslated on purpose -->
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_any"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'any': records no other rule matches. Writes an Unknown event (TypeId x) from roles. -->
  <xsl:template match="node()" mode="event_type_any">
    <xsl:param name="record" tunnel="yes"/>
    <Event>
      <xsl:if test="*[@key='ts'][normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="(*[@key='ts'])[1]"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>App</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>audit</Generator>
        <xsl:if test="$record/*[@key='host'][normalize-space(.)]">
          <Device>
            <HostName>
              <xsl:value-of select="($record/*[@key='host'])[1]"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:variable name="user" select="*[@key='user'][normalize-space(.)]"/>
        <xsl:if test="$user or exists((*[@key='roles'])[normalize-space(.)])">
          <User>
            <xsl:if test="$user">
              <Id>
                <xsl:value-of select="$user[1]"/>
              </Id>
            </xsl:if>
            <xsl:if test="exists((*[@key='roles'])[normalize-space(.)])">
              <Groups>
                <xsl:for-each select="*[@key='roles']/*">
                  <Group>
                    <Name>
                      <xsl:value-of select="."/>
                    </Name>
                  </Group>
                </xsl:for-each>
              </Groups>
            </xsl:if>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>x</TypeId>
        <Unknown>
          <xsl:for-each select="*[@key='roles']/*">
            <Data Name="role" Value="{upper-case(.[1])}"/>
          </xsl:for-each>
        </Unknown>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
