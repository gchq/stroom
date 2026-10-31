<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://schemas.microsoft.com/win/2004/08/events/event" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:variable name="action_to_action" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'Account Locked'" select="'AccountLock'"/>
      <xsl:map-entry key="'Login'" select="'Logon'"/>
      <xsl:map-entry key="'Login Failed'" select="'Logon'"/>
      <xsl:map-entry key="'Logout'" select="'Logoff'"/>
      <xsl:map-entry key="'MFA Challenge'" select="'Other'"/>
    </xsl:map>
  </xsl:variable>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="*/Event" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="msg_user_parts" select="analyze-string(string(EventData/Data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
    <xsl:variable name="action" select="($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)]"/>
    <xsl:variable name="category" select="($msg_user_parts/fn:match//fn:group[@nr=2])[normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$category = 'User' and $action = ('Login', 'Login Failed', 'Logout', 'MFA Challenge', 'Account Locked')">
        <xsl:apply-templates select="." mode="event_type_auth"/>
      </xsl:when>
      <xsl:when test="$category = 'User' and $action = ('Role Changed', 'Password Reset')">
        <xsl:apply-templates select="." mode="event_type_user_admin"/>
      </xsl:when>
      <xsl:when test="$category = 'Secret' and $action = ('View', 'Password Displayed', 'Copy Password', 'Checkout', 'Checkin', 'Export')">
        <xsl:apply-templates select="." mode="event_type_secret_view"/>
      </xsl:when>
      <xsl:when test="$category = 'Secret' and $action = 'Create'">
        <xsl:apply-templates select="." mode="event_type_secret_create"/>
      </xsl:when>
      <xsl:when test="$category = 'Secret' and $action = ('Edit', 'Restore', 'Password Changed')">
        <xsl:apply-templates select="." mode="event_type_secret_update"/>
      </xsl:when>
      <xsl:when test="$category = 'Secret' and $action = 'Delete'">
        <xsl:apply-templates select="." mode="event_type_secret_delete"/>
      </xsl:when>
      <xsl:when test="$category = 'Secret' and $action = ('Access Denied', 'Password Change Failed', 'Heartbeat Failed')">
        <xsl:apply-templates select="." mode="event_type_secret_alert"/>
      </xsl:when>
      <xsl:when test="$category = 'Secret' and $action = 'Launch'">
        <xsl:apply-templates select="." mode="event_type_secret_launch"/>
      </xsl:when>
      <xsl:when test="$category = 'Folder' and $action = 'Create'">
        <xsl:apply-templates select="." mode="event_type_folder_create"/>
      </xsl:when>
      <xsl:when test="$category = 'Folder'">
        <xsl:apply-templates select="." mode="event_type_folder_update"/>
      </xsl:when>
      <xsl:when test="$category = 'Permission'">
        <xsl:apply-templates select="." mode="event_type_permission"/>
      </xsl:when>
      <xsl:when test="$category = 'Session'">
        <xsl:apply-templates select="." mode="event_type_session"/>
      </xsl:when>
      <xsl:when test="$category = 'System' and $action = 'Configuration Changed'">
        <xsl:apply-templates select="." mode="event_type_system_config"/>
      </xsl:when>
      <xsl:when test="$category = 'System'">
        <xsl:apply-templates select="." mode="event_type_system_job"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
        <xsl:sequence select="stroom:log('INFO', concat('Kept as Unknown by rule ''other'': record ', $record-no, ' (', string-join((concat('category=', string(($category)[1])), concat('action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'auth': records where category = User and action in Login, Login Failed, Logout, MFA Challenge, Account
       Locked. Writes an Authenticate event (TypeId from action) from action, actor, msg_user, item_id, ticket
       (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_auth">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
          <xsl:variable name="msg_user_parts" select="analyze-string(string($event_data_data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
          <Action>
            <xsl:value-of select="($action_to_action?(($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)]), 'Other')[1]"/>
          </Action>
          <xsl:variable name="msg_user" select="($msg_user_parts/fn:match//fn:group[@nr=1])[normalize-space(.)]"/>
          <xsl:variable name="actor" select="($msg_user_parts/fn:match//fn:group[@nr=4])[normalize-space(.)]"/>
          <xsl:if test="$actor or $msg_user">
            <User>
              <xsl:if test="$actor">
                <Id>
                  <xsl:value-of select="$actor"/>
                </Id>
              </xsl:if>
              <xsl:if test="$msg_user">
                <Name>
                  <xsl:value-of select="$msg_user"/>
                </Name>
              </xsl:if>
            </User>
          </xsl:if>
          <xsl:sequence select="mcp:data('action', $msg_user_parts/fn:match//fn:group[@nr=3])"/>
          <xsl:sequence select="mcp:data('item_id', $msg_user_parts/fn:match//fn:group[@nr=5])"/>
          <xsl:variable name="ticket_parts" select="analyze-string(string($event_data_data[1]), '^.*?Ticket: ([^\s;]+).*$')"/>
          <xsl:sequence select="mcp:data('ticket', $ticket_parts/fn:match//fn:group[@nr=1])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'user-admin': records where category = User and action in Role Changed, Password Reset. Writes an Update
       event (TypeId from action) from category, item_id, action, ticket (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_user_admin">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:apply-templates select="." mode="event_detail"/>
    </Event>
  </xsl:template>
  <!-- Rule 'secret-view': records where category = Secret and action in View, Password Displayed, Copy Password,
       Checkout, Checkin, Export. Writes a View event (TypeId from action) from category, item_id, action,
       ticket (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_secret_view">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
      <xsl:variable name="ticket_parts" select="analyze-string(string($event_data_data[1]), '^.*?Ticket: ([^\s;]+).*$')"/>
      <xsl:variable name="ticket" select="($ticket_parts/fn:match//fn:group[@nr=1])[normalize-space(.)]"/>
      <xsl:variable name="msg_user_parts" select="analyze-string(string($event_data_data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
      <xsl:variable name="item_id" select="($msg_user_parts/fn:match//fn:group[@nr=5])[normalize-space(.)]"/>
      <xsl:variable name="action" select="($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)]"/>
      <xsl:if test="$event_data_data or $action or ($msg_user_parts/fn:match//fn:group[@nr=2])[normalize-space(.)] or $item_id or $action or $item_id or $ticket">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:apply-templates select="." mode="description"/>
          <xsl:if test="($msg_user_parts/fn:match//fn:group[@nr=2])[normalize-space(.)] or $item_id or $action or $item_id or $ticket">
            <View>
              <xsl:apply-templates select="." mode="object"/>
              <xsl:sequence select="mcp:data('action', $action)"/>
              <xsl:sequence select="mcp:data('item_id', $item_id)"/>
              <xsl:sequence select="mcp:data('ticket', $ticket)"/>
            </View>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'secret-create': records where category = Secret and action = Create. Writes a Create event (TypeId from
       action) from category, item_id, action (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_secret_create">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="msg_user_parts" select="analyze-string(string(EventData/Data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
      <xsl:if test="(EventData/Data)[normalize-space(.)] or ($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)] or ($msg_user_parts/fn:match//fn:group[@nr=2])[normalize-space(.)] or ($msg_user_parts/fn:match//fn:group[@nr=5])[normalize-space(.)] or ($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)] or ($msg_user_parts/fn:match//fn:group[@nr=5])[normalize-space(.)]">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:apply-templates select="." mode="description"/>
          <xsl:apply-templates select="." mode="create"/>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'secret-update': records where category = Secret and action in Edit, Restore, Password Changed. Writes an
       Update event (TypeId from action) from category, item_id, action, ticket (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_secret_update">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:apply-templates select="." mode="event_detail"/>
    </Event>
  </xsl:template>
  <!-- Rule 'secret-delete': records where category = Secret and action = Delete. Writes a Delete event (TypeId from
       action) from category, item_id, action (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_secret_delete">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="msg_user_parts" select="analyze-string(string(EventData/Data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
      <xsl:variable name="item_id" select="($msg_user_parts/fn:match//fn:group[@nr=5])[normalize-space(.)]"/>
      <xsl:variable name="action" select="($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)]"/>
      <xsl:if test="(EventData/Data)[normalize-space(.)] or $action or ($msg_user_parts/fn:match//fn:group[@nr=2])[normalize-space(.)] or $item_id or $action or $item_id">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:apply-templates select="." mode="description"/>
          <xsl:if test="($msg_user_parts/fn:match//fn:group[@nr=2])[normalize-space(.)] or $item_id or $action or $item_id">
            <Delete>
              <xsl:apply-templates select="." mode="object"/>
              <xsl:sequence select="mcp:data('action', $action)"/>
              <xsl:sequence select="mcp:data('item_id', $item_id)"/>
            </Delete>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'secret-alert': records where category = Secret and action in Access Denied, Password Change Failed,
       Heartbeat Failed. Writes an Alert event (TypeId from action) from action, item_id, ticket (extracted from
       EventData/Data). -->
  <xsl:template match="node()" mode="event_type_secret_alert">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Alert>
          <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
          <xsl:variable name="msg_user_parts" select="analyze-string(string($event_data_data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
          <xsl:variable name="action" select="($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)]"/>
          <Type>
            <xsl:value-of select="if ($action = 'Access Denied') then 'Other' else if ($action = 'Heartbeat Failed') then 'Error' else if ($action = 'Password Change Failed') then 'Error' else 'Other'"/>
          </Type>
          <xsl:sequence select="mcp:data('action', $action)"/>
          <xsl:sequence select="mcp:data('item_id', $msg_user_parts/fn:match//fn:group[@nr=5])"/>
          <xsl:variable name="ticket_parts" select="analyze-string(string($event_data_data[1]), '^.*?Ticket: ([^\s;]+).*$')"/>
          <xsl:sequence select="mcp:data('ticket', $ticket_parts/fn:match//fn:group[@nr=1])"/>
        </Alert>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'secret-launch': records where category = Secret and action = Launch. Writes a Process event (TypeId from
       action) from action, item_id, ticket (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_secret_launch">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Process>
          <Action>Execute</Action>
          <Type>Application</Type>
          <xsl:apply-templates select="." mode="command"/>
          <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
          <xsl:variable name="msg_user_parts" select="analyze-string(string($event_data_data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
          <xsl:sequence select="mcp:data('action', $msg_user_parts/fn:match//fn:group[@nr=3])"/>
          <xsl:sequence select="mcp:data('item_id', $msg_user_parts/fn:match//fn:group[@nr=5])"/>
          <xsl:variable name="ticket_parts" select="analyze-string(string($event_data_data[1]), '^.*?Ticket: ([^\s;]+).*$')"/>
          <xsl:sequence select="mcp:data('ticket', $ticket_parts/fn:match//fn:group[@nr=1])"/>
        </Process>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'folder-create': records where category = Folder and action = Create. Writes a Create event (TypeId Folder
       Create) from category, item_id, action (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_folder_create">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>Folder Create</TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:apply-templates select="." mode="create"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'folder-update': records where category = Folder. Writes an Update event (TypeId from action) from
       category, item_id, action, ticket (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_folder_update">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:variable name="msg_user_parts" select="analyze-string(string(EventData/Data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
          <xsl:value-of select="if (($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)]) then $msg_user_parts/fn:match//fn:group[@nr=3] else 'folder-update'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:apply-templates select="." mode="update"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'permission': records where category = Permission. Writes an Update event (TypeId from action) from
       category, item_id, action, ticket (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_permission">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:variable name="msg_user_parts" select="analyze-string(string(EventData/Data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
          <xsl:value-of select="if (($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)]) then $msg_user_parts/fn:match//fn:group[@nr=3] else 'permission'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:apply-templates select="." mode="update"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'session': records where category = Session. Writes a Process event (TypeId from action) from action,
       item_id, ticket (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_session">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
        <xsl:variable name="msg_user_parts" select="analyze-string(string($event_data_data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
        <xsl:variable name="action" select="($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action else 'session'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Process>
          <Action>
            <xsl:value-of select="if ($action = 'End') then 'Shutdown' else if ($action = 'Recording Viewed') then 'Access' else if ($action = 'Start') then 'Startup' else 'Execute'"/>
          </Action>
          <Type>Application</Type>
          <xsl:apply-templates select="." mode="command"/>
          <xsl:sequence select="mcp:data('action', $action)"/>
          <xsl:sequence select="mcp:data('item_id', $msg_user_parts/fn:match//fn:group[@nr=5])"/>
          <xsl:variable name="ticket_parts" select="analyze-string(string($event_data_data[1]), '^.*?Ticket: ([^\s;]+).*$')"/>
          <xsl:sequence select="mcp:data('ticket', $ticket_parts/fn:match//fn:group[@nr=1])"/>
        </Process>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'system-config': records where category = System and action = Configuration Changed. Writes an Update
       event (TypeId from action) from category, item_id, action, ticket (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_system_config">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:apply-templates select="." mode="event_detail"/>
    </Event>
  </xsl:template>
  <!-- Rule 'system-job': records where category = System. Writes a Process event (TypeId from action) from action,
       item_id (extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_system_job">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:variable name="msg_user_parts" select="analyze-string(string(EventData/Data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
        <xsl:variable name="action" select="($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action else 'system-job'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Process>
          <Action>Execute</Action>
          <Type>Application</Type>
          <xsl:apply-templates select="." mode="command"/>
          <xsl:sequence select="mcp:data('action', $action)"/>
          <xsl:sequence select="mcp:data('item_id', $msg_user_parts/fn:match//fn:group[@nr=5])"/>
        </Process>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from action) from category,
       action, item_id (extracted from EventData/Data). Unknown on purpose: Records whose Event category is none
       of User/Secret/Folder/Permission/Session/System, or whose action matches no rule above. None are expected
       in this sample; kept as Unknown with category/action/item id so nothing is silently dropped.. -->
  <xsl:template match="node()" mode="event_type_other">
    <!-- other: EventDetail/Unknown on purpose: Records whose Event category is none of User/Secret/Folder/Permission/Session/System, or whose action matches no rule above. None are expected in this sample; kept as Unknown with category/action/item id so nothing is silently dropped. -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:variable name="msg_user_parts" select="analyze-string(string(EventData/Data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
        <xsl:variable name="action" select="($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)]"/>
        <TypeId>
          <xsl:value-of select="if ($action) then $action else 'other'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:if test="($msg_user_parts/fn:match//fn:group[@nr=2])[normalize-space(.)] or $action or ($msg_user_parts/fn:match//fn:group[@nr=5])[normalize-space(.)]">
          <Unknown>
            <xsl:sequence select="mcp:data('Category', $msg_user_parts/fn:match//fn:group[@nr=2])"/>
            <xsl:sequence select="mcp:data('Action', $action)"/>
            <xsl:sequence select="mcp:data('ItemId', $msg_user_parts/fn:match//fn:group[@nr=5])"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'auth', 'user-admin', 'secret-view', 'secret-create', 'secret-update',
       'secret-delete', 'secret-alert', 'secret-launch', 'folder-create', 'folder-update', 'permission',
       'session', 'system-config', 'system-job', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="(System/TimeCreated/@SystemTime)[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(System/TimeCreated/@SystemTime[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSSSSSX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'auth', 'user-admin', 'secret-view', 'secret-create', 'secret-update',
       'secret-delete', 'secret-alert', 'secret-launch', 'folder-create', 'folder-update', 'permission',
       'session', 'system-config', 'system-job', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
      <xsl:variable name="session_id_parts" select="analyze-string(string($event_data_data[1]), '^.*?Session Id: (\d+).*$')"/>
      <xsl:if test="($session_id_parts/fn:match//fn:group[@nr=1])[normalize-space(.)]">
        <SessionId>
          <xsl:value-of select="$session_id_parts/fn:match//fn:group[@nr=1]"/>
        </SessionId>
      </xsl:if>
      <System>
        <Name>Delinea Secret Server</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>Delinea Secret Server</Generator>
      <xsl:if test="(System/Computer)[normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="System/Computer"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="source_ip_parts" select="analyze-string(string($event_data_data[1]), '^.*?Source IP: (\d{1,3}(?:\.\d{1,3}){3}).*$')"/>
      <xsl:if test="($source_ip_parts/fn:match//fn:group[@nr=1])[normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="$source_ip_parts/fn:match//fn:group[@nr=1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:variable name="msg_user_parts" select="analyze-string(string($event_data_data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
      <xsl:if test="($msg_user_parts/fn:match//fn:group[@nr=4])[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="replace(replace($msg_user_parts/fn:match//fn:group[@nr=4][1], '^[^\\]*\\', ''), '@.*$', '')"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'auth', 'user-admin', 'secret-view', 'secret-create', 'secret-update',
       'secret-delete', 'secret-alert', 'secret-launch', 'system-config' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:variable name="msg_user_parts" select="analyze-string(string(EventData/Data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
    <xsl:if test="($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="$msg_user_parts/fn:match//fn:group[@nr=3]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'auth', 'user-admin', 'secret-view', 'secret-create', 'secret-update',
       'secret-delete', 'secret-alert', 'secret-launch', 'folder-create', 'folder-update', 'permission',
       'session', 'system-config', 'system-job', 'other' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="(EventData/Data)[normalize-space(.)]">
      <Description>
        <xsl:value-of select="EventData/Data"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- event_detail: shared by the rules 'user-admin', 'secret-update', 'system-config' -->
  <xsl:template match="node()" mode="event_detail">
    <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
    <xsl:variable name="ticket_parts" select="analyze-string(string($event_data_data[1]), '^.*?Ticket: ([^\s;]+).*$')"/>
    <xsl:variable name="msg_user_parts" select="analyze-string(string($event_data_data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
    <xsl:if test="$event_data_data or ($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)] or ($msg_user_parts/fn:match//fn:group[@nr=2])[normalize-space(.)] or ($msg_user_parts/fn:match//fn:group[@nr=5])[normalize-space(.)] or ($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)] or ($msg_user_parts/fn:match//fn:group[@nr=5])[normalize-space(.)] or ($ticket_parts/fn:match//fn:group[@nr=1])[normalize-space(.)]">
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <xsl:apply-templates select="." mode="update"/>
      </EventDetail>
    </xsl:if>
  </xsl:template>
  <!-- update: shared by the rules 'user-admin', 'secret-update', 'folder-update', 'permission', 'system-config' -->
  <xsl:template match="node()" mode="update">
    <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
    <xsl:variable name="ticket_parts" select="analyze-string(string($event_data_data[1]), '^.*?Ticket: ([^\s;]+).*$')"/>
    <xsl:variable name="msg_user_parts" select="analyze-string(string($event_data_data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
    <xsl:variable name="item_id" select="($msg_user_parts/fn:match//fn:group[@nr=5])[normalize-space(.)]"/>
    <xsl:variable name="category" select="($msg_user_parts/fn:match//fn:group[@nr=2])[normalize-space(.)]"/>
    <xsl:if test="$category or $item_id or ($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)] or $item_id or ($ticket_parts/fn:match//fn:group[@nr=1])[normalize-space(.)]">
      <Update>
        <xsl:if test="$category or $item_id">
          <After>
            <Object>
              <xsl:if test="$category">
                <Type>
                  <xsl:value-of select="$category"/>
                </Type>
              </xsl:if>
              <xsl:if test="$item_id">
                <Id>
                  <xsl:value-of select="$item_id"/>
                </Id>
              </xsl:if>
            </Object>
          </After>
        </xsl:if>
        <xsl:sequence select="mcp:data('action', $msg_user_parts/fn:match//fn:group[@nr=3])"/>
        <xsl:sequence select="mcp:data('item_id', $item_id)"/>
        <xsl:sequence select="mcp:data('ticket', $ticket_parts/fn:match//fn:group[@nr=1])"/>
      </Update>
    </xsl:if>
  </xsl:template>
  <!-- object: shared by the rules 'secret-view', 'secret-create', 'secret-delete', 'folder-create' -->
  <xsl:template match="node()" mode="object">
    <xsl:variable name="msg_user_parts" select="analyze-string(string(EventData/Data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
    <xsl:variable name="item_id" select="($msg_user_parts/fn:match//fn:group[@nr=5])[normalize-space(.)]"/>
    <xsl:variable name="category" select="($msg_user_parts/fn:match//fn:group[@nr=2])[normalize-space(.)]"/>
    <xsl:if test="$category or $item_id">
      <Object>
        <xsl:if test="$category">
          <Type>
            <xsl:value-of select="$category"/>
          </Type>
        </xsl:if>
        <xsl:if test="$item_id">
          <Id>
            <xsl:value-of select="$item_id"/>
          </Id>
        </xsl:if>
      </Object>
    </xsl:if>
  </xsl:template>
  <!-- create: shared by the rules 'secret-create', 'folder-create' -->
  <xsl:template match="node()" mode="create">
    <xsl:variable name="msg_user_parts" select="analyze-string(string(EventData/Data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
    <xsl:variable name="item_id" select="($msg_user_parts/fn:match//fn:group[@nr=5])[normalize-space(.)]"/>
    <xsl:if test="($msg_user_parts/fn:match//fn:group[@nr=2])[normalize-space(.)] or $item_id or ($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)] or $item_id">
      <Create>
        <xsl:apply-templates select="." mode="object"/>
        <xsl:sequence select="mcp:data('action', $msg_user_parts/fn:match//fn:group[@nr=3])"/>
        <xsl:sequence select="mcp:data('item_id', $item_id)"/>
      </Create>
    </xsl:if>
  </xsl:template>
  <!-- command: shared by the rules 'secret-launch', 'session', 'system-job' -->
  <xsl:template match="node()" mode="command">
    <xsl:variable name="msg_user_parts" select="analyze-string(string(EventData/Data[1]), '^[A-Z][a-z]{2} +\d{1,2} \d{4} \d{2}:\d{2}:\d{2} - User: (.+) - \[\[SecretServer\]\] Event: \[([^\]]*)\] Action: \[([^\]]*)\] By User: (\S+)(?: \(Item Id: (\d+)\))?.*$')"/>
    <xsl:if test="($msg_user_parts/fn:match//fn:group[@nr=3])[normalize-space(.)]">
      <Command>
        <xsl:value-of select="$msg_user_parts/fn:match//fn:group[@nr=3]"/>
      </Command>
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
