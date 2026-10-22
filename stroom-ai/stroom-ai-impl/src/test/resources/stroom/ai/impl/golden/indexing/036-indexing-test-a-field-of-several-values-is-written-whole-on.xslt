<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="3.0">
  <xsl:template match="/Events">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="Event"/>
    </array>
  </xsl:template>
  <!-- Each Event: one document for the index x -->
  <xsl:template match="Event">
    <map>
      <xsl:if test="@StreamId">
        <number key="StreamId">
          <xsl:value-of select="@StreamId"/>
        </number>
      </xsl:if>
      <xsl:if test="EventTime/TimeCreated">
        <string key="@timestamp">
          <xsl:value-of select="EventTime/TimeCreated"/>
        </string>
      </xsl:if>
      <xsl:apply-templates select="." mode="user"/>
      <xsl:apply-templates select="." mode="acme"/>
    </map>
  </xsl:template>
  <!-- user: user.name from EventSource/User/Id -->
  <xsl:template match="Event" mode="user">
    <xsl:if test="EventSource/User/Id">
      <map key="user">
        <string key="name">
          <xsl:value-of select="EventSource/User/Id"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- acme: acme.port from EventDetail/Authenticate/Data[@Name='port']/@Value; acme.tag from
       EventDetail/Authenticate/Data[@Name='tag']/@Value; acme.none from
       EventDetail/Authenticate/Data[@Name='none']/@Value -->
  <xsl:template match="Event" mode="acme">
    <xsl:variable name="acme_port" select="EventDetail/Authenticate/Data[@Name='port']/@Value"/>
    <xsl:variable name="acme_tag" select="EventDetail/Authenticate/Data[@Name='tag']/@Value"/>
    <xsl:variable name="acme_none" select="EventDetail/Authenticate/Data[@Name='none']/@Value"/>
    <xsl:if test="$acme_port or $acme_tag or $acme_none">
      <map key="acme">
        <xsl:choose>
          <xsl:when test="count($acme_port) gt 1">
            <array key="port">
              <xsl:for-each select="$acme_port">
                <number>
                  <xsl:value-of select="."/>
                </number>
              </xsl:for-each>
            </array>
          </xsl:when>
          <xsl:when test="$acme_port">
            <number key="port">
              <xsl:value-of select="$acme_port"/>
            </number>
          </xsl:when>
        </xsl:choose>
        <xsl:choose>
          <xsl:when test="count($acme_tag) gt 1">
            <array key="tag">
              <xsl:for-each select="$acme_tag">
                <string>
                  <xsl:value-of select="."/>
                </string>
              </xsl:for-each>
            </array>
          </xsl:when>
          <xsl:when test="$acme_tag">
            <string key="tag">
              <xsl:value-of select="$acme_tag"/>
            </string>
          </xsl:when>
        </xsl:choose>
        <xsl:choose>
          <xsl:when test="count($acme_none) gt 1">
            <array key="none">
              <xsl:for-each select="$acme_none">
                <string>
                  <xsl:value-of select="."/>
                </string>
              </xsl:for-each>
            </array>
          </xsl:when>
          <xsl:when test="$acme_none">
            <string key="none">
              <xsl:value-of select="$acme_none"/>
            </string>
          </xsl:when>
        </xsl:choose>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
