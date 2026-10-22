<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="3.0">
  <xsl:template match="/Events">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="Event"/>
    </array>
  </xsl:template>
  <!-- Each Event: one document for the index acme-v1 -->
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
      <xsl:apply-templates select="." mode="user"/>
      <xsl:apply-templates select="." mode="source"/>
      <xsl:apply-templates select="." mode="http"/>
      <xsl:apply-templates select="." mode="event"/>
      <xsl:apply-templates select="." mode="acme"/>
      <xsl:if test="EventDetail/Description">
        <string key="message">
          <xsl:value-of select="EventDetail/Description"/>
        </string>
      </xsl:if>
    </map>
  </xsl:template>
  <!-- user: user.name from EventSource/User/Id; user.domain from EventSource/User/Domain -->
  <xsl:template match="Event" mode="user">
    <xsl:if test="EventSource/User/Id or EventSource/User/Domain">
      <map key="user">
        <xsl:if test="EventSource/User/Id">
          <string key="name">
            <xsl:value-of select="EventSource/User/Id"/>
          </string>
        </xsl:if>
        <xsl:if test="EventSource/User/Domain">
          <string key="domain">
            <xsl:value-of select="EventSource/User/Domain"/>
          </string>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- source: source.ip from EventSource/Client/IPAddress; source.port from EventSource/Client/Port -->
  <xsl:template match="Event" mode="source">
    <xsl:if test="EventSource/Client/IPAddress or EventSource/Client/Port">
      <map key="source">
        <xsl:if test="EventSource/Client/IPAddress">
          <string key="ip">
            <xsl:value-of select="EventSource/Client/IPAddress"/>
          </string>
        </xsl:if>
        <xsl:if test="EventSource/Client/Port">
          <number key="port">
            <xsl:value-of select="EventSource/Client/Port"/>
          </number>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- http: http.request.method from EventDetail/*/Resource/HTTPMethod; http.request.body.bytes from
       EventDetail/*/Resource/InboundSize -->
  <xsl:template match="Event" mode="http">
    <xsl:if test="EventDetail/*/Resource/HTTPMethod or EventDetail/*/Resource/InboundSize">
      <map key="http">
        <map key="request">
          <xsl:if test="EventDetail/*/Resource/HTTPMethod">
            <string key="method">
              <xsl:value-of select="EventDetail/*/Resource/HTTPMethod"/>
            </string>
          </xsl:if>
          <xsl:if test="EventDetail/*/Resource/InboundSize">
            <map key="body">
              <number key="bytes">
                <xsl:value-of select="EventDetail/*/Resource/InboundSize"/>
              </number>
            </map>
          </xsl:if>
        </map>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- event: event.outcome from EventDetail/*/Outcome/Success -->
  <xsl:template match="Event" mode="event">
    <xsl:if test="EventDetail/*/Outcome/Success">
      <map key="event">
        <string key="outcome">
          <xsl:value-of select="EventDetail/*/Outcome/Success ! (if (. = 'false') then 'failure' else 'success')"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- acme: acme.ticket from EventDetail/Data[@Name='ticket']/@Value; acme.ports from
       EventDetail/Data[@Name="port"]/@Value -->
  <xsl:template match="Event" mode="acme">
    <xsl:if test="EventDetail/Data[@Name='ticket']/@Value or EventDetail/Data[@Name=&quot;port&quot;]/@Value">
      <map key="acme">
        <xsl:if test="EventDetail/Data[@Name='ticket']/@Value">
          <string key="ticket">
            <xsl:value-of select="EventDetail/Data[@Name='ticket']/@Value"/>
          </string>
        </xsl:if>
        <xsl:choose>
          <xsl:when test="count(EventDetail/Data[@Name=&quot;port&quot;]/@Value) gt 1">
            <array key="ports">
              <xsl:for-each select="EventDetail/Data[@Name=&quot;port&quot;]/@Value">
                <number>
                  <xsl:value-of select="."/>
                </number>
              </xsl:for-each>
            </array>
          </xsl:when>
          <xsl:when test="EventDetail/Data[@Name=&quot;port&quot;]/@Value">
            <number key="ports">
              <xsl:value-of select="EventDetail/Data[@Name=&quot;port&quot;]/@Value"/>
            </number>
          </xsl:when>
        </xsl:choose>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
