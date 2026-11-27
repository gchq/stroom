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
    <xsl:variable name="userName" select="EventSource/User/Id"/>
    <xsl:variable name="userDomain" select="EventSource/User/Domain"/>
    <xsl:if test="$userName or $userDomain">
      <map key="user">
        <xsl:if test="$userName">
          <string key="name">
            <xsl:value-of select="$userName"/>
          </string>
        </xsl:if>
        <xsl:if test="$userDomain">
          <string key="domain">
            <xsl:value-of select="$userDomain"/>
          </string>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- source: source.ip from EventSource/Client/IPAddress; source.port from EventSource/Client/Port -->
  <xsl:template match="Event" mode="source">
    <xsl:variable name="sourceIp" select="EventSource/Client/IPAddress"/>
    <xsl:variable name="sourcePort" select="EventSource/Client/Port"/>
    <xsl:if test="$sourceIp or $sourcePort">
      <map key="source">
        <xsl:if test="$sourceIp">
          <string key="ip">
            <xsl:value-of select="$sourceIp"/>
          </string>
        </xsl:if>
        <xsl:if test="$sourcePort">
          <number key="port">
            <xsl:value-of select="$sourcePort"/>
          </number>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- http: http.request.method from EventDetail/*/Resource/HTTPMethod; http.request.body.bytes from
       EventDetail/*/Resource/InboundSize -->
  <xsl:template match="Event" mode="http">
    <xsl:variable name="httpRequestMethod" select="EventDetail/*/Resource/HTTPMethod"/>
    <xsl:variable name="httpRequestBodyBytes" select="EventDetail/*/Resource/InboundSize"/>
    <xsl:if test="$httpRequestMethod or $httpRequestBodyBytes">
      <map key="http">
        <map key="request">
          <xsl:if test="$httpRequestMethod">
            <string key="method">
              <xsl:value-of select="$httpRequestMethod"/>
            </string>
          </xsl:if>
          <xsl:if test="$httpRequestBodyBytes">
            <map key="body">
              <number key="bytes">
                <xsl:value-of select="$httpRequestBodyBytes"/>
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
    <xsl:variable name="acmeTicket" select="EventDetail/Data[@Name='ticket']/@Value"/>
    <xsl:variable name="acmePorts" select="EventDetail/Data[@Name=&quot;port&quot;]/@Value"/>
    <xsl:if test="$acmeTicket or $acmePorts">
      <map key="acme">
        <xsl:if test="$acmeTicket">
          <string key="ticket">
            <xsl:value-of select="$acmeTicket"/>
          </string>
        </xsl:if>
        <xsl:choose>
          <xsl:when test="count($acmePorts) gt 1">
            <array key="ports">
              <xsl:for-each select="$acmePorts">
                <number>
                  <xsl:value-of select="."/>
                </number>
              </xsl:for-each>
            </array>
          </xsl:when>
          <xsl:when test="$acmePorts">
            <number key="ports">
              <xsl:value-of select="$acmePorts"/>
            </number>
          </xsl:when>
        </xsl:choose>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
