<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <!-- Fields of the record no rule reads are logged (mcp_unread_fields) -->
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:variable name="type" select="*[@key='type'][normalize-space(.)]"/>
    <xsl:variable name="event_type" select="*[@key='eventType'][normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$event_type/(if (self::*:array) then * else .)[normalize-space(.)] = 'user.session.start'">
        <xsl:apply-templates select="." mode="event_type_new_session_start"/>
      </xsl:when>
      <xsl:when test="$event_type/(if (self::*:array) then * else .)[normalize-space(.)] = 'user.session.end'">
        <xsl:apply-templates select="." mode="event_type_new_session_end"/>
      </xsl:when>
      <xsl:when test="$event_type/(if (self::*:array) then * else .)[normalize-space(.)] = 'user.account.lock'">
        <xsl:apply-templates select="." mode="event_type_new_account_lock"/>
      </xsl:when>
      <xsl:when test="$type/(if (self::*:array) then * else .)[normalize-space(.)] = 'login'">
        <xsl:apply-templates select="." mode="event_type_legacy_login"/>
      </xsl:when>
      <xsl:when test="$type/(if (self::*:array) then * else .)[normalize-space(.)] = 'logout'">
        <xsl:apply-templates select="." mode="event_type_legacy_logout"/>
      </xsl:when>
      <xsl:when test="$type/(if (self::*:array) then * else .)[normalize-space(.)] = 'password_change'">
        <xsl:apply-templates select="." mode="event_type_legacy_password_change"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('eventType=', string(($event_type)[1])), concat('type=', string(($type)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="*[@key][not(@key = ('actor', 'alternateId', 'client', 'eventType', 'ip', 'ipAddress', 'ok', 'outcome', 'published', 'reason', 'result', 'ts', 'type', 'user'))][normalize-space(string(.))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'new session start': records where eventType = user.session.start. Writes an Authenticate event (TypeId
       Authenticate-Logon) from eventType, published, actor.alternateId, client.ipAddress, outcome.result,
       outcome.reason. -->
  <xsl:template match="node()" mode="event_type_new_session_start">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Authenticate-Logon</TypeId>
        <Description>A user signed in, or tried to</Description>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:variable name="outcome_reason" select="*[@key='outcome']/*[@key='reason'][normalize-space(.)]"/>
          <xsl:variable name="outcome_result" select="*[@key='outcome']/*[@key='result'][normalize-space(.)]"/>
          <xsl:if test="$outcome_result[1] = ('SUCCESS', 'FAILURE') or $outcome_reason">
            <Outcome>
              <xsl:if test="$outcome_result[1] = ('SUCCESS', 'FAILURE')">
                <Success>
                  <xsl:value-of select="if ($outcome_result[1] = 'SUCCESS') then 'true' else 'false'"/>
                </Success>
              </xsl:if>
              <xsl:if test="$outcome_reason">
                <Description>
                  <xsl:value-of select="$outcome_reason[1]"/>
                </Description>
              </xsl:if>
            </Outcome>
          </xsl:if>
          <xsl:sequence select="mcp:data('eventType', *[@key='eventType'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'new session end': records where eventType = user.session.end. Writes an Authenticate event (TypeId
       Authenticate-Logoff) from eventType, published, actor.alternateId, client.ipAddress. -->
  <xsl:template match="node()" mode="event_type_new_session_end">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Authenticate-Logoff</TypeId>
        <Description>A user signed out</Description>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:sequence select="mcp:data('eventType', *[@key='eventType'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'new account lock': records where eventType = user.account.lock. Writes an Authenticate event (TypeId
       Lock-UserAccount) from eventType, published, actor.alternateId, client.ipAddress. -->
  <xsl:template match="node()" mode="event_type_new_account_lock">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Lock-UserAccount</TypeId>
        <Description>An account was locked</Description>
        <Authenticate>
          <Action>AccountLock</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:sequence select="mcp:data('eventType', *[@key='eventType'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'legacy login': records where type = login. Writes an Authenticate event (TypeId Authenticate-Logon) from
       type, ts, user, ip, ok. -->
  <xsl:template match="node()" mode="event_type_legacy_login">
    <Event>
      <xsl:apply-templates select="." mode="event_time_2"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <TypeId>Authenticate-Logon</TypeId>
        <Description>A user signed in, or tried to</Description>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:apply-templates select="." mode="outcome"/>
          <xsl:sequence select="mcp:data('type', *[@key='type'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'legacy logout': records where type = logout. Writes an Authenticate event (TypeId Authenticate-Logoff)
       from type, ts, user, ip. -->
  <xsl:template match="node()" mode="event_type_legacy_logout">
    <Event>
      <xsl:apply-templates select="." mode="event_time_2"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <TypeId>Authenticate-Logoff</TypeId>
        <Description>A user signed out</Description>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:sequence select="mcp:data('type', *[@key='type'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'legacy password change': records where type = password_change. Writes an Authenticate event (TypeId
       Authenticate-PasswordChange) from type, ts, user, ip, ok. -->
  <xsl:template match="node()" mode="event_type_legacy_password_change">
    <Event>
      <xsl:apply-templates select="." mode="event_time_2"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <TypeId>Authenticate-PasswordChange</TypeId>
        <Description>A user changed their password</Description>
        <Authenticate>
          <Action>ChangePassword</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:apply-templates select="." mode="outcome"/>
          <xsl:sequence select="mcp:data('type', *[@key='type'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'new session start', 'new session end', 'new account lock' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="*[@key='published'][normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((*[@key='published'])[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'new session start', 'new session end', 'new account lock' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <xsl:apply-templates select="." mode="system"/>
      <Generator>idp</Generator>
      <xsl:apply-templates select="." mode="device"/>
      <xsl:if test="*[@key='client']/*[@key='ipAddress'][normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="(*[@key='client']/*[@key='ipAddress'])[1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:variable name="actor_alternate_id" select="*[@key='actor']/*[@key='alternateId'][normalize-space(.)]"/>
      <xsl:if test="$actor_alternate_id">
        <User>
          <Id>
            <xsl:value-of select="mcp:strip_domain($actor_alternate_id[1])"/>
          </Id>
          <Domain>
            <xsl:value-of select="mcp:domain($actor_alternate_id[1])"/>
          </Domain>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- system: shared by the rules 'new session start', 'new session end', 'new account lock', 'legacy login', 'legacy
       logout', 'legacy password change' -->
  <xsl:template match="node()" mode="system">
    <System>
      <Name>Identity Provider</Name>
      <Environment>Eval</Environment>
    </System>
  </xsl:template>
  <!-- device: shared by the rules 'new session start', 'new session end', 'new account lock', 'legacy login', 'legacy
       logout', 'legacy password change' -->
  <xsl:template match="node()" mode="device">
    <Device>
      <HostName>idp.example.org</HostName>
    </Device>
  </xsl:template>
  <!-- user: shared by the rules 'new session start', 'new session end', 'new account lock' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="actor_alternate_id" select="*[@key='actor']/*[@key='alternateId'][normalize-space(.)]"/>
    <xsl:if test="$actor_alternate_id">
      <User>
        <Id>
          <xsl:value-of select="mcp:strip_domain($actor_alternate_id[1])"/>
        </Id>
        <Domain>
          <xsl:value-of select="mcp:domain($actor_alternate_id[1])"/>
        </Domain>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- event_time_2: shared by the rules 'legacy login', 'legacy logout', 'legacy password change' -->
  <xsl:template match="node()" mode="event_time_2">
    <xsl:if test="*[@key='ts'][normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((*[@key='ts'])[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source_2: shared by the rules 'legacy login', 'legacy logout', 'legacy password change' -->
  <xsl:template match="node()" mode="event_source_2">
    <EventSource>
      <xsl:apply-templates select="." mode="system"/>
      <Generator>idp</Generator>
      <xsl:apply-templates select="." mode="device"/>
      <xsl:if test="*[@key='ip'][normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="(*[@key='ip'])[1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="*[@key='user'][normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(*[@key='user'])[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- authenticate_user: shared by the rules 'legacy login', 'legacy logout', 'legacy password change' -->
  <xsl:template match="node()" mode="authenticate_user">
    <xsl:if test="*[@key='user'][normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="(*[@key='user'])[1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- outcome: shared by the rules 'legacy login', 'legacy password change' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:if test="*[@key='ok'][normalize-space(.)]">
      <Outcome>
        <Success>
          <xsl:value-of select="(*[@key='ok'])[1]"/>
        </Success>
      </Outcome>
    </xsl:if>
  </xsl:template>
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value[1]}"/>
    </xsl:if>
  </xsl:function>
  <xsl:function name="mcp:domain" as="xs:string?">
    <xsl:param name="value" as="item()?"/>
    <xsl:sequence select="replace($value, '^(?:([^\\]*)\\.*|[^@]*@(.*))$', '$1$2')"/>
  </xsl:function>
  <xsl:function name="mcp:strip_domain" as="xs:string?">
    <xsl:param name="value" as="item()?"/>
    <xsl:sequence select="replace(replace($value, '^[^\\]*\\', ''), '@.*$', '')"/>
  </xsl:function>
</xsl:stylesheet>
