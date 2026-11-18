<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="3.0">
  <xsl:template match="/Events">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="Event"/>
    </array>
  </xsl:template>
  <!-- Each Event: one document for the index ecs-acme-v1 -->
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
      <xsl:call-template name="http"/>
      <xsl:call-template name="event"/>
    </map>
  </xsl:template>
  <!-- user: user.name from EventSource/User/Id -->
  <xsl:template name="user">
    <xsl:if test="EventSource/User/Id">
      <map key="user">
        <string key="name">
          <xsl:value-of select="EventSource/User/Id"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- http: http.request.method from EventDetail/*/Resource/HTTPMethod; http.request.body.bytes from
       EventDetail/*/Resource/InboundSize -->
  <xsl:template name="http">
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
  <xsl:template name="event">
    <xsl:if test="EventDetail/*/Outcome/Success">
      <map key="event">
        <string key="outcome">
          <xsl:value-of select="EventDetail/*/Outcome/Success ! (if (. = 'false') then 'failure' else 'success')"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
