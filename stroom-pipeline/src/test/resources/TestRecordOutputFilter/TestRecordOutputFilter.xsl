<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="records:2"
  xmlns="event-logging:3" xmlns:stroom="stroom"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  version="2.0">

  <xsl:template match="records">
    <Events
      xsi:schemaLocation="event-logging:3 file://event-logging-v3.0.0.xsd"
      Version="3.0.0">
      <xsl:apply-templates />
    </Events>
  </xsl:template>
  <xsl:template
    match="record[data[@name = 'url'] and contains(data[@name = 'url']/@value, 'good')]">
    <Event>
      <xsl:apply-templates select="." mode="content" />
    </Event>
  </xsl:template>
  <xsl:template
    match="record[data[@name = 'url'] and contains(data[@name = 'url']/@value, 'bad')]">
    <Eventy>
      <xsl:apply-templates select="." mode="content" />
    </Eventy>
  </xsl:template>
  <xsl:template match="record" mode="content">
    <xsl:variable name="ipAddress" select="data[1]/@value" />
    <xsl:variable name="userId" select="data[3]/@value" />
    <xsl:variable name="dateTime" select="data[4]/@value" />
    <xsl:variable name="httpMethod"
      select="replace(data[5]/@value, ' .*' , '')" />
    <xsl:variable name="url"
      select="replace(data[5]/@value, '^\S+\s|\s\S+' , '')" />
    <xsl:variable name="httpVersion"
      select="replace(data[5]/@value, '.*\s' , '')" />
    <xsl:variable name="responseCode" select="data[6]/@value" />
    <xsl:variable name="size" select="data[7]/@value" />
    <xsl:variable name="referrer" select="data[8]/@value" />
    <xsl:variable name="userAgent"
      select="replace(data[9]/@value, '\S+: ' , '')" />
    <xsl:variable name="formattedDate"
      select="stroom:format-date($dateTime, 'dd/MMM/yyyy:HH:mm:ss Z')" />
    <EventTime>
      <TimeCreated>
        <xsl:value-of select="$formattedDate" />
      </TimeCreated>
    </EventTime>
    <EventSource>
      <System>
        <Name>Some System</Name>
        <Environment>Reference</Environment>
        <Organisation>Some Org</Organisation>
      </System>
      <Generator>Some-Event-Log-Generator</Generator>
      <Device>
        <HostName>test.test.com</HostName>
      </Device>
      <Client>
        <IPAddress>
          <xsl:value-of select="$ipAddress" />
        </IPAddress>
      </Client>
      <xsl:if test="$userId != '-'">
        <User>
          <Id>
            <xsl:value-of select="$userId" />
          </Id>
        </User>
      </xsl:if>
    </EventSource>
    <EventDetail>
      <TypeId>1234</TypeId>
      <View>
        <Resource>
          <URL>
            <xsl:value-of select="$url" />
          </URL>
          <xsl:if test="$referrer != '-'">
            <Referrer>
              <xsl:value-of select="$referrer" />
            </Referrer>
          </xsl:if>
          <HTTPMethod>
            <xsl:value-of select="$httpMethod" />
          </HTTPMethod>
          <HTTPVersion>
            <xsl:value-of select="$httpVersion" />
          </HTTPVersion>
          <UserAgent>
            <xsl:value-of select="$userAgent" />
          </UserAgent>
          <ResponseCode>
            <xsl:value-of select="$responseCode" />
          </ResponseCode>
        </Resource>
      </View>
    </EventDetail>
  </xsl:template>
</xsl:stylesheet>