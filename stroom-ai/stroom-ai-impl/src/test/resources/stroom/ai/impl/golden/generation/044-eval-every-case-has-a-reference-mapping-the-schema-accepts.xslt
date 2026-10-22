<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_badge"/>
  </xsl:template>
  <!-- Rule 'badge': every record. Writes an Authenticate event (TypeId Badge-Access) from door, badge_user, result. -->
  <xsl:template match="node()" mode="event_type_badge">
    <Event>
      <xsl:if test="normalize-space(data[@name='time']/@value)">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="badge_user" select="data[@name='badge_user']/@value[normalize-space(.)]"/>
      <xsl:variable name="door" select="data[@name='door']/@value[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>Door Control</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>badge-controller</Generator>
        <xsl:if test="$door">
          <Device>
            <Name>
              <xsl:value-of select="$door"/>
            </Name>
          </Device>
        </xsl:if>
        <xsl:variable name="user_directory_2" select="(stroom:lookup('USER_DIRECTORY', string((lower-case(data[@name='badge_user']/@value))[1]))//*:department)[normalize-space(.)]"/>
        <xsl:variable name="user_directory" select="(stroom:lookup('USER_DIRECTORY', string((lower-case(data[@name='badge_user']/@value))[1]))//*:name)[normalize-space(.)]"/>
        <xsl:if test="$badge_user or exists($user_directory) or exists($user_directory_2)">
          <User>
            <xsl:if test="$badge_user">
              <Id>
                <xsl:value-of select="lower-case($badge_user[1])"/>
              </Id>
            </xsl:if>
            <xsl:if test="exists($user_directory)">
              <Name>
                <xsl:value-of select="$user_directory"/>
              </Name>
            </xsl:if>
            <xsl:if test="exists($user_directory_2)">
              <UserDetails>
                <Organisation>
                  <xsl:value-of select="$user_directory_2"/>
                </Organisation>
              </UserDetails>
            </xsl:if>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>Badge-Access</TypeId>
        <xsl:if test="$door">
          <Description>
            <xsl:value-of select="$door"/>
          </Description>
        </xsl:if>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="$badge_user">
            <User>
              <Id>
                <xsl:value-of select="lower-case($badge_user[1])"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="data[@name='result']/@value = ('granted', 'denied')">
            <Outcome>
              <Success>
                <xsl:value-of select="if (data[@name='result']/@value = 'granted') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
