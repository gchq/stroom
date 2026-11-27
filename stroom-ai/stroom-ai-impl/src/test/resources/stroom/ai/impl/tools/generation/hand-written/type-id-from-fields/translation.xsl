<?xml version="1.1" encoding="UTF-8"?>
<!-- A hand-written translation whose TypeId is built from two fields with concat(). -->
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xpath-default-namespace="http://www.w3.org/2013/XSL/json" version="3.0">

  <xsl:template match="/">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd" Version="4.1.0">
      <xsl:apply-templates select="array/map"/>
    </Events>
  </xsl:template>

  <xsl:template match="map">
    <xsl:variable name="operation" select="string[@key='operation']"/>
    <Event>
      <EventTime>
        <TimeCreated><xsl:value-of select="string[@key='eventTime']"/></TimeCreated>
      </EventTime>
      <EventSource>
        <System>
          <Name>Example Cloud</Name>
          <Environment>Production</Environment>
        </System>
        <Generator>audit</Generator>
        <Device>
          <HostName>audit.example.org</HostName>
        </Device>
        <Client>
          <IPAddress><xsl:value-of select="map[@key='actor']/string[@key='ip']"/></IPAddress>
        </Client>
        <User>
          <Id><xsl:value-of select="map[@key='actor']/string[@key='name']"/></Id>
        </User>
      </EventSource>
      <EventDetail>
        <TypeId><xsl:value-of select="concat(string[@key='service'], ':', $operation)"/></TypeId>
        <xsl:choose>
          <xsl:when test="starts-with($operation, 'Get')">
            <View>
              <Object>
                <Id><xsl:value-of select="string[@key='resource']"/></Id>
              </Object>
            </View>
          </xsl:when>
          <xsl:when test="starts-with($operation, 'Delete')">
            <Delete>
              <Object>
                <Id><xsl:value-of select="string[@key='resource']"/></Id>
              </Object>
            </Delete>
          </xsl:when>
          <xsl:otherwise>
            <Create>
              <Object>
                <Id><xsl:value-of select="string[@key='resource']"/></Id>
              </Object>
            </Create>
          </xsl:otherwise>
        </xsl:choose>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
