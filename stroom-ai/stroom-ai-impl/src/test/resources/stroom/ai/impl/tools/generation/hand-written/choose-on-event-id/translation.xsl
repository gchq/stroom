<?xml version="1.1" encoding="UTF-8"?>
<!-- A hand-written translation: one record template, the event chosen by a big xsl:choose on the event id. -->
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xpath-default-namespace="records:2" version="2.0">

  <xsl:template match="records">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd" Version="4.1.0">
      <xsl:apply-templates/>
    </Events>
  </xsl:template>

  <xsl:template match="record">
    <xsl:variable name="eventId" select="data[@name='EventID']/@value"/>
    <Event>
      <EventTime>
        <TimeCreated><xsl:value-of select="data[@name='TimeCreated']/@value"/></TimeCreated>
      </EventTime>
      <EventSource>
        <System>
          <Name>Example Directory</Name>
          <Environment>Production</Environment>
        </System>
        <Generator>Security Log</Generator>
        <Device>
          <HostName><xsl:value-of select="data[@name='Computer']/@value"/></HostName>
        </Device>
        <xsl:if test="data[@name='IpAddress']/@value != ''">
          <Client>
            <IPAddress><xsl:value-of select="data[@name='IpAddress']/@value"/></IPAddress>
          </Client>
        </xsl:if>
        <User>
          <Id><xsl:value-of select="(data[@name='TargetUserName']/@value, data[@name='SubjectUserName']/@value)[1]"/></Id>
        </User>
      </EventSource>
      <EventDetail>
        <xsl:choose>
          <xsl:when test="$eventId = '1001'">
            <TypeId>1001</TypeId>
            <Description>An account logged on</Description>
            <Authenticate>
              <Action>Logon</Action>
              <User>
                <Id><xsl:value-of select="data[@name='TargetUserName']/@value"/></Id>
              </User>
              <Outcome>
                <Success>true</Success>
              </Outcome>
              <Data Name="LogonType" Value="{data[@name='LogonType']/@value}"/>
            </Authenticate>
          </xsl:when>
          <xsl:when test="$eventId = '1002'">
            <TypeId>1002</TypeId>
            <Description>An account failed to log on</Description>
            <Authenticate>
              <Action>Logon</Action>
              <User>
                <Id><xsl:value-of select="data[@name='TargetUserName']/@value"/></Id>
              </User>
              <Outcome>
                <Success>false</Success>
                <Reason>IncorrectUsernameOrPassword</Reason>
              </Outcome>
              <Data Name="FailureReason" Value="{data[@name='FailureReason']/@value}"/>
            </Authenticate>
          </xsl:when>
          <xsl:when test="$eventId = '1003'">
            <TypeId>1003</TypeId>
            <Description>An account logged off</Description>
            <Authenticate>
              <Action>Logoff</Action>
              <User>
                <Id><xsl:value-of select="data[@name='TargetUserName']/@value"/></Id>
              </User>
            </Authenticate>
          </xsl:when>
          <xsl:when test="$eventId = '2001'">
            <TypeId>2001</TypeId>
            <Description>An object was accessed</Description>
            <View>
              <File>
                <Path><xsl:value-of select="data[@name='ObjectName']/@value"/></Path>
              </File>
              <Data Name="AccessMask" Value="{data[@name='AccessMask']/@value}"/>
            </View>
          </xsl:when>
          <xsl:otherwise>
            <TypeId><xsl:value-of select="$eventId"/></TypeId>
            <Unknown>
              <Data Name="Message" Value="{data[@name='Message']/@value}"/>
            </Unknown>
          </xsl:otherwise>
        </xsl:choose>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
