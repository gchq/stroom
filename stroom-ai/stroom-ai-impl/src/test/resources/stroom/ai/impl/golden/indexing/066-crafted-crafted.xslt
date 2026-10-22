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
      <xsl:call-template name="user"/>
      <xsl:call-template name="source"/>
      <xsl:call-template name="http"/>
      <xsl:call-template name="event"/>
      <xsl:call-template name="acme"/>
      <xsl:if test="EventDetail/Description">
        <string key="message">
          <xsl:value-of select="EventDetail/Description"/>
        </string>
      </xsl:if>
    </map>
  </xsl:template>
  <!-- user: user.name from EventSource/User/Id; user.domain from EventSource/User/Domain -->
  <xsl:template name="user">
    <xsl:variable name="user-name" select="EventSource/User/Id"/>
    <xsl:variable name="user-domain" select="EventSource/User/Domain"/>
    <xsl:if test="$user-name or $user-domain">
      <map key="user">
        <xsl:if test="$user-name">
          <string key="name">
            <xsl:value-of select="$user-name"/>
          </string>
        </xsl:if>
        <xsl:if test="$user-domain">
          <string key="domain">
            <xsl:value-of select="$user-domain"/>
          </string>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- source: source.ip from EventSource/Client/IPAddress; source.port from EventSource/Client/Port -->
  <xsl:template name="source">
    <xsl:variable name="source-ip" select="EventSource/Client/IPAddress"/>
    <xsl:variable name="source-port" select="EventSource/Client/Port"/>
    <xsl:if test="$source-ip or $source-port">
      <map key="source">
        <xsl:if test="$source-ip">
          <string key="ip">
            <xsl:value-of select="$source-ip"/>
          </string>
        </xsl:if>
        <xsl:if test="$source-port">
          <number key="port">
            <xsl:value-of select="$source-port"/>
          </number>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- http: http.request.method from EventDetail/*/Resource/HTTPMethod; http.request.body.bytes from
       EventDetail/*/Resource/InboundSize -->
  <xsl:template name="http">
    <xsl:variable name="http-request-method" select="EventDetail/*/Resource/HTTPMethod"/>
    <xsl:variable name="http-request-body-bytes" select="EventDetail/*/Resource/InboundSize"/>
    <xsl:if test="$http-request-method or $http-request-body-bytes">
      <map key="http">
        <map key="request">
          <xsl:if test="$http-request-method">
            <string key="method">
              <xsl:value-of select="$http-request-method"/>
            </string>
          </xsl:if>
          <xsl:if test="$http-request-body-bytes">
            <map key="body">
              <number key="bytes">
                <xsl:value-of select="$http-request-body-bytes"/>
              </number>
            </map>
          </xsl:if>
        </map>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- event: event.outcome from EventDetail/*/Outcome/Success -->
  <xsl:template name="event">
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
  <xsl:template name="acme">
    <xsl:variable name="acme-ticket" select="EventDetail/Data[@Name='ticket']/@Value"/>
    <xsl:variable name="acme-ports" select="EventDetail/Data[@Name=&quot;port&quot;]/@Value"/>
    <xsl:if test="$acme-ticket or $acme-ports">
      <map key="acme">
        <xsl:if test="$acme-ticket">
          <string key="ticket">
            <xsl:value-of select="$acme-ticket"/>
          </string>
        </xsl:if>
        <xsl:choose>
          <xsl:when test="count($acme-ports) gt 1">
            <array key="ports">
              <xsl:for-each select="$acme-ports">
                <number>
                  <xsl:value-of select="."/>
                </number>
              </xsl:for-each>
            </array>
          </xsl:when>
          <xsl:when test="$acme-ports">
            <number key="ports">
              <xsl:value-of select="$acme-ports"/>
            </number>
          </xsl:when>
        </xsl:choose>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
