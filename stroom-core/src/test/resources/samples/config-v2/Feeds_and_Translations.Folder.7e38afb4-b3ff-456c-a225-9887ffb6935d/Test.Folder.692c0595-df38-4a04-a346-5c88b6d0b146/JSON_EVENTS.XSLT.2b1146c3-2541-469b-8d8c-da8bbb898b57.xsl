<?xml version="1.1" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="http://www.w3.org/2013/XSL/json" xmlns="event-logging:3"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns:stroom="stroom" version="2.0">

  <!-- Root template -->
  <xsl:template match="/">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v3.0.0.xsd" Version="3.0.0">
      <xsl:apply-templates />
    </Events>
  </xsl:template>

  <!-- Handle each event -->
  <xsl:template match="/map/map">
    <Event>
      <xsl:apply-templates select="string[@key='EventTime']" />
      <EventSource>
        <System>
          <Name>
            <xsl:value-of select="stroom:feed-attribute('System')" />
          </Name>
          <Environment>
            <xsl:value-of select="stroom:feed-attribute('Environment')" />
          </Environment>
        </System>
        <Generator>Apache HTTPD</Generator>
        <xsl:apply-templates select="string[@key='sourceAddress']" />
        <xsl:apply-templates select="string[@key='destinationUserName']" />
      </EventSource>
      <EventDetail>
        <TypeId>0001</TypeId>
        <View>
          <Resource>
            <Type>WebPage</Type>
            <URL>
              <xsl:value-of select="string[@key='request']" />
            </URL>
            <ResponseCode>
              <xsl:value-of select="number[@key='status']" />
            </ResponseCode>
          </Resource>
        </View>
      </EventDetail>
    </Event>
  </xsl:template>
  <xsl:template match="string[@key='EventTime']">
    <EventTime>
      <TimeCreated>
        <xsl:value-of select="stroom:format-date(text(), 'yyyy-MM-dd HH:mm:ss')" />
      </TimeCreated>
    </EventTime>
  </xsl:template>
  <xsl:template match="string[@key='sourceAddress']">
    <Device>
      <IPAddress>
        <xsl:value-of select="text()" />
      </IPAddress>
    </Device>
  </xsl:template>
  <xsl:template match="string[@key='destinationUserName']">
    <User>
      <Id>
        <xsl:value-of select="text()" />
      </Id>
    </User>
  </xsl:template>
</xsl:stylesheet>
