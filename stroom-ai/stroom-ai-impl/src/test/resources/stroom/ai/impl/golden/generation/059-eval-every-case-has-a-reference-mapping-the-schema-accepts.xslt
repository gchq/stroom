<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="type" select="*[@key='type'][normalize-space(.)]"/>
    <xsl:variable name="event_type" select="*[@key='eventType'][normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$event_type = 'user.session.start'">
        <xsl:apply-templates select="." mode="event_type_new_session_start"/>
      </xsl:when>
      <xsl:when test="$event_type = 'user.session.end'">
        <xsl:apply-templates select="." mode="event_type_new_session_end"/>
      </xsl:when>
      <xsl:when test="$event_type = 'user.account.lock'">
        <xsl:apply-templates select="." mode="event_type_new_account_lock"/>
      </xsl:when>
      <xsl:when test="$type = 'login'">
        <xsl:apply-templates select="." mode="event_type_legacy_login"/>
      </xsl:when>
      <xsl:when test="$type = 'logout'">
        <xsl:apply-templates select="." mode="event_type_legacy_logout"/>
      </xsl:when>
      <xsl:when test="$type = 'password_change'">
        <xsl:apply-templates select="." mode="event_type_legacy_password_change"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('eventType=', string(($event_type)[1])), concat('type=', string(($type)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'new session start': records where eventType = user.session.start. Writes an Authenticate event (TypeId
       from eventType) from published, actor.alternateId, client.ipAddress, eventType, outcome.result,
       outcome.reason. -->
  <xsl:template match="node()" mode="event_type_new_session_start">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:variable name="outcome_reason" select="*[@key='outcome']/*[@key='reason'][normalize-space(.)]"/>
          <xsl:variable name="outcome_result" select="*[@key='outcome']/*[@key='result'][normalize-space(.)]"/>
          <xsl:if test="$outcome_result = ('SUCCESS', 'FAILURE') or $outcome_reason">
            <Outcome>
              <xsl:if test="$outcome_result = ('SUCCESS', 'FAILURE')">
                <Success>
                  <xsl:value-of select="if ($outcome_result = 'SUCCESS') then 'true' else 'false'"/>
                </Success>
              </xsl:if>
              <xsl:if test="$outcome_reason">
                <Description>
                  <xsl:value-of select="$outcome_reason"/>
                </Description>
              </xsl:if>
            </Outcome>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'new session end': records where eventType = user.session.end. Writes an Authenticate event (TypeId from
       eventType) from published, actor.alternateId, client.ipAddress, eventType. -->
  <xsl:template match="node()" mode="event_type_new_session_end">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'new account lock': records where eventType = user.account.lock. Writes an Authenticate event (TypeId from
       eventType) from published, actor.alternateId, client.ipAddress, eventType. -->
  <xsl:template match="node()" mode="event_type_new_account_lock">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>AccountLock</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'legacy login': records where type = login. Writes an Authenticate event (TypeId from type) from ts, user,
       ip, type, ok. -->
  <xsl:template match="node()" mode="event_type_legacy_login">
    <Event>
      <xsl:apply-templates select="." mode="event_time_2"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="event_detail_type_id"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:apply-templates select="." mode="outcome"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'legacy logout': records where type = logout. Writes an Authenticate event (TypeId from type) from ts,
       user, ip, type. -->
  <xsl:template match="node()" mode="event_type_legacy_logout">
    <Event>
      <xsl:apply-templates select="." mode="event_time_2"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="event_detail_type_id"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'legacy password change': records where type = password_change. Writes an Authenticate event (TypeId from
       type) from ts, user, ip, type, ok. -->
  <xsl:template match="node()" mode="event_type_legacy_password_change">
    <Event>
      <xsl:apply-templates select="." mode="event_time_2"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="event_detail_type_id"/>
        <Authenticate>
          <Action>ChangePassword</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:apply-templates select="." mode="outcome"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'new session start', 'new session end', 'new account lock' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(*[@key='published'])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(*[@key='published'][1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
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
      <xsl:if test="normalize-space(*[@key='client']/*[@key='ipAddress'])">
        <Client>
          <IPAddress>
            <xsl:value-of select="*[@key='client']/*[@key='ipAddress']"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="normalize-space(*[@key='actor']/*[@key='alternateId'])">
        <User>
          <Id>
            <xsl:value-of select="*[@key='actor']/*[@key='alternateId']"/>
          </Id>
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
  <!-- type_id: shared by the rules 'new session start', 'new session end', 'new account lock' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(*[@key='eventType'])">
      <TypeId>
        <xsl:value-of select="*[@key='eventType']"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'new session start', 'new session end', 'new account lock' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="normalize-space(*[@key='actor']/*[@key='alternateId'])">
      <User>
        <Id>
          <xsl:value-of select="*[@key='actor']/*[@key='alternateId']"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- event_time_2: shared by the rules 'legacy login', 'legacy logout', 'legacy password change' -->
  <xsl:template match="node()" mode="event_time_2">
    <xsl:if test="normalize-space(*[@key='ts'])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(*[@key='ts'][1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
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
      <xsl:if test="normalize-space(*[@key='ip'])">
        <Client>
          <IPAddress>
            <xsl:value-of select="*[@key='ip']"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="normalize-space(*[@key='user'])">
        <User>
          <Id>
            <xsl:value-of select="*[@key='user']"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- event_detail_type_id: shared by the rules 'legacy login', 'legacy logout', 'legacy password change' -->
  <xsl:template match="node()" mode="event_detail_type_id">
    <xsl:if test="normalize-space(*[@key='type'])">
      <TypeId>
        <xsl:value-of select="*[@key='type']"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- authenticate_user: shared by the rules 'legacy login', 'legacy logout', 'legacy password change' -->
  <xsl:template match="node()" mode="authenticate_user">
    <xsl:if test="normalize-space(*[@key='user'])">
      <User>
        <Id>
          <xsl:value-of select="*[@key='user']"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- outcome: shared by the rules 'legacy login', 'legacy password change' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:if test="normalize-space(*[@key='ok'])">
      <Outcome>
        <Success>
          <xsl:value-of select="*[@key='ok']"/>
        </Success>
      </Outcome>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
