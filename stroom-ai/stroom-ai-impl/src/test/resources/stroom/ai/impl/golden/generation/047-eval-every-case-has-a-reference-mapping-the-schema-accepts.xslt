<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <xsl:function name="mcp:json-to-xml" as="node()?">
    <xsl:param name="text" as="item()*"/>
    <xsl:variable name="json" select="string(($text)[1])"/>
    <xsl:if test="normalize-space($json)">
      <xsl:try>
        <xsl:sequence select="json-to-xml($json)"/>
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
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no()))"/>
      </xsl:otherwise>
    </xsl:choose>
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
          <xsl:value-of select="if (exists($outcome_success)) then $outcome_success else 'logon'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:if test="$outcome_success = ('LOGIN_SUCCESS', 'LOGIN_FAILURE')">
            <Outcome>
              <Success>
                <xsl:value-of select="if ($outcome_success = 'LOGIN_SUCCESS') then 'true' else 'false'"/>
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
          <xsl:value-of select="if (exists((mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type'])[normalize-space(.)])) then (mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type']) else 'logoff'"/>
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
          <xsl:value-of select="if (exists((mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type'])[normalize-space(.)])) then (mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type']) else 'password change'"/>
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
          <xsl:value-of select="if (exists((mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type'])[normalize-space(.)])) then (mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type']) else 'view'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:if test="exists((mcp:json-to-xml(*[@key='event'])/*/*[@key='resource'])[normalize-space(.)])">
          <View>
            <xsl:apply-templates select="." mode="document"/>
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
          <xsl:value-of select="if (exists((mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type'])[normalize-space(.)])) then (mcp:json-to-xml(*[@key='event'])/*/*[@key='event_type']) else 'delete'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:if test="exists((mcp:json-to-xml(*[@key='event'])/*/*[@key='resource'])[normalize-space(.)])">
          <Delete>
            <xsl:apply-templates select="." mode="document"/>
          </Delete>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff', 'password change', 'view', 'delete' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="exists((mcp:json-to-xml(*[@key='event'])/*/*[@key='timestamp'])[normalize-space(.)])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((mcp:json-to-xml(*[@key='event'])/*/*[@key='timestamp'])[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')"/>
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
      <xsl:if test="normalize-space(*[@key='host'])">
        <Device>
          <HostName>
            <xsl:value-of select="*[@key='host']"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="exists((mcp:json-to-xml(*[@key='event'])/*/*[@key='username'])[normalize-space(.)])">
        <User>
          <Id>
            <xsl:value-of select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='username'])"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- description: shared by the rules 'logon', 'logoff', 'password change', 'view', 'delete' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="exists((mcp:json-to-xml(*[@key='event'])/*/*[@key='message'])[normalize-space(.)])">
      <Description>
        <xsl:value-of select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='message'])"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff', 'password change' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="exists((mcp:json-to-xml(*[@key='event'])/*/*[@key='username'])[normalize-space(.)])">
      <User>
        <Id>
          <xsl:value-of select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='username'])"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- document: shared by the rules 'view', 'delete' -->
  <xsl:template match="node()" mode="document">
    <xsl:if test="exists((mcp:json-to-xml(*[@key='event'])/*/*[@key='resource'])[normalize-space(.)])">
      <Document>
        <Name>
          <xsl:value-of select="(mcp:json-to-xml(*[@key='event'])/*/*[@key='resource'])"/>
        </Name>
      </Document>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
