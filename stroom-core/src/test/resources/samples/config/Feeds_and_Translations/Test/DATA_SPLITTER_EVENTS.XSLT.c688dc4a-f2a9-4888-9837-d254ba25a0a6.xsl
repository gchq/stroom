<?xml version="1.1" encoding="UTF-8" ?>
<!-- UK Crown Copyright © 2016 -->
<xsl:stylesheet xpath-default-namespace="records:2" xmlns="event-logging:3" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.0">
  <xsl:template match="records">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v3.0.0.xsd" Version="3.0.0">
      <xsl:apply-templates />
    </Events>
  </xsl:template>

  <!-- EG
  <Event>
  Date,Time,FileNo,LineNo,User,Message
  01/01/2010,00:00:00,1,1,user1,Some message 1
  01/01/2010,00:01:00,1,2,user2,Some message 2
  01/01/2010,00:02:00,1,3,user3,Some message 3
  01/01/2010,00:03:00,1,4,user4,Some message 4
  01/01/2010,00:04:00,1,5,user5,Some message 5
  01/01/2010,00:05:00,1,6,user6,Some message 6
  01/01/2010,00:06:00,1,7,user7,Some message 7
  01/01/2010,00:07:00,1,8,user8,Some message 8
  01/01/2010,00:08:00,1,9,user9,Some message 9
  01/01/2010,00:09:00,1,10,user10,Some message 10
  </Event>
  -->
  <xsl:template match="record">
    <xsl:variable name="user" select="data[@name='User']/@value" />
    <Event>
      <xsl:call-template name="header" />
      <EventDetail>
        <TypeId>0001</TypeId>
        <Description>
          <xsl:value-of select="data[@name='Message']/@value" />
        </Description>
        <Authenticate>
          <Action>Logon</Action>
          <LogonType>Interactive</LogonType>
          <User>
            <Id>
              <xsl:value-of select="$user" />
            </Id>
          </User>
          <Data Name="FileNo">
            <xsl:attribute name="Value" select="data[@name='FileNo']/@value" />
          </Data>
          <Data Name="LineNo">
            <xsl:attribute name="Value" select="data[@name='LineNo']/@value" />
          </Data>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <xsl:template name="header">
    <xsl:variable name="date" select="data[@name='Date']/@value" />
    <xsl:variable name="time" select="data[@name='Time']/@value" />
    <xsl:variable name="dateTime" select="concat($date, $time)" />
    <xsl:variable name="formattedDateTime" select="stroom:format-date($dateTime, 'dd/MM/yyyyHH:mm:ss')" />
    <xsl:variable name="user" select="data[@name='User']/@value" />
    <EventTime>
      <TimeCreated>
        <xsl:value-of select="$formattedDateTime" />
      </TimeCreated>
    </EventTime>
    <EventSource>
      <System>
        <Name>Test</Name>
        <Environment>Test</Environment>
      </System>
      <Generator>CSV</Generator>
      <Device>
        <IPAddress>1.1.1.1</IPAddress>
        <MACAddress>00-00-00-00-00-00</MACAddress>
        <xsl:variable name="location" select="stroom:lookup('FILENO_TO_LOCATION_MAP', data[@name='FileNo']/@value, $formattedDateTime)" />
        <xsl:if test="$location">
          <xsl:copy-of select="$location" />
        </xsl:if>
        <xsl:variable name="testBitmapLookup" select="stroom:bitmap-lookup('GreekAlphabet', '0x801', $formattedDateTime)" />
        <Data Name="Designation">
          <xsl:attribute name="Value" select="$testBitmapLookup//@Value" />
        </Data>
        <Data Name="Zone1">
          <xsl:attribute name="Value" select="stroom:lookup('IPToLocation', stroom:numeric-ip('192.168.1.1'))" />
        </Data>
        <Data Name="Zone2">
          <xsl:attribute name="Value" select="stroom:lookup('IPToLocation', stroom:numeric-ip('192.168.5.5'))" />
        </Data>
        <Data Name="Zone3">
          <xsl:attribute name="Value" select="stroom:lookup('IPToLocation', stroom:numeric-ip('192.168.12.5'))" />
        </Data>
        <Data Name="Zone4">
          <xsl:attribute name="Value" select="stroom:lookup('IPToLocation', stroom:numeric-ip('192.168.23.11'))" />
        </Data>
        <Data Name="Zone5">
          <xsl:attribute name="Value" select="stroom:lookup('IPToLocation', stroom:numeric-ip('192.168.31.255'))" />
        </Data>
      </Device>
      <User>
        <Id>
          <xsl:value-of select="$user" />
        </Id>
      </User>
      <Data Name="LocalTime">
        <xsl:attribute name="Value" select="stroom:format-date($dateTime, 'dd/MM/yyyyHH:mm:ss', 'UTC', 'd MMM yyyy HH:mm:ss xx', 'GMT/BST')" />
      </Data>
      <Data Name="LocalTime">
        <xsl:attribute name="Value" select="stroom:format-date($dateTime, 'dd/MM/yyyyHH:mm:ss', 'UTC', 'd MMM yyyy HH:mm:ss xxx', 'GMT/BST')" />
      </Data>
      <Data Name="LocalTime">
        <xsl:attribute name="Value" select="stroom:format-date($dateTime, 'dd/MM/yyyyHH:mm:ss', 'UTC', 'd MMM yyyy HH:mm:ss VV', 'GMT/BST')" />
      </Data>
    </EventSource>
  </xsl:template>
</xsl:stylesheet>
