<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://schemas.microsoft.com/win/2004/08/events/event" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="*/Event" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="system_event_id" select="(System/EventID)[normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$system_event_id = '4624'">
        <xsl:apply-templates select="." mode="event_type_an_account_was_successfully_logged_on"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4625'">
        <xsl:apply-templates select="." mode="event_type_an_account_failed_to_log_on"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4634'">
        <xsl:apply-templates select="." mode="event_type_an_account_was_logged_off"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4647'">
        <xsl:apply-templates select="." mode="event_type_user_initiated_logoff"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4648'">
        <xsl:apply-templates select="." mode="event_type_a_logon_was_attempted_using_explicit_credentials"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4672'">
        <xsl:apply-templates select="." mode="event_type_special_privileges_assigned_to_new_logon"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4688'">
        <xsl:apply-templates select="." mode="event_type_a_new_process_has_been_created"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4689'">
        <xsl:apply-templates select="." mode="event_type_a_process_has_exited"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4697'">
        <xsl:apply-templates select="." mode="event_type_a_service_was_installed_in_the_system"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4719'">
        <xsl:apply-templates select="." mode="event_type_system_audit_policy_was_changed"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4720'">
        <xsl:apply-templates select="." mode="event_type_a_user_account_was_created"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4722'">
        <xsl:apply-templates select="." mode="event_type_a_user_account_was_enabled"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4723'">
        <xsl:apply-templates select="." mode="event_type_an_attempt_was_made_to_change_an_accounts_password"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4724'">
        <xsl:apply-templates select="." mode="event_type_an_attempt_was_made_to_reset_an_accounts_password"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4725'">
        <xsl:apply-templates select="." mode="event_type_a_user_account_was_disabled"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4726'">
        <xsl:apply-templates select="." mode="event_type_a_user_account_was_deleted"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4728'">
        <xsl:apply-templates select="." mode="event_type_a_member_was_added_to_a_security_enabled_global_group"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4729'">
        <xsl:apply-templates select="." mode="event_type_a_member_was_removed_from_a_security_enabled_global_group"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4740'">
        <xsl:apply-templates select="." mode="event_type_a_user_account_was_locked_out"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '4767'">
        <xsl:apply-templates select="." mode="event_type_a_user_account_was_unlocked"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '1102'">
        <xsl:apply-templates select="." mode="event_type_the_audit_log_was_cleared"/>
      </xsl:when>
      <xsl:when test="$system_event_id = '5140'">
        <xsl:apply-templates select="." mode="event_type_a_network_share_object_was_accessed"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('System/EventID=', string(($system_event_id)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'an account was successfully logged on': records where System/EventID = 4624. Writes an Authenticate event
       (TypeId Authenticate-Logon) from System/EventID, EventData/Data[@Name='TargetUserName'],
       EventData/Data[@Name='IpAddress']. -->
  <xsl:template match="node()" mode="event_type_an_account_was_successfully_logged_on">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Authenticate-Logon</TypeId>
        <Description>An account was successfully logged on</Description>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'an account failed to log on': records where System/EventID = 4625. Writes an Authenticate event (TypeId
       Authenticate-FailedLogon) from System/EventID, EventData/Data[@Name='TargetUserName'],
       EventData/Data[@Name='IpAddress']. -->
  <xsl:template match="node()" mode="event_type_an_account_failed_to_log_on">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Authenticate-FailedLogon</TypeId>
        <Description>An account failed to log on</Description>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <Outcome>
            <Success>false</Success>
          </Outcome>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'an account was logged off': records where System/EventID = 4634. Writes an Authenticate event (TypeId
       Authenticate-Logoff) from System/EventID, EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_an_account_was_logged_off">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <TypeId>Authenticate-Logoff</TypeId>
        <Description>An account was logged off</Description>
        <xsl:apply-templates select="." mode="authenticate"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'user initiated logoff': records where System/EventID = 4647. Writes an Authenticate event (TypeId
       Authenticate-UserLogoff) from System/EventID, EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_user_initiated_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_2"/>
      <EventDetail>
        <TypeId>Authenticate-UserLogoff</TypeId>
        <Description>User initiated logoff</Description>
        <xsl:apply-templates select="." mode="authenticate"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a logon was attempted using explicit credentials': records where System/EventID = 4648. Writes an
       Authenticate event (TypeId Authenticate-ExplicitCredentials) from System/EventID,
       EventData/Data[@Name='SubjectUserName'], EventData/Data[@Name='TargetUserName'],
       EventData/Data[@Name='TargetServerName']. -->
  <xsl:template match="node()" mode="event_type_a_logon_was_attempted_using_explicit_credentials">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Authenticate-ExplicitCredentials</TypeId>
        <Description>A logon was attempted using explicit credentials</Description>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
          <xsl:sequence select="mcp:data('target_server', EventData/Data[@Name='TargetServerName'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'special privileges assigned to new logon': records where System/EventID = 4672. Writes an Authenticate
       event (TypeId Authenticate-SpecialPrivileges) from System/EventID,
       EventData/Data[@Name='SubjectUserName'], EventData/Data[@Name='PrivilegeList']. -->
  <xsl:template match="node()" mode="event_type_special_privileges_assigned_to_new_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Authenticate-SpecialPrivileges</TypeId>
        <Description>Special privileges assigned to new logon</Description>
        <Authenticate>
          <Action>ElevatePrivilege</Action>
          <xsl:if test="(EventData/Data[@Name='SubjectUserName'])[normalize-space(.)]">
            <User>
              <Id>
                <xsl:value-of select="(EventData/Data[@Name='SubjectUserName'])[1]"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
          <xsl:sequence select="mcp:data('privileges', EventData/Data[@Name='PrivilegeList'])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a new process has been created': records where System/EventID = 4688. Writes a Process event (TypeId
       Process-Start) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='NewProcessName'], EventData/Data[@Name='CommandLine']. -->
  <xsl:template match="node()" mode="event_type_a_new_process_has_been_created">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Process-Start</TypeId>
        <Description>A new process has been created</Description>
        <Process>
          <Action>Execute</Action>
          <Type>Application</Type>
          <xsl:if test="(EventData/Data[@Name='NewProcessName'])[normalize-space(.)]">
            <Command>
              <xsl:value-of select="(EventData/Data[@Name='NewProcessName'])[1]"/>
            </Command>
          </xsl:if>
          <xsl:if test="(EventData/Data[@Name='CommandLine'])[normalize-space(.)]">
            <Arguments>
              <xsl:value-of select="(EventData/Data[@Name='CommandLine'])[1]"/>
            </Arguments>
          </xsl:if>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Process>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a process has exited': records where System/EventID = 4689. Writes a Process event (TypeId Process-Exit)
       from System/EventID, EventData/Data[@Name='SubjectUserName'], EventData/Data[@Name='ProcessName']. -->
  <xsl:template match="node()" mode="event_type_a_process_has_exited">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Process-Exit</TypeId>
        <Description>A process has exited</Description>
        <Process>
          <Action>Terminate</Action>
          <Type>Application</Type>
          <xsl:if test="(EventData/Data[@Name='ProcessName'])[normalize-space(.)]">
            <Command>
              <xsl:value-of select="(EventData/Data[@Name='ProcessName'])[1]"/>
            </Command>
          </xsl:if>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Process>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a service was installed in the system': records where System/EventID = 4697. Writes an Install event
       (TypeId Install-Service) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='ServiceName'], EventData/Data[@Name='ServiceFileName']. -->
  <xsl:template match="node()" mode="event_type_a_service_was_installed_in_the_system">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Install-Service</TypeId>
        <Description>A service was installed in the system</Description>
        <xsl:variable name="event_data_data_name_service_file_name" select="(EventData/Data[@Name='ServiceFileName'])[normalize-space(.)]"/>
        <xsl:variable name="event_data_data_name_service_name" select="(EventData/Data[@Name='ServiceName'])[normalize-space(.)]"/>
        <xsl:if test="$event_data_data_name_service_name or $event_data_data_name_service_file_name or (System/EventID)[normalize-space(.)]">
          <Install>
            <xsl:if test="$event_data_data_name_service_name or $event_data_data_name_service_file_name">
              <Software>
                <xsl:if test="$event_data_data_name_service_name">
                  <Name>
                    <xsl:value-of select="$event_data_data_name_service_name[1]"/>
                  </Name>
                </xsl:if>
                <xsl:sequence select="mcp:data('file', $event_data_data_name_service_file_name)"/>
              </Software>
            </xsl:if>
            <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
          </Install>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'system audit policy was changed': records where System/EventID = 4719. Writes an Update event (TypeId
       Update-AuditPolicy) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='CategoryId'], EventData/Data[@Name='AuditPolicyChanges']. -->
  <xsl:template match="node()" mode="event_type_system_audit_policy_was_changed">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Update-AuditPolicy</TypeId>
        <Description>System audit policy was changed</Description>
        <Update>
          <After>
            <Configuration>
              <Type>Audit policy</Type>
              <xsl:if test="(EventData/Data[@Name='CategoryId'])[normalize-space(.)]">
                <Name>
                  <xsl:value-of select="(EventData/Data[@Name='CategoryId'])[1]"/>
                </Name>
              </xsl:if>
              <xsl:sequence select="mcp:data('change', EventData/Data[@Name='AuditPolicyChanges'])"/>
            </Configuration>
          </After>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Update>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a user account was created': records where System/EventID = 4720. Writes a Create event (TypeId
       Create-UserAccount) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_a_user_account_was_created">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Create-UserAccount</TypeId>
        <Description>A user account was created</Description>
        <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)] or (System/EventID)[normalize-space(.)]">
          <Create>
            <xsl:apply-templates select="." mode="authenticate_user"/>
            <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
          </Create>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a user account was enabled': records where System/EventID = 4722. Writes an Update event (TypeId
       Enable-UserAccount) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_a_user_account_was_enabled">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Enable-UserAccount</TypeId>
        <Description>A user account was enabled</Description>
        <Update>
          <After>
            <User>
              <xsl:apply-templates select="." mode="id"/>
              <Data Name="change" Value="enabled"/>
            </User>
          </After>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Update>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'an attempt was made to change an accounts password': records where System/EventID = 4723. Writes an
       Authenticate event (TypeId Change-Password) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_an_attempt_was_made_to_change_an_accounts_password">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Change-Password</TypeId>
        <Description>An attempt was made to change an account's password</Description>
        <Authenticate>
          <Action>ChangePassword</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'an attempt was made to reset an accounts password': records where System/EventID = 4724. Writes an
       Authenticate event (TypeId Reset-Password) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_an_attempt_was_made_to_reset_an_accounts_password">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Reset-Password</TypeId>
        <Description>An attempt was made to reset an account's password</Description>
        <Authenticate>
          <Action>ResetPassword</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a user account was disabled': records where System/EventID = 4725. Writes an Update event (TypeId
       Disable-UserAccount) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_a_user_account_was_disabled">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Disable-UserAccount</TypeId>
        <Description>A user account was disabled</Description>
        <Update>
          <After>
            <User>
              <xsl:apply-templates select="." mode="id"/>
              <Data Name="change" Value="disabled"/>
            </User>
          </After>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Update>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a user account was deleted': records where System/EventID = 4726. Writes a Delete event (TypeId
       Delete-UserAccount) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_a_user_account_was_deleted">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Delete-UserAccount</TypeId>
        <Description>A user account was deleted</Description>
        <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)] or (System/EventID)[normalize-space(.)]">
          <Delete>
            <xsl:apply-templates select="." mode="authenticate_user"/>
            <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
          </Delete>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a member was added to a security-enabled global group': records where System/EventID = 4728. Writes an
       Authorise event (TypeId Add-GroupMember) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='MemberName'], EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_a_member_was_added_to_a_security_enabled_global_group">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Add-GroupMember</TypeId>
        <Description>A member was added to a security-enabled global group</Description>
        <Authorise>
          <xsl:apply-templates select="." mode="authorise_user"/>
          <Action>Modify</Action>
          <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)]">
            <AddGroups>
              <Group>
                <Id>
                  <xsl:value-of select="(EventData/Data[@Name='TargetUserName'])[1]"/>
                </Id>
              </Group>
            </AddGroups>
          </xsl:if>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Authorise>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a member was removed from a security-enabled global group': records where System/EventID = 4729. Writes
       an Authorise event (TypeId Remove-GroupMember) from System/EventID,
       EventData/Data[@Name='SubjectUserName'], EventData/Data[@Name='MemberName'],
       EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_a_member_was_removed_from_a_security_enabled_global_group">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Remove-GroupMember</TypeId>
        <Description>A member was removed from a security-enabled global group</Description>
        <Authorise>
          <xsl:apply-templates select="." mode="authorise_user"/>
          <Action>Modify</Action>
          <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)]">
            <RemoveGroups>
              <Group>
                <Id>
                  <xsl:value-of select="(EventData/Data[@Name='TargetUserName'])[1]"/>
                </Id>
              </Group>
            </RemoveGroups>
          </xsl:if>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Authorise>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a user account was locked out': records where System/EventID = 4740. Writes an Authenticate event (TypeId
       Lock-UserAccount) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_a_user_account_was_locked_out">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Lock-UserAccount</TypeId>
        <Description>A user account was locked out</Description>
        <Authenticate>
          <Action>AccountLock</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a user account was unlocked': records where System/EventID = 4767. Writes an Authenticate event (TypeId
       Unlock-UserAccount) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='TargetUserName']. -->
  <xsl:template match="node()" mode="event_type_a_user_account_was_unlocked">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Unlock-UserAccount</TypeId>
        <Description>A user account was unlocked</Description>
        <Authenticate>
          <Action>AccountUnlock</Action>
          <xsl:apply-templates select="." mode="authenticate_user"/>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'the audit log was cleared': records where System/EventID = 1102. Writes a Delete event (TypeId
       Clear-AuditLog) from System/EventID, EventData/Data[@Name='SubjectUserName']. -->
  <xsl:template match="node()" mode="event_type_the_audit_log_was_cleared">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source_3"/>
      <EventDetail>
        <TypeId>Clear-AuditLog</TypeId>
        <Description>The audit log was cleared</Description>
        <Delete>
          <Object>
            <Type>Event log</Type>
            <Name>Security</Name>
          </Object>
          <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
        </Delete>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'a network share object was accessed': records where System/EventID = 5140. Writes a View event (TypeId
       Access-NetworkShare) from System/EventID, EventData/Data[@Name='SubjectUserName'],
       EventData/Data[@Name='IpAddress'], EventData/Data[@Name='ShareName']. -->
  <xsl:template match="node()" mode="event_type_a_network_share_object_was_accessed">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <EventSource>
        <xsl:apply-templates select="." mode="system"/>
        <xsl:apply-templates select="." mode="generator"/>
        <xsl:apply-templates select="." mode="device"/>
        <xsl:apply-templates select="." mode="client"/>
        <xsl:apply-templates select="." mode="event_source_user"/>
      </EventSource>
      <EventDetail>
        <TypeId>Access-NetworkShare</TypeId>
        <Description>A network share object was accessed</Description>
        <xsl:variable name="event_data_data_name_share_name" select="(EventData/Data[@Name='ShareName'])[normalize-space(.)]"/>
        <xsl:if test="$event_data_data_name_share_name or (System/EventID)[normalize-space(.)]">
          <View>
            <xsl:if test="$event_data_data_name_share_name">
              <Folder>
                <Path>
                  <xsl:value-of select="$event_data_data_name_share_name[1]"/>
                </Path>
              </Folder>
            </xsl:if>
            <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
          </View>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'an account was successfully logged on', 'an account failed to log on', 'an
       account was logged off', 'user initiated logoff', 'a logon was attempted using explicit credentials',
       'special privileges assigned to new logon', 'a new process has been created', 'a process has exited', 'a
       service was installed in the system', 'system audit policy was changed', 'a user account was created', 'a
       user account was enabled', 'an attempt was made to change an accounts password', 'an attempt was made to
       reset an accounts password', 'a user account was disabled', 'a user account was deleted', 'a member was
       added to a security-enabled global group', 'a member was removed from a security-enabled global group',
       'a user account was locked out', 'a user account was unlocked', 'the audit log was cleared', 'a network
       share object was accessed' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:variable name="event_time_time_created" select="(replace(System/TimeCreated/@SystemTime, '(\.\d{3})\d*Z$', '$1Z'))[normalize-space(.)]"/>
    <xsl:if test="exists($event_time_time_created)">
      <EventTime>
        <TimeCreated>
          <xsl:if test="count($event_time_time_created) gt 1">
            <xsl:sequence select="stroom:log('WARN', concat('Several values (', count($event_time_time_created), ') for ', 'EventTime/TimeCreated, only the first written (repeat: true writes each): record ', stroom:record-no()))"/>
          </xsl:if>
          <xsl:value-of select="stroom:format-date($event_time_time_created[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'an account was successfully logged on', 'an account failed to log on' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <xsl:apply-templates select="." mode="system"/>
      <xsl:apply-templates select="." mode="generator"/>
      <xsl:apply-templates select="." mode="device"/>
      <xsl:apply-templates select="." mode="client"/>
      <xsl:apply-templates select="." mode="user"/>
    </EventSource>
  </xsl:template>
  <!-- system: shared by the rules 'an account was successfully logged on', 'an account failed to log on', 'an account
       was logged off', 'user initiated logoff', 'a logon was attempted using explicit credentials', 'special
       privileges assigned to new logon', 'a new process has been created', 'a process has exited', 'a service
       was installed in the system', 'system audit policy was changed', 'a user account was created', 'a user
       account was enabled', 'an attempt was made to change an accounts password', 'an attempt was made to reset
       an accounts password', 'a user account was disabled', 'a user account was deleted', 'a member was added
       to a security-enabled global group', 'a member was removed from a security-enabled global group', 'a user
       account was locked out', 'a user account was unlocked', 'the audit log was cleared', 'a network share
       object was accessed' -->
  <xsl:template match="node()" mode="system">
    <System>
      <Name>Active Directory</Name>
      <Environment>Eval</Environment>
    </System>
  </xsl:template>
  <!-- generator: shared by the rules 'an account was successfully logged on', 'an account failed to log on', 'an
       account was logged off', 'user initiated logoff', 'a logon was attempted using explicit credentials',
       'special privileges assigned to new logon', 'a new process has been created', 'a process has exited', 'a
       service was installed in the system', 'system audit policy was changed', 'a user account was created', 'a
       user account was enabled', 'an attempt was made to change an accounts password', 'an attempt was made to
       reset an accounts password', 'a user account was disabled', 'a user account was deleted', 'a member was
       added to a security-enabled global group', 'a member was removed from a security-enabled global group',
       'a user account was locked out', 'a user account was unlocked', 'the audit log was cleared', 'a network
       share object was accessed' -->
  <xsl:template match="node()" mode="generator">
    <xsl:if test="(System/Provider/@Name)[normalize-space(.)]">
      <Generator>
        <xsl:value-of select="(System/Provider/@Name)[1]"/>
      </Generator>
    </xsl:if>
  </xsl:template>
  <!-- device: shared by the rules 'an account was successfully logged on', 'an account failed to log on', 'an account
       was logged off', 'user initiated logoff', 'a logon was attempted using explicit credentials', 'special
       privileges assigned to new logon', 'a new process has been created', 'a process has exited', 'a service
       was installed in the system', 'system audit policy was changed', 'a user account was created', 'a user
       account was enabled', 'an attempt was made to change an accounts password', 'an attempt was made to reset
       an accounts password', 'a user account was disabled', 'a user account was deleted', 'a member was added
       to a security-enabled global group', 'a member was removed from a security-enabled global group', 'a user
       account was locked out', 'a user account was unlocked', 'the audit log was cleared', 'a network share
       object was accessed' -->
  <xsl:template match="node()" mode="device">
    <xsl:if test="(System/Computer)[normalize-space(.)]">
      <Device>
        <HostName>
          <xsl:value-of select="(System/Computer)[1]"/>
        </HostName>
      </Device>
    </xsl:if>
  </xsl:template>
  <!-- client: shared by the rules 'an account was successfully logged on', 'an account failed to log on', 'a network
       share object was accessed' -->
  <xsl:template match="node()" mode="client">
    <xsl:if test="(EventData/Data[@Name='IpAddress'])[normalize-space(.)]">
      <Client>
        <IPAddress>
          <xsl:value-of select="(EventData/Data[@Name='IpAddress'])[1]"/>
        </IPAddress>
      </Client>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'an account was successfully logged on', 'an account failed to log on', 'an account
       was logged off', 'user initiated logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="(EventData/Data[@Name='TargetUserName'])[1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- authenticate_user: shared by the rules 'an account was successfully logged on', 'an account failed to log on',
       'an account was logged off', 'user initiated logoff', 'a logon was attempted using explicit credentials',
       'a user account was created', 'an attempt was made to change an accounts password', 'an attempt was made
       to reset an accounts password', 'a user account was deleted', 'a user account was locked out', 'a user
       account was unlocked' -->
  <xsl:template match="node()" mode="authenticate_user">
    <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="(EventData/Data[@Name='TargetUserName'])[1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- event_source_2: shared by the rules 'an account was logged off', 'user initiated logoff' -->
  <xsl:template match="node()" mode="event_source_2">
    <EventSource>
      <xsl:apply-templates select="." mode="system"/>
      <xsl:apply-templates select="." mode="generator"/>
      <xsl:apply-templates select="." mode="device"/>
      <xsl:apply-templates select="." mode="user"/>
    </EventSource>
  </xsl:template>
  <!-- authenticate: shared by the rules 'an account was logged off', 'user initiated logoff' -->
  <xsl:template match="node()" mode="authenticate">
    <Authenticate>
      <Action>Logoff</Action>
      <xsl:apply-templates select="." mode="authenticate_user"/>
      <xsl:sequence select="mcp:data('EventID', System/EventID)"/>
    </Authenticate>
  </xsl:template>
  <!-- event_source_3: shared by the rules 'a logon was attempted using explicit credentials', 'special privileges
       assigned to new logon', 'a new process has been created', 'a process has exited', 'a service was
       installed in the system', 'system audit policy was changed', 'a user account was created', 'a user
       account was enabled', 'an attempt was made to change an accounts password', 'an attempt was made to reset
       an accounts password', 'a user account was disabled', 'a user account was deleted', 'a member was added
       to a security-enabled global group', 'a member was removed from a security-enabled global group', 'a user
       account was locked out', 'a user account was unlocked', 'the audit log was cleared' -->
  <xsl:template match="node()" mode="event_source_3">
    <EventSource>
      <xsl:apply-templates select="." mode="system"/>
      <xsl:apply-templates select="." mode="generator"/>
      <xsl:apply-templates select="." mode="device"/>
      <xsl:apply-templates select="." mode="event_source_user"/>
    </EventSource>
  </xsl:template>
  <!-- event_source_user: shared by the rules 'a logon was attempted using explicit credentials', 'special privileges
       assigned to new logon', 'a new process has been created', 'a process has exited', 'a service was
       installed in the system', 'system audit policy was changed', 'a user account was created', 'a user
       account was enabled', 'an attempt was made to change an accounts password', 'an attempt was made to reset
       an accounts password', 'a user account was disabled', 'a user account was deleted', 'a member was added
       to a security-enabled global group', 'a member was removed from a security-enabled global group', 'a user
       account was locked out', 'a user account was unlocked', 'the audit log was cleared', 'a network share
       object was accessed' -->
  <xsl:template match="node()" mode="event_source_user">
    <xsl:if test="(EventData/Data[@Name='SubjectUserName'])[normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="(EventData/Data[@Name='SubjectUserName'])[1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- id: shared by the rules 'a user account was enabled', 'a user account was disabled' -->
  <xsl:template match="node()" mode="id">
    <xsl:if test="(EventData/Data[@Name='TargetUserName'])[normalize-space(.)]">
      <Id>
        <xsl:value-of select="(EventData/Data[@Name='TargetUserName'])[1]"/>
      </Id>
    </xsl:if>
  </xsl:template>
  <!-- authorise_user: shared by the rules 'a member was added to a security-enabled global group', 'a member was
       removed from a security-enabled global group' -->
  <xsl:template match="node()" mode="authorise_user">
    <xsl:if test="(EventData/Data[@Name='MemberName'])[normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="(EventData/Data[@Name='MemberName'])[1]"/>
        </Id>
      </User>
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
