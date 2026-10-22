<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="2.0">
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
      <xsl:apply-templates select="." mode="User"/>
      <xsl:apply-templates select="." mode="Source"/>
      <xsl:apply-templates select="." mode="Http"/>
      <xsl:apply-templates select="." mode="Event"/>
      <xsl:apply-templates select="." mode="Acme"/>
      <xsl:if test="EventDetail/Description">
        <string key="message">
          <xsl:value-of select="EventDetail/Description"/>
        </string>
      </xsl:if>
    </map>
  </xsl:template>
  <!-- User: user.name from EventSource/User/Id; user.domain from EventSource/User/Domain -->
  <xsl:template match="Event" mode="User">
    <xsl:variable name="UserName" select="EventSource/User/Id"/>
    <xsl:variable name="UserDomain" select="EventSource/User/Domain"/>
    <xsl:if test="$UserName or $UserDomain">
      <map key="user">
        <xsl:if test="$UserName">
          <string key="name">
            <xsl:value-of select="$UserName"/>
          </string>
        </xsl:if>
        <xsl:if test="$UserDomain">
          <string key="domain">
            <xsl:value-of select="$UserDomain"/>
          </string>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- Source: source.ip from EventSource/Client/IPAddress; source.port from EventSource/Client/Port -->
  <xsl:template match="Event" mode="Source">
    <xsl:variable name="SourceIp" select="EventSource/Client/IPAddress"/>
    <xsl:variable name="SourcePort" select="EventSource/Client/Port"/>
    <xsl:if test="$SourceIp or $SourcePort">
      <map key="source">
        <xsl:if test="$SourceIp">
          <string key="ip">
            <xsl:value-of select="$SourceIp"/>
          </string>
        </xsl:if>
        <xsl:if test="$SourcePort">
          <number key="port">
            <xsl:value-of select="$SourcePort"/>
          </number>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- Http: http.request.method from EventDetail/*/Resource/HTTPMethod; http.request.body.bytes from
       EventDetail/*/Resource/InboundSize -->
  <xsl:template match="Event" mode="Http">
    <xsl:variable name="HttpRequestMethod" select="EventDetail/*/Resource/HTTPMethod"/>
    <xsl:variable name="HttpRequestBodyBytes" select="EventDetail/*/Resource/InboundSize"/>
    <xsl:if test="$HttpRequestMethod or $HttpRequestBodyBytes">
      <map key="http">
        <map key="request">
          <xsl:if test="$HttpRequestMethod">
            <string key="method">
              <xsl:value-of select="$HttpRequestMethod"/>
            </string>
          </xsl:if>
          <xsl:if test="$HttpRequestBodyBytes">
            <map key="body">
              <number key="bytes">
                <xsl:value-of select="$HttpRequestBodyBytes"/>
              </number>
            </map>
          </xsl:if>
        </map>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- Event: event.outcome from EventDetail/*/Outcome/Success -->
  <xsl:template match="Event" mode="Event">
    <xsl:if test="EventDetail/*/Outcome/Success">
      <map key="event">
        <string key="outcome">
          <xsl:value-of select="EventDetail/*/Outcome/Success ! (if (. = 'false') then 'failure' else 'success')"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- Acme: acme.ticket from EventDetail/Data[@Name='ticket']/@Value; acme.ports from
       EventDetail/Data[@Name="port"]/@Value -->
  <xsl:template match="Event" mode="Acme">
    <xsl:variable name="AcmeTicket" select="EventDetail/Data[@Name='ticket']/@Value"/>
    <xsl:variable name="AcmePorts" select="EventDetail/Data[@Name=&quot;port&quot;]/@Value"/>
    <xsl:if test="$AcmeTicket or $AcmePorts">
      <map key="acme">
        <xsl:if test="$AcmeTicket">
          <string key="ticket">
            <xsl:value-of select="$AcmeTicket"/>
          </string>
        </xsl:if>
        <xsl:choose>
          <xsl:when test="count($AcmePorts) gt 1">
            <array key="ports">
              <xsl:for-each select="$AcmePorts">
                <number>
                  <xsl:value-of select="."/>
                </number>
              </xsl:for-each>
            </array>
          </xsl:when>
          <xsl:when test="$AcmePorts">
            <number key="ports">
              <xsl:value-of select="$AcmePorts"/>
            </number>
          </xsl:when>
        </xsl:choose>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
