<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="2.0">
  <xsl:template match="/Events">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="Event"/>
    </array>
  </xsl:template>
  <!-- Each Event: one document for the index acme-v1 -->
  <xsl:template match="Event">
    <xsl:variable name="stream_id" select="@StreamId"/>
    <xsl:variable name="event_id" select="@EventId"/>
    <xsl:variable name="timestamp" select="EventTime/TimeCreated"/>
    <xsl:variable name="message" select="EventDetail/Description"/>
    <map>
      <xsl:if test="$stream_id">
        <number key="StreamId">
          <xsl:value-of select="$stream_id"/>
        </number>
      </xsl:if>
      <xsl:if test="$event_id">
        <number key="EventId">
          <xsl:value-of select="$event_id"/>
        </number>
      </xsl:if>
      <xsl:if test="$timestamp">
        <string key="@timestamp">
          <xsl:value-of select="$timestamp"/>
        </string>
      </xsl:if>
      <xsl:call-template name="user"/>
      <xsl:call-template name="source"/>
      <xsl:call-template name="http"/>
      <xsl:call-template name="event"/>
      <xsl:call-template name="acme"/>
      <xsl:if test="$message">
        <string key="message">
          <xsl:value-of select="$message"/>
        </string>
      </xsl:if>
    </map>
  </xsl:template>
  <!-- user: user.name from EventSource/User/Id; user.domain from EventSource/User/Domain -->
  <xsl:template name="user">
    <xsl:variable name="user_name" select="EventSource/User/Id"/>
    <xsl:variable name="user_domain" select="EventSource/User/Domain"/>
    <xsl:if test="$user_name or $user_domain">
      <map key="user">
        <xsl:if test="$user_name">
          <string key="name">
            <xsl:value-of select="$user_name"/>
          </string>
        </xsl:if>
        <xsl:if test="$user_domain">
          <string key="domain">
            <xsl:value-of select="$user_domain"/>
          </string>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- source: source.ip from EventSource/Client/IPAddress; source.port from EventSource/Client/Port -->
  <xsl:template name="source">
    <xsl:variable name="source_ip" select="EventSource/Client/IPAddress"/>
    <xsl:variable name="source_port" select="EventSource/Client/Port"/>
    <xsl:if test="$source_ip or $source_port">
      <map key="source">
        <xsl:if test="$source_ip">
          <string key="ip">
            <xsl:value-of select="$source_ip"/>
          </string>
        </xsl:if>
        <xsl:if test="$source_port">
          <number key="port">
            <xsl:value-of select="$source_port"/>
          </number>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- http: http.request.method from EventDetail/*/Resource/HTTPMethod; http.request.body.bytes from
       EventDetail/*/Resource/InboundSize -->
  <xsl:template name="http">
    <xsl:variable name="http_request_method" select="EventDetail/*/Resource/HTTPMethod"/>
    <xsl:variable name="http_request_body_bytes" select="EventDetail/*/Resource/InboundSize"/>
    <xsl:if test="$http_request_method or $http_request_body_bytes">
      <map key="http">
        <map key="request">
          <xsl:if test="$http_request_method">
            <string key="method">
              <xsl:value-of select="$http_request_method"/>
            </string>
          </xsl:if>
          <xsl:if test="$http_request_body_bytes">
            <map key="body">
              <number key="bytes">
                <xsl:value-of select="$http_request_body_bytes"/>
              </number>
            </map>
          </xsl:if>
        </map>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- event: event.outcome from EventDetail/*/Outcome/Success -->
  <xsl:template name="event">
    <xsl:variable name="event_outcome" select="EventDetail/*/Outcome/Success"/>
    <xsl:if test="$event_outcome">
      <map key="event">
        <string key="outcome">
          <xsl:value-of select="$event_outcome ! (if (. = 'false') then 'failure' else 'success')"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- acme: acme.ticket from EventDetail/Data[@Name='ticket']/@Value; acme.ports from
       EventDetail/Data[@Name="port"]/@Value -->
  <xsl:template name="acme">
    <xsl:variable name="acme_ticket" select="EventDetail/Data[@Name='ticket']/@Value"/>
    <xsl:variable name="acme_ports" select="EventDetail/Data[@Name=&quot;port&quot;]/@Value"/>
    <xsl:if test="$acme_ticket or $acme_ports">
      <map key="acme">
        <xsl:if test="$acme_ticket">
          <string key="ticket">
            <xsl:value-of select="$acme_ticket"/>
          </string>
        </xsl:if>
        <xsl:choose>
          <xsl:when test="count($acme_ports) gt 1">
            <array key="ports">
              <xsl:for-each select="$acme_ports">
                <number>
                  <xsl:value-of select="."/>
                </number>
              </xsl:for-each>
            </array>
          </xsl:when>
          <xsl:when test="$acme_ports">
            <number key="ports">
              <xsl:value-of select="$acme_ports"/>
            </number>
          </xsl:when>
        </xsl:choose>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
