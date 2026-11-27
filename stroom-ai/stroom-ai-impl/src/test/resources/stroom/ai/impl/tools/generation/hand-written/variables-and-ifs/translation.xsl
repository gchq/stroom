<?xml version="1.1" encoding="UTF-8"?>
<!-- A hand-written translation: variables at the top of the record template and a chain of xsl:if, each writing
     its own event. -->
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xpath-default-namespace="http://www.w3.org/2013/XSL/json" version="3.0">

  <xsl:template match="/">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd" Version="4.1.0">
      <xsl:apply-templates select="array/map"/>
    </Events>
  </xsl:template>

  <xsl:template match="map">
    <xsl:variable name="kind" select="string[@key='kind']"/>
    <xsl:variable name="outcome" select="string[@key='outcome']"/>
    <xsl:variable name="reader" select="string[@key='reader']"/>
    <xsl:if test="$kind = 'door' and $outcome = 'granted'">
      <Event>
        <EventTime>
          <TimeCreated><xsl:value-of select="string[@key='when']"/></TimeCreated>
        </EventTime>
        <EventSource>
          <System>
            <Name>Example Access Control</Name>
            <Environment>Production</Environment>
          </System>
          <Generator>door-controller</Generator>
          <Device>
            <Location>
              <Room><xsl:value-of select="$reader"/></Room>
            </Location>
          </Device>
          <User>
            <Id><xsl:value-of select="string[@key='holder']"/></Id>
          </User>
        </EventSource>
        <EventDetail>
          <TypeId>door-granted</TypeId>
          <Description>A badge opened a door</Description>
          <Authenticate>
            <Action>Logon</Action>
            <LogonType>PhysicalAccess</LogonType>
            <User>
              <Id><xsl:value-of select="string[@key='badge']"/></Id>
            </User>
            <Outcome>
              <Success>true</Success>
            </Outcome>
          </Authenticate>
        </EventDetail>
      </Event>
    </xsl:if>
    <xsl:if test="$kind = 'door' and $outcome = 'denied'">
      <Event>
        <EventTime>
          <TimeCreated><xsl:value-of select="string[@key='when']"/></TimeCreated>
        </EventTime>
        <EventSource>
          <System>
            <Name>Example Access Control</Name>
            <Environment>Production</Environment>
          </System>
          <Generator>door-controller</Generator>
          <Device>
            <Location>
              <Room><xsl:value-of select="$reader"/></Room>
            </Location>
          </Device>
        </EventSource>
        <EventDetail>
          <TypeId>door-denied</TypeId>
          <Description>A badge was refused at a door</Description>
          <Authenticate>
            <Action>Logon</Action>
            <LogonType>PhysicalAccess</LogonType>
            <User>
              <Id><xsl:value-of select="string[@key='badge']"/></Id>
            </User>
            <Outcome>
              <Success>false</Success>
              <xsl:if test="string[@key='reason'] != ''">
                <Data Name="reason" Value="{string[@key='reason']}"/>
              </xsl:if>
            </Outcome>
          </Authenticate>
        </EventDetail>
      </Event>
    </xsl:if>
    <xsl:if test="$kind = 'alarm'">
      <Event>
        <EventTime>
          <TimeCreated><xsl:value-of select="string[@key='when']"/></TimeCreated>
        </EventTime>
        <EventSource>
          <System>
            <Name>Example Access Control</Name>
            <Environment>Production</Environment>
          </System>
          <Generator>door-controller</Generator>
          <Device>
            <Location>
              <Room><xsl:value-of select="$reader"/></Room>
            </Location>
          </Device>
        </EventSource>
        <EventDetail>
          <TypeId>door-alarm</TypeId>
          <Description>A door raised an alarm</Description>
          <Alert>
            <Type>Other</Type>
            <Description><xsl:value-of select="string[@key='detail']"/></Description>
          </Alert>
        </EventDetail>
      </Event>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
