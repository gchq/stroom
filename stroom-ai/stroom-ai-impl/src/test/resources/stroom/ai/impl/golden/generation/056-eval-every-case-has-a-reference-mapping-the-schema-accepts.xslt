<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="message" select="data[@name='message']/@value[normalize-space(.)]"/>
    <xsl:variable name="user_failed_parts" select="analyze-string(string($message[1]), '^Login failed for user ''([^'']+)''')"/>
    <xsl:variable name="user_ok_parts" select="analyze-string(string($message[1]), '^User ''([^'']+)'' logged (in|out)')"/>
    <xsl:variable name="direction" select="$user_ok_parts/fn:match//fn:group[@nr=2][normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$direction = 'in'">
        <xsl:apply-templates select="." mode="event_type_logged_in"/>
      </xsl:when>
      <xsl:when test="normalize-space($user_failed_parts/fn:match//fn:group[@nr=1])">
        <xsl:apply-templates select="." mode="event_type_failed_login"/>
      </xsl:when>
      <xsl:when test="$direction = 'out'">
        <xsl:apply-templates select="." mode="event_type_logged_out"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('direction=', string(($direction)[1])), concat('user_failed=', string((normalize-space($user_failed_parts/fn:match//fn:group[@nr=1]))[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logged in': records where direction = in. Writes an Authenticate event (TypeId login) from user_ok,
       session (extracted from message). -->
  <xsl:template match="node()" mode="event_type_logged_in">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>login</TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:variable name="session_parts" select="analyze-string(string(data[@name='message']/@value[1]), ' session (\S+)$')"/>
          <xsl:sequence select="mcp:data('session', $session_parts/fn:match//fn:group[@nr=1])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'failed login': records where user_failed present. Writes an Authenticate event (TypeId login-failed) from
       user_failed, reason (extracted from message). -->
  <xsl:template match="node()" mode="event_type_failed_login">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>login-failed</TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:variable name="message" select="data[@name='message']/@value[normalize-space(.)]"/>
          <xsl:variable name="user_failed_parts" select="analyze-string(string($message[1]), '^Login failed for user ''([^'']+)''')"/>
          <xsl:if test="normalize-space($user_failed_parts/fn:match//fn:group[@nr=1])">
            <User>
              <Id>
                <xsl:value-of select="$user_failed_parts/fn:match//fn:group[@nr=1]"/>
              </Id>
            </User>
          </xsl:if>
          <Outcome>
            <Success>false</Success>
            <xsl:variable name="reason_parts" select="analyze-string(string($message[1]), ' reason: (.+)$')"/>
            <xsl:if test="normalize-space($reason_parts/fn:match//fn:group[@nr=1])">
              <Description>
                <xsl:value-of select="$reason_parts/fn:match//fn:group[@nr=1]"/>
              </Description>
            </xsl:if>
          </Outcome>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logged out': records where direction = out. Writes an Authenticate event (TypeId logout) from user_ok,
       session (extracted from message). -->
  <xsl:template match="node()" mode="event_type_logged_out">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>logout</TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:variable name="session_parts" select="analyze-string(string(data[@name='message']/@value[1]), ' session (\S+)$')"/>
          <xsl:sequence select="mcp:data('session', $session_parts/fn:match//fn:group[@nr=1])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logged in', 'failed login', 'logged out' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='time']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd HH:mm:ss,SSS', 'UTC')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logged in', 'failed login', 'logged out' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Auth Service</Name>
        <Environment>Eval</Environment>
      </System>
      <xsl:if test="normalize-space(data[@name='component']/@value)">
        <Generator>
          <xsl:value-of select="data[@name='component']/@value"/>
        </Generator>
      </xsl:if>
      <Device>
        <HostName>auth01.example.org</HostName>
      </Device>
      <xsl:variable name="message" select="data[@name='message']/@value[normalize-space(.)]"/>
      <xsl:variable name="client_parts" select="analyze-string(string($message[1]), ' from (\S+)')"/>
      <xsl:if test="normalize-space($client_parts/fn:match//fn:group[@nr=1])">
        <Client>
          <IPAddress>
            <xsl:value-of select="$client_parts/fn:match//fn:group[@nr=1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:variable name="user_failed_parts" select="analyze-string(string($message[1]), '^Login failed for user ''([^'']+)''')"/>
      <xsl:variable name="user_ok_parts" select="analyze-string(string($message[1]), '^User ''([^'']+)'' logged (in|out)')"/>
      <xsl:if test="exists((($user_ok_parts/fn:match//fn:group[@nr=1], $user_failed_parts/fn:match//fn:group[@nr=1])[normalize-space(.)][1])[normalize-space(.)])">
        <User>
          <Id>
            <xsl:value-of select="(($user_ok_parts/fn:match//fn:group[@nr=1], $user_failed_parts/fn:match//fn:group[@nr=1])[normalize-space(.)][1])"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- description: shared by the rules 'logged in', 'failed login', 'logged out' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="normalize-space(data[@name='message']/@value)">
      <Description>
        <xsl:value-of select="data[@name='message']/@value"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logged in', 'logged out' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="user_ok_parts" select="analyze-string(string(data[@name='message']/@value[1]), '^User ''([^'']+)'' logged (in|out)')"/>
    <xsl:if test="normalize-space($user_ok_parts/fn:match//fn:group[@nr=1])">
      <User>
        <Id>
          <xsl:value-of select="$user_ok_parts/fn:match//fn:group[@nr=1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
