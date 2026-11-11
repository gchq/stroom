<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="3.0">
  <xsl:template match="/Events">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="Event"/>
    </array>
  </xsl:template>
  <!-- Each Event: one document for the index m -->
  <xsl:template match="Event">
    <map>
      <xsl:if test="@StreamId">
        <number key="StreamId">
          <xsl:value-of select="@StreamId"/>
        </number>
      </xsl:if>
      <xsl:if test="@EventId">
        <number key="EventId">
          <xsl:value-of select="@EventId"/>
        </number>
      </xsl:if>
      <xsl:if test="EventTime/TimeCreated">
        <string key="@timestamp">
          <xsl:value-of select="EventTime/TimeCreated"/>
        </string>
      </xsl:if>
      <xsl:if test="EventDetail/Data[@Name=&quot;time&quot;]/@Value">
        <string key="time">
          <xsl:value-of select="EventDetail/Data[@Name=&quot;time&quot;]/@Value"/>
        </string>
      </xsl:if>
      <xsl:if test="EventDetail/Data[@Name=&quot;min&quot;]/@Value">
        <string key="time.min">
          <xsl:value-of select="EventDetail/Data[@Name=&quot;min&quot;]/@Value"/>
        </string>
      </xsl:if>
      <xsl:if test="EventDetail/Data[@Name=&quot;max&quot;]/@Value">
        <string key="time.max.x">
          <xsl:value-of select="EventDetail/Data[@Name=&quot;max&quot;]/@Value"/>
        </string>
      </xsl:if>
      <xsl:apply-templates select="." mode="a"/>
    </map>
  </xsl:template>
  <!-- a: a.b.c from EventDetail/Data[@Name="c"]/@Value -->
  <xsl:template match="Event" mode="a">
    <xsl:variable name="a_b_c" select="EventDetail/Data[@Name=&quot;c&quot;]/@Value"/>
    <xsl:if test="$a_b_c">
      <map key="a">
        <map key="b">
          <xsl:choose>
            <xsl:when test="count($a_b_c) gt 1">
              <array key="c">
                <xsl:for-each select="$a_b_c">
                  <number>
                    <xsl:value-of select="."/>
                  </number>
                </xsl:for-each>
              </array>
            </xsl:when>
            <xsl:when test="$a_b_c">
              <number key="c">
                <xsl:value-of select="$a_b_c"/>
              </number>
            </xsl:when>
          </xsl:choose>
        </map>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
