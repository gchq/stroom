<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:apply-templates select="." mode="event_type_badge"/>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('badge_user', 'door', 'result', 'time'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'badge': every record. Writes an Authenticate event (TypeId Badge-Access) from door, badge_user, result. -->
  <xsl:template match="node()" mode="event_type_badge">
    <Event>
      <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date((data[@name='time']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
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
              <xsl:value-of select="$door[1]"/>
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
            <xsl:value-of select="$door[1]"/>
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
          <xsl:if test="(data[@name='result']/@value)[1] = ('granted', 'denied')">
            <Outcome>
              <Success>
                <xsl:value-of select="if ((data[@name='result']/@value)[1] = 'granted') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
