<?xml version="1.1" encoding="UTF-8"?>
<!-- A hand-written translation: the event built from named templates called with parameters. -->
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xpath-default-namespace="records:2" version="2.0">

  <xsl:template match="records">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd" Version="4.1.0">
      <xsl:apply-templates/>
    </Events>
  </xsl:template>

  <xsl:template match="record">
    <Event>
      <xsl:call-template name="event-time"/>
      <xsl:call-template name="event-source">
        <xsl:with-param name="user" select="data[@name='user']/@value"/>
      </xsl:call-template>
      <EventDetail>
        <xsl:choose>
          <xsl:when test="data[@name='status']/@value = '403'">
            <TypeId>web-denied</TypeId>
            <Description>A request was refused</Description>
            <View>
              <xsl:call-template name="resource">
                <xsl:with-param name="url" select="data[@name='url']/@value"/>
              </xsl:call-template>
              <Outcome>
                <Success>false</Success>
              </Outcome>
            </View>
          </xsl:when>
          <xsl:when test="data[@name='method']/@value = 'POST'">
            <TypeId>web-upload</TypeId>
            <Description>A resource was uploaded</Description>
            <Create>
              <xsl:call-template name="resource">
                <xsl:with-param name="url" select="data[@name='url']/@value"/>
              </xsl:call-template>
              <Outcome>
                <Success>true</Success>
              </Outcome>
            </Create>
          </xsl:when>
          <xsl:otherwise>
            <TypeId>web-view</TypeId>
            <Description>A resource was viewed</Description>
            <View>
              <xsl:call-template name="resource">
                <xsl:with-param name="url" select="data[@name='url']/@value"/>
              </xsl:call-template>
              <Outcome>
                <Success>true</Success>
              </Outcome>
            </View>
          </xsl:otherwise>
        </xsl:choose>
      </EventDetail>
    </Event>
  </xsl:template>

  <xsl:template name="event-time">
    <EventTime>
      <TimeCreated><xsl:value-of select="data[@name='time']/@value"/></TimeCreated>
    </EventTime>
  </xsl:template>

  <xsl:template name="event-source">
    <xsl:param name="user"/>
    <EventSource>
      <System>
        <Name>Example Web Proxy</Name>
        <Environment>Production</Environment>
      </System>
      <Generator>proxyd</Generator>
      <Device>
        <IPAddress><xsl:value-of select="data[@name='client']/@value"/></IPAddress>
      </Device>
      <xsl:if test="$user != '-'">
        <User>
          <Id><xsl:value-of select="$user"/></Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>

  <xsl:template name="resource">
    <xsl:param name="url"/>
    <Resource>
      <URL><xsl:value-of select="$url"/></URL>
      <Data Name="method" Value="{data[@name='method']/@value}"/>
      <Data Name="status" Value="{data[@name='status']/@value}"/>
      <Data Name="bytes" Value="{data[@name='bytes']/@value}"/>
    </Resource>
  </xsl:template>
</xsl:stylesheet>
