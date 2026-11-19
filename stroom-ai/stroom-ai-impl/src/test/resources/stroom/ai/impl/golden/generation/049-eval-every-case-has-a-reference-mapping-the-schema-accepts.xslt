<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <!-- Fields of the record no rule reads are logged (mcp_unread_fields) -->
    <xsl:call-template name="mcp_unread_fields"/>
    <xsl:variable name="evt" select="data[@name='evt']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$evt = 'A17'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$evt = 'A18'">
        <xsl:apply-templates select="." mode="event_type_failed_logon"/>
      </xsl:when>
      <xsl:when test="$evt = 'A40'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:when test="$evt = 'A52'">
        <xsl:apply-templates select="." mode="event_type_password_change"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('evt=', string(($evt)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('evt', 'host', 'res', 'src', 'ts', 'usr'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'logon': records where evt = A17. Writes an Authenticate event (TypeId Authenticate-Logon) from evt. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Authenticate-Logon</TypeId>
        <Description>Account logged on</Description>
        <xsl:apply-templates select="." mode="authenticate"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'failed logon': records where evt = A18. Writes an Authenticate event (TypeId Authenticate-FailedLogon)
       from evt. -->
  <xsl:template match="node()" mode="event_type_failed_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Authenticate-FailedLogon</TypeId>
        <Description>Account failed to log on</Description>
        <xsl:apply-templates select="." mode="authenticate"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where evt = A40. Writes an Authenticate event (TypeId Authenticate-Logoff) from evt. -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Authenticate-Logoff</TypeId>
        <Description>Account logged off</Description>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:apply-templates select="." mode="outcome"/>
          <xsl:sequence select="mcp:data('evt', data[@name='evt']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'password change': records where evt = A52. Writes an Authenticate event (TypeId
       Authenticate-PasswordChange) from evt. -->
  <xsl:template match="node()" mode="event_type_password_change">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Authenticate-PasswordChange</TypeId>
        <Description>Account password changed</Description>
        <Authenticate>
          <Action>ChangePassword</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:apply-templates select="." mode="outcome"/>
          <xsl:sequence select="mcp:data('evt', data[@name='evt']/@value)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'failed logon', 'logoff', 'password change' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='ts']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((data[@name='ts']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssXXX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'failed logon', 'logoff', 'password change' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Acme directory</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>acme-directory</Generator>
      <xsl:if test="data[@name='host']/@value[normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(data[@name='host']/@value)[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="data[@name='src']/@value[normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="(data[@name='src']/@value)[1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="data[@name='usr']/@value[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(data[@name='usr']/@value)[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- authenticate: shared by the rules 'logon', 'failed logon' -->
  <xsl:template match="node()" mode="authenticate">
    <Authenticate>
      <Action>Logon</Action>
      <xsl:apply-templates select="." mode="user"/>
      <xsl:apply-templates select="." mode="outcome"/>
      <xsl:sequence select="mcp:data('evt', data[@name='evt']/@value)"/>
    </Authenticate>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'failed logon', 'logoff', 'password change' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="data[@name='usr']/@value[normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="(data[@name='usr']/@value)[1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- outcome: shared by the rules 'logon', 'failed logon', 'logoff', 'password change' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:variable name="res" select="data[@name='res']/@value[normalize-space(.)]"/>
    <xsl:if test="$res[1] = ('R0', 'R7', 'R9')">
      <Outcome>
        <Success>
          <xsl:value-of select="if ($res[1] = 'R0') then 'true' else if ($res[1] = 'R7') then 'false' else 'false'"/>
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
</xsl:stylesheet>
