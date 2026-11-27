<?xml version="1.1" encoding="UTF-8"?>
<!-- A hand-written translation of plain XML: templates with priorities, and an empty template dropping heartbeats. -->
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" version="2.0">

  <xsl:template match="/audit">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd" Version="4.1.0">
      <xsl:apply-templates select="entry"/>
    </Events>
  </xsl:template>

  <!-- Heartbeats are not events. -->
  <xsl:template match="entry[@type = 'heartbeat']"/>

  <!-- Anything of high severity is an alert, whatever its type: chosen first by its priority. -->
  <xsl:template match="entry[@severity = 'high']" priority="2">
    <Event>
      <EventTime>
        <TimeCreated><xsl:value-of select="@at"/></TimeCreated>
      </EventTime>
      <EventSource>
        <System>
          <Name>Example Kiosk</Name>
          <Environment>Production</Environment>
        </System>
        <Generator>kioskd</Generator>
        <Device>
          <HostName><xsl:value-of select="@station"/></HostName>
        </Device>
      </EventSource>
      <EventDetail>
        <TypeId>kiosk-alert</TypeId>
        <Alert>
          <Type>Other</Type>
          <Description><xsl:value-of select="concat(@type, ' by ', @user)"/></Description>
        </Alert>
      </EventDetail>
    </Event>
  </xsl:template>

  <xsl:template match="entry[@type = 'login']">
    <Event>
      <EventTime>
        <TimeCreated><xsl:value-of select="@at"/></TimeCreated>
      </EventTime>
      <EventSource>
        <System>
          <Name>Example Kiosk</Name>
          <Environment>Production</Environment>
        </System>
        <Generator>kioskd</Generator>
        <Device>
          <HostName><xsl:value-of select="@station"/></HostName>
        </Device>
        <User>
          <Id><xsl:value-of select="@user"/></Id>
        </User>
      </EventSource>
      <EventDetail>
        <TypeId>kiosk-login</TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <User>
            <Id><xsl:value-of select="@user"/></Id>
          </User>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>

  <!-- Every other entry, kept as Unknown. -->
  <xsl:template match="entry">
    <Event>
      <EventTime>
        <TimeCreated><xsl:value-of select="@at"/></TimeCreated>
      </EventTime>
      <EventSource>
        <System>
          <Name>Example Kiosk</Name>
          <Environment>Production</Environment>
        </System>
        <Generator>kioskd</Generator>
        <Device>
          <HostName><xsl:value-of select="@station"/></HostName>
        </Device>
      </EventSource>
      <EventDetail>
        <TypeId><xsl:value-of select="@type"/></TypeId>
        <Unknown>
          <Data Name="user" Value="{@user}"/>
        </Unknown>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
