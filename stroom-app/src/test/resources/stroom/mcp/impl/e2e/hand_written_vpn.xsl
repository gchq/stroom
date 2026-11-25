<?xml version="1.1" encoding="UTF-8"?>
<!-- A hand-written translation the tools never built: one template per event, chosen by match patterns on the
     record. -->
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xpath-default-namespace="http://www.w3.org/2013/XSL/json" version="3.0">

  <xsl:template match="/">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd" Version="4.1.0">
      <xsl:apply-templates select="array/map"/>
    </Events>
  </xsl:template>

  <!-- A successful VPN login. -->
  <xsl:template match="map[string[@key='action'] = 'login' and string[@key='result'] = 'success']">
    <Event>
      <EventTime>
        <TimeCreated><xsl:value-of select="string[@key='ts']"/></TimeCreated>
      </EventTime>
      <EventSource>
        <System>
          <Name>Example VPN</Name>
          <Environment>Production</Environment>
        </System>
        <Generator>vpnd</Generator>
        <Device>
          <HostName><xsl:value-of select="string[@key='host']"/></HostName>
        </Device>
        <User>
          <Id><xsl:value-of select="string[@key='user']"/></Id>
        </User>
      </EventSource>
      <EventDetail>
        <TypeId>vpn-login</TypeId>
        <Description>A user logged in to the VPN</Description>
        <Authenticate>
          <Action>Logon</Action>
          <User>
            <Id><xsl:value-of select="string[@key='user']"/></Id>
          </User>
          <Outcome>
            <Success>true</Success>
          </Outcome>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>

  <!-- A failed VPN login. -->
  <xsl:template match="map[string[@key='action'] = 'login' and string[@key='result'] = 'failure']">
    <Event>
      <EventTime>
        <TimeCreated><xsl:value-of select="string[@key='ts']"/></TimeCreated>
      </EventTime>
      <EventSource>
        <System>
          <Name>Example VPN</Name>
          <Environment>Production</Environment>
        </System>
        <Generator>vpnd</Generator>
        <Device>
          <HostName><xsl:value-of select="string[@key='host']"/></HostName>
        </Device>
        <User>
          <Id><xsl:value-of select="string[@key='user']"/></Id>
        </User>
      </EventSource>
      <EventDetail>
        <TypeId>vpn-login-failed</TypeId>
        <Description>A user failed to log in to the VPN</Description>
        <Authenticate>
          <Action>Logon</Action>
          <User>
            <Id><xsl:value-of select="string[@key='user']"/></Id>
          </User>
          <Outcome>
            <Success>false</Success>
          </Outcome>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>

  <!-- A VPN logout. -->
  <xsl:template match="map[string[@key='action'] = 'logout']">
    <Event>
      <EventTime>
        <TimeCreated><xsl:value-of select="string[@key='ts']"/></TimeCreated>
      </EventTime>
      <EventSource>
        <System>
          <Name>Example VPN</Name>
          <Environment>Production</Environment>
        </System>
        <Generator>vpnd</Generator>
        <Device>
          <HostName><xsl:value-of select="string[@key='host']"/></HostName>
        </Device>
        <User>
          <Id><xsl:value-of select="string[@key='user']"/></Id>
        </User>
      </EventSource>
      <EventDetail>
        <TypeId>vpn-logout</TypeId>
        <Description>A user logged out of the VPN</Description>
        <Authenticate>
          <Action>Logoff</Action>
          <User>
            <Id><xsl:value-of select="string[@key='user']"/></Id>
          </User>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>

  <!-- A configuration change. -->
  <xsl:template match="map[string[@key='action'] = 'config_change']">
    <Event>
      <EventTime>
        <TimeCreated><xsl:value-of select="string[@key='ts']"/></TimeCreated>
      </EventTime>
      <EventSource>
        <System>
          <Name>Example VPN</Name>
          <Environment>Production</Environment>
        </System>
        <Generator>vpnd</Generator>
        <Device>
          <HostName><xsl:value-of select="string[@key='host']"/></HostName>
        </Device>
        <User>
          <Id><xsl:value-of select="string[@key='user']"/></Id>
        </User>
      </EventSource>
      <EventDetail>
        <TypeId>vpn-config-change</TypeId>
        <Description>A VPN setting was changed</Description>
        <Update>
          <After>
            <Configuration>
              <Id><xsl:value-of select="string[@key='setting']"/></Id>
              <Data Name="value" Value="{string[@key='new_value']}"/>
            </Configuration>
          </After>
        </Update>
      </EventDetail>
    </Event>
  </xsl:template>

  <!-- Anything else is left untranslated. -->
  <xsl:template match="map"/>
</xsl:stylesheet>
