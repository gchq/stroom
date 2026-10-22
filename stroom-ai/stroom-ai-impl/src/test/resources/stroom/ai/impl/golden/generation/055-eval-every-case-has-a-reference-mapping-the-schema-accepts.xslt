<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://schemas.microsoft.com/win/2004/08/events/event" exclude-result-prefixes="stroom xs mcp">
  <xsl:variable name="event_data_data_name_logon_type_to_logon_type" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'2'" select="'Interactive'"/>
      <xsl:map-entry key="'3'" select="'Network'"/>
      <xsl:map-entry key="'4'" select="'Batch'"/>
      <xsl:map-entry key="'5'" select="'Service'"/>
      <xsl:map-entry key="'10'" select="'RemoteInteractive'"/>
      <xsl:map-entry key="'11'" select="'CachedInteractive'"/>
    </xsl:map>
  </xsl:variable>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="*/Event" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="system_event_id" select="(System/EventID)[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
    <xsl:choose>
      <xsl:when test="$system_event_id = '4624'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4625'">
        <xsl:apply-templates select="." mode="event_type_failed_logon"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4634'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4672'">
        <xsl:apply-templates select="." mode="event_type_special_privileges"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4740'">
        <xsl:apply-templates select="." mode="event_type_account_locked"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4720'">
        <xsl:apply-templates select="." mode="event_type_account_created"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('System/EventID=', string(($system_event_id)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where System/EventID = 4624. Writes an Authenticate event (TypeId from System/EventID)
       from EventData/Data[@Name='TargetUserName'], EventData/Data[@Name='IpAddress'],
       EventData/Data[@Name='TargetDomainName'], EventData/Data[@Name='LogonType']. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="logon_type"/>
          <xsl:apply-templates select="." mode="authenticate_user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'failed logon': records where System/EventID = 4625. Writes an Authenticate event (TypeId from
       System/EventID) from EventData/Data[@Name='TargetUserName'], EventData/Data[@Name='IpAddress'],
       EventData/Data[@Name='TargetDomainName'], EventData/Data[@Name='LogonType'],
       EventData/Data[@Name='FailureReason']. -->
  <xsl:template match="node()" mode="event_type_failed_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="logon_type"/>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <Outcome>
            <Success>false</Success>
            <xsl:if test="(EventData/Data[@Name='FailureReason'])[normalize-space(.)][not(normalize-space(.) = ('-'))]">
              <Description>
                <xsl:value-of select="EventData/Data[@Name='FailureReason']"/>
              </Description>
            </xsl:if>
          </Outcome>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where System/EventID = 4634. Writes an Authenticate event (TypeId from System/EventID)
       from EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <EventSource>
        <xsl:apply-templates select="." mode="system"/>
        <xsl:apply-templates select="." mode="generator"/>
        <xsl:apply-templates select="." mode="device"/>
        <xsl:apply-templates select="." mode="user"/>
      </EventSource>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="event_detail_authenticate_user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'special privileges': records where System/EventID = 4672. Writes an Authenticate event (TypeId from
       System/EventID) from EventData/Data[@Name='SubjectUserName'], EventData/Data[@Name='PrivilegeList']. -->
  <xsl:template match="node()" mode="event_type_special_privileges">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>ElevatePrivilege</Action>
          <xsl:if test="(EventData/Data[@Name='SubjectUserName'])[normalize-space(.)][not(normalize-space(.) = ('-'))]">
            <User>
              <Id>
                <xsl:value-of select="EventData/Data[@Name='SubjectUserName']"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:sequence select="mcp:data('privileges', EventData/Data[@Name='PrivilegeList'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'account locked': records where System/EventID = 4740. Writes an Authenticate event (TypeId from
       System/EventID) from EventData/Data[@Name='SubjectUserName'], EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_account_locked">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>AccountLock</Action>
          <xsl:apply-templates select="." mode="event_detail_authenticate_user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'account created': records where System/EventID = 4720. Writes a Create event (TypeId from System/EventID)
       from EventData/Data[@Name='SubjectUserName'], EventData/Data[@Name='TargetUserName'],
       EventData/Data[@Name='TargetDomainName']. -->
  <xsl:template match="node()" mode="event_type_account_created">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <xsl:if test="(System/EventID)[normalize-space(.)][not(normalize-space(.) = ('-'))] or (EventData/Data[@Name='TargetUserName'])[normalize-space(.)][not(normalize-space(.) = ('-'))] or (EventData/Data[@Name='TargetDomainName'])[normalize-space(.)][not(normalize-space(.) = ('-'))]">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)][not(normalize-space(.) = ('-'))] or (EventData/Data[@Name='TargetDomainName'])[normalize-space(.)][not(normalize-space(.) = ('-'))]">
            <Create>
              <xsl:apply-templates select="." mode="authenticate_user"/>
            </Create>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'failed logon', 'logoff', 'special privileges', 'account locked',
       'account created' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="exists((replace(System/TimeCreated/@SystemTime, '(\.\d{3})\d*Z$', '$1Z'))[normalize-space(.)][not(normalize-space(.) = ('-'))])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(replace(System/TimeCreated/@SystemTime, '(\.\d{3})\d*Z$', '$1Z')[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'failed logon' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <xsl:apply-templates select="." mode="system"/>
      <xsl:apply-templates select="." mode="generator"/>
      <xsl:apply-templates select="." mode="device"/>
      <xsl:if test="(EventData/Data[@Name='IpAddress'])[normalize-space(.)][not(normalize-space(.) = ('-'))]">
        <Client>
          <IPAddress>
            <xsl:value-of select="EventData/Data[@Name='IpAddress']"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:apply-templates select="." mode="user"/>
    </EventSource>
  </xsl:template>
  <!-- system: shared by the rules 'logon', 'failed logon', 'logoff', 'special privileges', 'account locked', 'account
       created' -->
  <xsl:template match="node()" mode="system">
    <System>
      <Name>Active Directory</Name>
      <Environment>Eval</Environment>
    </System>
  </xsl:template>
  <!-- generator: shared by the rules 'logon', 'failed logon', 'logoff', 'special privileges', 'account locked',
       'account created' -->
  <xsl:template match="node()" mode="generator">
    <xsl:if test="(System/Provider/@Name)[normalize-space(.)][not(normalize-space(.) = ('-'))]">
      <Generator>
        <xsl:value-of select="System/Provider/@Name"/>
      </Generator>
    </xsl:if>
  </xsl:template>
  <!-- device: shared by the rules 'logon', 'failed logon', 'logoff', 'special privileges', 'account locked', 'account
       created' -->
  <xsl:template match="node()" mode="device">
    <xsl:if test="(System/Computer)[normalize-space(.)][not(normalize-space(.) = ('-'))]">
      <Device>
        <HostName>
          <xsl:value-of select="System/Computer"/>
        </HostName>
      </Device>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'failed logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)][not(normalize-space(.) = ('-'))]">
      <User>
        <Id>
          <xsl:value-of select="EventData/Data[@Name='TargetUserName']"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'failed logon', 'logoff', 'special privileges', 'account locked',
       'account created' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="(System/EventID)[normalize-space(.)][not(normalize-space(.) = ('-'))]">
      <TypeId>
        <xsl:value-of select="System/EventID"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- logon_type: shared by the rules 'logon', 'failed logon' -->
  <xsl:template match="node()" mode="logon_type">
    <LogonType>
      <xsl:value-of select="($event_data_data_name_logon_type_to_logon_type?((EventData/Data[@Name='LogonType'])[normalize-space(.)][not(normalize-space(.) = ('-'))]), 'Other')[1]"/>
    </LogonType>
  </xsl:template>
  <!-- authenticate_user: shared by the rules 'logon', 'failed logon', 'account created' -->
  <xsl:template match="node()" mode="authenticate_user">
    <xsl:variable name="event_data_data_name_target_domain_name" select="(EventData/Data[@Name='TargetDomainName'])[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
    <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)][not(normalize-space(.) = ('-'))] or $event_data_data_name_target_domain_name">
      <User>
        <xsl:apply-templates select="." mode="id"/>
        <xsl:if test="$event_data_data_name_target_domain_name">
          <Domain>
            <xsl:value-of select="$event_data_data_name_target_domain_name"/>
          </Domain>
        </xsl:if>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- id: shared by the rules 'logon', 'failed logon', 'logoff', 'account locked', 'account created' -->
  <xsl:template match="node()" mode="id">
    <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)][not(normalize-space(.) = ('-'))]">
      <Id>
        <xsl:value-of select="EventData/Data[@Name='TargetUserName']"/>
      </Id>
    </xsl:if>
  </xsl:template>
  <!-- event_detail_authenticate_user: shared by the rules 'logoff', 'account locked' -->
  <xsl:template match="node()" mode="event_detail_authenticate_user">
    <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)][not(normalize-space(.) = ('-'))]">
      <User>
        <xsl:apply-templates select="." mode="id"/>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- event_source_2: shared by the rules 'special privileges', 'account locked', 'account created' -->
  <xsl:template match="node()" mode="event_source_2">
    <EventSource>
      <xsl:apply-templates select="." mode="system"/>
      <xsl:apply-templates select="." mode="generator"/>
      <xsl:apply-templates select="." mode="device"/>
      <xsl:if test="(EventData/Data[@Name='SubjectUserName'])[normalize-space(.)][not(normalize-space(.) = ('-'))]">
        <User>
          <Id>
            <xsl:value-of select="EventData/Data[@Name='SubjectUserName']"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank, nor -) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)][not(normalize-space(.) = ('-'))])">
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
