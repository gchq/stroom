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
      <xsl:if test="normalize-space(*[@key='ts'])">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="*[@key='ts']"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>App</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>audit</Generator>
        <xsl:if test="normalize-space($record/*[@key='host'])">
          <Device>
            <HostName>
              <xsl:value-of select="$record/*[@key='host']"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:variable name="user" select="*[@key='user'][normalize-space(.)]"/>
        <xsl:if test="$user or (*[@key='roles'])[normalize-space(.)]">
          <User>
            <xsl:if test="$user">
              <Id>
                <xsl:value-of select="$user"/>
              </Id>
            </xsl:if>
            <xsl:if test="(*[@key='roles'])[normalize-space(.)]">
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
        <xsl:if test="(*[@key='roles'])[normalize-space(.)]">
          <Unknown>
            <xsl:for-each select="*[@key='roles']/*">
              <Data Name="role" Value="{upper-case(.[1])}"/>
            </xsl:for-each>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
