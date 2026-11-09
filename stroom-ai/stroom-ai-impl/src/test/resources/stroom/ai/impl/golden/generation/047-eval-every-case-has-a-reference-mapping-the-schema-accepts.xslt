<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:function name="mcp:json-to-xml" as="node()?">
    <xsl:param name="text" as="item()*"/>
    <xsl:variable name="json" select="string(($text)[1])"/>
    <xsl:if test="normalize-space($json)">
      <xsl:try>
        <xsl:variable name="parsed" select="json-to-xml($json)"/>
        <xsl:sequence select="if ($parsed/*[local-name() = 'string'] and matches($parsed/*, '^\s*[\[{]')) then mcp:json-to-xml(string($parsed/*)) else $parsed"/>
        <xsl:catch/>
      </xsl:try>
    </xsl:if>
  </xsl:function>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/array/map | /map/array/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <!-- Fields of the record no rule reads are logged (mcp_unread_fields) -->
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:variable name="outcome_success" select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type'])[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$outcome_success = ('LOGIN_SUCCESS', 'LOGIN_FAILURE')">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$outcome_success = 'LOGOUT'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:when test="$outcome_success = 'PASSWORD_CHANGE'">
        <xsl:apply-templates select="." mode="event_type_password_change"/>
      </xsl:when>
      <xsl:when test="$outcome_success = 'DOCUMENT_VIEW'">
        <xsl:apply-templates select="." mode="event_type_view"/>
      </xsl:when>
      <xsl:when test="$outcome_success = 'DOCUMENT_DELETE'">
        <xsl:apply-templates select="." mode="event_type_delete"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="*[@key][not(@key = ('event', 'event_type', 'host', 'message', 'resource', 'timestamp', 'username'))][normalize-space(string(.))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'logon': records where json-to-xml(*[@key='event'])/*/*[@key='event_type'] in LOGIN_SUCCESS,
       LOGIN_FAILURE. Writes an Authenticate event. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:variable name="outcome_success" select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type'])[normalize-space(.)]"/>
        <TypeId>
          <xsl:if test="count($outcome_success) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($outcome_success), ') for ', 'EventDetail/TypeId, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="if (exists($outcome_success)) then $outcome_success[1] else 'logon'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:if test="$outcome_success[1] = ('LOGIN_SUCCESS', 'LOGIN_FAILURE')">
            <Outcome>
              <Success>
                <xsl:if test="count($outcome_success) gt 1">
                  <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($outcome_success), ') for ', 'EventDetail/Authenticate/Outcome/Success, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
                </xsl:if>
                <xsl:value-of select="if ($outcome_success[1] = 'LOGIN_SUCCESS') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where json-to-xml(*[@key='event'])/*/*[@key='event_type'] = LOGOUT. Writes an
       Authenticate event. -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:variable name="outcome_success" select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type'])[normalize-space(.)]"/>
          <xsl:if test="count($outcome_success) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($outcome_success), ') for ', 'EventDetail/TypeId, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="if (exists($outcome_success)) then $outcome_success[1] else 'logoff'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'password change': records where json-to-xml(*[@key='event'])/*/*[@key='event_type'] = PASSWORD_CHANGE.
       Writes an Authenticate event. -->
  <xsl:template match="node()" mode="event_type_password_change">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:variable name="outcome_success" select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type'])[normalize-space(.)]"/>
          <xsl:if test="count($outcome_success) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($outcome_success), ') for ', 'EventDetail/TypeId, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="if (exists($outcome_success)) then $outcome_success[1] else 'password change'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>ChangePassword</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'view': records where json-to-xml(*[@key='event'])/*/*[@key='event_type'] = DOCUMENT_VIEW. Writes a View
       event. -->
  <xsl:template match="node()" mode="event_type_view">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:variable name="outcome_success" select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type'])[normalize-space(.)]"/>
          <xsl:if test="count($outcome_success) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($outcome_success), ') for ', 'EventDetail/TypeId, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="if (exists($outcome_success)) then $outcome_success[1] else 'view'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:variable name="document_name" select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='resource'])[normalize-space(.)]"/>
        <xsl:if test="exists($document_name)">
          <View>
            <Document>
              <Name>
                <xsl:if test="count($document_name) gt 1">
                  <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($document_name), ') for ', 'EventDetail/View/Document/Name, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
                </xsl:if>
                <xsl:value-of select="$document_name[1]"/>
              </Name>
            </Document>
          </View>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'delete': records where json-to-xml(*[@key='event'])/*/*[@key='event_type'] = DOCUMENT_DELETE. Writes a
       Delete event. -->
  <xsl:template match="node()" mode="event_type_delete">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:variable name="outcome_success" select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type'])[normalize-space(.)]"/>
          <xsl:if test="count($outcome_success) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($outcome_success), ') for ', 'EventDetail/TypeId, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="if (exists($outcome_success)) then $outcome_success[1] else 'delete'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:variable name="document_name" select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='resource'])[normalize-space(.)]"/>
        <xsl:if test="exists($document_name)">
          <Delete>
            <Document>
              <Name>
                <xsl:if test="count($document_name) gt 1">
                  <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($document_name), ') for ', 'EventDetail/Delete/Document/Name, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
                </xsl:if>
                <xsl:value-of select="$document_name[1]"/>
              </Name>
            </Document>
          </Delete>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff', 'password change', 'view', 'delete' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:variable name="event_time_time_created" select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='timestamp'])[normalize-space(.)]"/>
    <xsl:if test="exists($event_time_time_created)">
      <EventTime>
        <TimeCreated>
          <xsl:if test="count($event_time_time_created) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($event_time_time_created), ') for ', 'EventTime/TimeCreated, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="stroom:format-date($event_time_time_created[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'logoff', 'password change', 'view', 'delete' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Document Service</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>dms</Generator>
      <xsl:if test="*[@key='host'][normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(*[@key='host'])[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="user_id" select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='username'])[normalize-space(.)]"/>
      <xsl:if test="exists($user_id)">
        <User>
          <Id>
            <xsl:if test="count($user_id) gt 1">
              <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($user_id), ') for ', 'EventSource/User/Id, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
            </xsl:if>
            <xsl:value-of select="$user_id[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- description: shared by the rules 'logon', 'logoff', 'password change', 'view', 'delete' -->
  <xsl:template match="node()" mode="description">
    <xsl:variable name="event_detail_description" select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='message'])[normalize-space(.)]"/>
    <xsl:if test="exists($event_detail_description)">
      <Description>
        <xsl:if test="count($event_detail_description) gt 1">
          <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($event_detail_description), ') for ', 'EventDetail/Description, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
        </xsl:if>
        <xsl:value-of select="$event_detail_description[1]"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff', 'password change' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="user_id" select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='username'])[normalize-space(.)]"/>
    <xsl:if test="exists($user_id)">
      <User>
        <Id>
          <xsl:if test="count($user_id) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($user_id), ') for ', 'EventDetail/Authenticate/User/Id, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="$user_id[1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
