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
      <xsl:if test="EventSource/User/Id">
        <map key="user">
          <string key="name">
            <xsl:value-of select="EventSource/User/Id"/>
          </string>
        </map>
      </xsl:if>
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
      <xsl:if test="EventDetail/*/Outcome/Success">
        <map key="event">
          <string key="outcome">
            <xsl:value-of select="EventDetail/*/Outcome/Success ! (if (. = 'false') then 'failure' else 'success')"/>
          </string>
        </map>
      </xsl:if>
    </map>
  </xsl:template>
</xsl:stylesheet>
