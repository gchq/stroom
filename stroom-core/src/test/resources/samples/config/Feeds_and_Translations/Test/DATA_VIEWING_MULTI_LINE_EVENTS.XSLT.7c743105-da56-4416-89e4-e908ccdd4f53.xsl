<?xml version="1.1" encoding="UTF-8" ?>

<!-- UK Crown Copyright © 2016 -->
<xsl:stylesheet xpath-default-namespace="records:2" xmlns="event-logging:3" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.0">
  <xsl:template match="records">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v3.4.2.xsd" Version="3.4.2">
      <xsl:apply-templates />
    </Events>
  </xsl:template>

  <!-- Example
  <Event>
  "uuid","firstName","surname","username","bloodGroup","emotionalState","address","company","companyLogo","lorem"
  "6cea0880-65a3-4914-814c-94451c3efc1e","Darrel","Bogisich","romaine.lindgren","A-","😐","Suite 937 42269 Keith Wells, West Sergio, PA JD3E 7LE","Pouros-Deckow","https://pigment.github.io/fake-logos/logos/medium/color/11.png","accusamus accusamus sint quibusdam"
  "353acb65-c662-4f89-a38e-710c463a642c","Al","Collins","leandro.windler","AB-","😻","236 Austin Islands, Braunbury, AL F5W 7NG","O'Reilly LLC","https://pigment.github.io/fake-logos/logos/medium/color/1.png","voluptates hic ad voluptates"
  </Event>
  -->
  <xsl:template match="record">
    <xsl:variable name="user" select="data[@name='username']/@value" />
    <Event>
      <xsl:call-template name="header" />
      <EventDetail>
        <TypeId>
          <xsl:value-of select="data[@name='uuid']/@value" />
        </TypeId>
        <Description>
          <xsl:value-of select="data[@name='lorum']/@value" />
        </Description>
        <Authenticate>
          <Action>Logon</Action>
          <LogonType>Interactive</LogonType>
          <User>
            <Id>
              <xsl:value-of select="$user" />
            </Id>
          </User>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <xsl:template name="header">
    <xsl:variable name="formattedDateTime" select="stroom:format-date(data[@name='dateTime']/@value, 'yyyy-MM-dd''T''HH:mm:ss.SSS')" />
    <xsl:variable name="user" select="data[@name='username']/@value" />
    <EventTime>
      <TimeCreated>
        <xsl:value-of select="$formattedDateTime" />
      </TimeCreated>
    </EventTime>
    <EventSource>
      <System>
        <Name>Data Viewer Test</Name>
        <Environment>Test</Environment>
      </System>
      <Generator>CSV</Generator>
      <Device>
        <IPAddress>
          <xsl:value-of select="data[@name='machineIp']/@value" />
        </IPAddress>
        <MACAddress>
          <xsl:value-of select="upper-case(data[@name='machineMacAddress']/@value)" />
        </MACAddress>
      </Device>
      <User>
        <Id>
          <xsl:value-of select="$user" />
        </Id>
        <UserDetails>
          <Surname>
            <xsl:value-of select="data[@name='surname']/@value" />
          </Surname>
          <GivenName>
            <xsl:value-of select="data[@name='firstName']/@value" />
          </GivenName>

          <EmploymentStatus>
            <xsl:value-of select="data[@name='emotionalState']/@value" />
          </EmploymentStatus>
          <Location>
            <xsl:value-of select="data[@name='address']/@value" />
          </Location>
                    <Organisation>
            <xsl:value-of select="data[@name='company']/@value" />
          </Organisation>
          <Data Name="BloodGroup">
            <xsl:attribute name="Value" select="data[@name='bloodGroup']/@value" />
          </Data>
          <Data Name="CompanyLogo">
            <xsl:attribute name="Value" select="data[@name='companyLogo']/@value" />
          </Data>
        </UserDetails>
      </User>
    </EventSource>
  </xsl:template>
</xsl:stylesheet>
