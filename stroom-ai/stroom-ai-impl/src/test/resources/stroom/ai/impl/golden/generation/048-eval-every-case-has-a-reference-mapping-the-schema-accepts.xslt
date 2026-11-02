<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="kind_parts" select="analyze-string(string(*[@key='message'][1]), '^(\S+) (\S+)')"/>
    <xsl:variable name="kind" select="$kind_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$kind = 'AUTH'">
        <xsl:apply-templates select="." mode="event_type_auth"/>
      </xsl:when>
      <xsl:when test="$kind = 'SESSION'">
        <xsl:apply-templates select="." mode="event_type_session_end"/>
      </xsl:when>
      <xsl:when test="$kind = 'TUNNEL'">
        <xsl:apply-templates select="." mode="event_type_tunnel"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
        <xsl:sequence select="stroom:log('INFO', concat('Kept as Unknown by rule ''other'': record ', $record-no, ' (', string-join((concat('kind=', string(($kind)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'auth': records where kind = AUTH. Writes an Authenticate event (TypeId from kind) from user_quoted,
       user_plain, src, verb, reason, method (extracted from message). -->
  <xsl:template match="node()" mode="event_type_auth">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:variable name="message" select="*[@key='message'][normalize-space(.)]"/>
      <EventSource>
        <xsl:apply-templates select="." mode="system"/>
        <Generator>vpn</Generator>
        <xsl:apply-templates select="." mode="device"/>
        <xsl:if test="normalize-space(mcp:value($message, 'src'))">
          <Client>
            <IPAddress>
              <xsl:value-of select="mcp:value($message, 'src')"/>
            </IPAddress>
          </Client>
        </xsl:if>
        <xsl:apply-templates select="." mode="user"/>
      </EventSource>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:variable name="reason_parts" select="analyze-string(string($message[1]), ' reason=&quot;([^&quot;]*)&quot;')"/>
          <xsl:variable name="reason" select="$reason_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
          <xsl:variable name="kind_parts" select="analyze-string(string($message[1]), '^(\S+) (\S+)')"/>
          <xsl:variable name="verb" select="$kind_parts/fn:match//fn:group[@nr=2][normalize-space(.)]"/>
          <xsl:if test="$verb = ('ok', 'fail') or $reason">
            <Outcome>
              <xsl:if test="$verb = ('ok', 'fail')">
                <Success>
                  <xsl:value-of select="if ($verb = 'ok') then 'true' else 'false'"/>
                </Success>
              </xsl:if>
              <xsl:if test="$reason">
                <Description>
                  <xsl:value-of select="$reason"/>
                </Description>
              </xsl:if>
            </Outcome>
          </xsl:if>
          <xsl:sequence select="mcp:data('method', mcp:value($message, 'method'))"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'session end': records where kind = SESSION. Writes an Authenticate event (TypeId from kind) from
       user_quoted, user_plain, duration (extracted from message). -->
  <xsl:template match="node()" mode="event_type_session_end">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <EventSource>
        <xsl:apply-templates select="." mode="system"/>
        <Generator>vpn</Generator>
        <xsl:apply-templates select="." mode="device"/>
        <xsl:apply-templates select="." mode="user"/>
      </EventSource>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:variable name="duration_parts" select="analyze-string(string(*[@key='message'][1]), ' duration=(\d+)s')"/>
          <xsl:sequence select="mcp:data('duration_seconds', $duration_parts/fn:match//fn:group[@nr=1])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'tunnel': records where kind = TUNNEL. Writes an Alert event (TypeId from kind) from verb, host, peer,
       tunnel (verb, peer, tunnel extracted from message). -->
  <xsl:template match="node()" mode="event_type_tunnel">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Alert>
          <Type>Network</Type>
          <xsl:variable name="message" select="*[@key='message'][normalize-space(.)]"/>
          <xsl:variable name="kind_parts" select="analyze-string(string($message[1]), '^(\S+) (\S+)')"/>
          <xsl:if test="$kind_parts/fn:match//fn:group[@nr=2] = ('down', 'up')">
            <Severity>
              <xsl:value-of select="if ($kind_parts/fn:match//fn:group[@nr=2] = 'down') then 'Major' else 'Info'"/>
            </Severity>
          </xsl:if>
          <xsl:variable name="peer" select="mcp:value($message, 'peer')[normalize-space(.)]"/>
          <xsl:variable name="host" select="*[@key='host'][normalize-space(.)]"/>
          <xsl:if test="$host or $peer">
            <Network>
              <xsl:if test="$host">
                <Source>
                  <Device>
                    <HostName>
                      <xsl:value-of select="$host"/>
                    </HostName>
                  </Device>
                </Source>
              </xsl:if>
              <xsl:if test="$peer">
                <Destination>
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$peer"/>
                    </IPAddress>
                  </Device>
                </Destination>
              </xsl:if>
            </Network>
          </xsl:if>
          <xsl:variable name="tunnel_parts" select="analyze-string(string($message[1]), ' name=(\S+)')"/>
          <xsl:sequence select="mcp:data('tunnel', $tunnel_parts/fn:match//fn:group[@nr=1])"/>
        </Alert>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from kind) from severity. Unknown
       on purpose: free text with no recognised layout, kept with its text as the request asks. -->
  <xsl:template match="node()" mode="event_type_other">
    <!-- other: EventDetail/Unknown on purpose: free text with no recognised layout, kept with its text as the request asks -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:variable name="kind_parts" select="analyze-string(string(*[@key='message'][1]), '^(\S+) (\S+)')"/>
          <xsl:value-of select="if (normalize-space($kind_parts/fn:match//fn:group[@nr=1])) then $kind_parts/fn:match//fn:group[@nr=1] else 'other'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:if test="normalize-space(*[@key='severity'])">
          <Unknown>
            <Data Name="severity" Value="{*[@key='severity']}"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'auth', 'session end', 'tunnel', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(*[@key='time'])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(*[@key='time'][1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- system: shared by the rules 'auth', 'session end', 'tunnel', 'other' -->
  <xsl:template match="node()" mode="system">
    <System>
      <Name>Remote Access VPN</Name>
      <Environment>Eval</Environment>
    </System>
  </xsl:template>
  <!-- device: shared by the rules 'auth', 'session end', 'tunnel', 'other' -->
  <xsl:template match="node()" mode="device">
    <xsl:if test="normalize-space(*[@key='host'])">
      <Device>
        <HostName>
          <xsl:value-of select="*[@key='host']"/>
        </HostName>
      </Device>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'auth', 'session end' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="user_quoted_parts" select="analyze-string(string(*[@key='message'][1]), ' user=(?:&quot;([^&quot;]*)&quot;|(\S+))')"/>
    <xsl:if test="exists((($user_quoted_parts/fn:match//fn:group[@nr=1], $user_quoted_parts/fn:match//fn:group[@nr=2])[normalize-space(.)][1])[normalize-space(.)])">
      <User>
        <Id>
          <xsl:value-of select="(($user_quoted_parts/fn:match//fn:group[@nr=1], $user_quoted_parts/fn:match//fn:group[@nr=2])[normalize-space(.)][1])"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- type_id: shared by the rules 'auth', 'session end', 'tunnel' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:variable name="kind_parts" select="analyze-string(string(*[@key='message'][1]), '^(\S+) (\S+)')"/>
    <xsl:if test="normalize-space($kind_parts/fn:match//fn:group[@nr=1])">
      <TypeId>
        <xsl:value-of select="$kind_parts/fn:match//fn:group[@nr=1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'auth', 'session end', 'tunnel', 'other' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="normalize-space(*[@key='message'])">
      <Description>
        <xsl:value-of select="*[@key='message']"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- authenticate_user: shared by the rules 'auth', 'session end' -->
  <xsl:template match="node()" mode="authenticate_user">
    <xsl:variable name="user_quoted_parts" select="analyze-string(string(*[@key='message'][1]), ' user=(?:&quot;([^&quot;]*)&quot;|(\S+))')"/>
    <xsl:if test="exists((($user_quoted_parts/fn:match//fn:group[@nr=1], $user_quoted_parts/fn:match//fn:group[@nr=2])[normalize-space(.)][1])[normalize-space(.)])">
      <User>
        <Id>
          <xsl:value-of select="(($user_quoted_parts/fn:match//fn:group[@nr=1], $user_quoted_parts/fn:match//fn:group[@nr=2])[normalize-space(.)][1])"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'tunnel', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <xsl:apply-templates select="." mode="system"/>
      <Generator>vpn</Generator>
      <xsl:apply-templates select="." mode="device"/>
    </EventSource>
  </xsl:template>
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
  <xsl:function name="mcp:value" as="xs:string?">
    <!-- The value of a key in the text: the regex  <key>=(\S+), its first group -->
    <xsl:param name="text" as="item()*"/>
    <xsl:param name="key" as="xs:string"/>
    <xsl:sequence select="for $g in (analyze-string(string($text[1]), concat(' ', $key, '=(\S+)'))//fn:group[@nr=1])[1] return string($g)"/>
  </xsl:function>
</xsl:stylesheet>
