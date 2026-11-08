<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://schemas.microsoft.com/win/2004/08/events/event" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="*/Event" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
    <xsl:variable name="action_parts" select="analyze-string(string($event_data_data[1]), '(?:^|[;|()\]]|\s-)\s*Action: \[([^\]]*)\]')"/>
    <xsl:variable name="action" select="($action_parts/fn:match//fn:group[@nr=1])[normalize-space(.)]"/>
    <xsl:variable name="event_parts" select="analyze-string(string($event_data_data[1]), '(?:^|[;|()\]]|\s-)\s*Event: \[([^\]]*)\]')"/>
    <xsl:variable name="event" select="($event_parts/fn:match//fn:group[@nr=1])[normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$event = 'User' and $action = ('Login', 'Login Failed')">
        <xsl:apply-templates select="." mode="event_type_user_logon"/>
      </xsl:when>
      <xsl:when test="$event = 'User' and $action = 'Logout'">
        <xsl:apply-templates select="." mode="event_type_user_logoff"/>
      </xsl:when>
      <xsl:when test="$event = 'Secret'">
        <xsl:apply-templates select="." mode="event_type_secret"/>
        <xsl:call-template name="mcp_kept_unknown">
          <xsl:with-param name="group" select="concat('secret|', string-join((string(($event)[1]), string(($action)[1])), '|'))"/>
          <xsl:with-param name="line" select="concat('Kept as Unknown by rule ''secret'': record ', $record-no, ' (', string-join((concat('event=', string(($event)[1])), concat('action=', string(($action)[1]))), ' | '), ')')"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
        <xsl:call-template name="mcp_kept_unknown">
          <xsl:with-param name="group" select="concat('other|', string-join((string(($event)[1]), string(($action)[1])), '|'))"/>
          <xsl:with-param name="line" select="concat('Kept as Unknown by rule ''other'': record ', $record-no, ' (', string-join((concat('event=', string(($event)[1])), concat('action=', string(($action)[1]))), ' | '), ')')"/>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_kept_unknown: a record a rule keeps as Unknown on purpose, logged once a stream for each kind (the rule and
       the values the rules test) -->
  <xsl:template name="mcp_kept_unknown">
    <xsl:param name="group"/>
    <xsl:param name="line"/>
    <xsl:variable name="mcp_kept" select="concat('mcp-unknown:', $group)"/>
    <xsl:if test="not(string(stroom:get($mcp_kept)))">
      <xsl:value-of select="stroom:put($mcp_kept, 'y')"/>
      <xsl:sequence select="stroom:log('INFO', $line)"/>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'user_logon': records where event = User and action in Login, Login Failed. Writes an Authenticate event
       (TypeId from event) from user, System/Provider/@Name, System/EventID, by_user, item_id, client, secret,
       secret_id (user, by_user, item_id, client, secret, secret_id extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_user_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:sequence select="mcp:data('System/Provider/@Name', System/Provider/@Name)"/>
          <xsl:sequence select="mcp:data('System/EventID', System/EventID)"/>
          <xsl:apply-templates select="." mode="data_by_user_to_secret_id"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'user_logoff': records where event = User and action = Logout. Writes an Authenticate event (TypeId from
       event) from user, System/Provider/@Name, System/EventID, by_user, item_id, client, secret, secret_id
       (user, by_user, item_id, client, secret, secret_id extracted from EventData/Data). -->
  <xsl:template match="node()" mode="event_type_user_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:sequence select="mcp:data('System/Provider/@Name', System/Provider/@Name)"/>
          <xsl:sequence select="mcp:data('System/EventID', System/EventID)"/>
          <xsl:apply-templates select="." mode="data_by_user_to_secret_id"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'secret': records where event = Secret. Writes an Unknown event (TypeId from event) from
       System/Provider/@Name, System/EventID, action, by_user, item_id, client, secret, secret_id (action,
       by_user, item_id, client, secret, secret_id extracted from EventData/Data). Unknown on purpose: kept
       unknown for the test. -->
  <xsl:template match="node()" mode="event_type_secret">
    <!-- secret: EventDetail/Unknown on purpose: kept unknown for the test -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
      <xsl:variable name="action_parts" select="analyze-string(string($event_data_data[1]), '(?:^|[;|()\]]|\s-)\s*Action: \[([^\]]*)\]')"/>
      <xsl:variable name="event_parts" select="analyze-string(string($event_data_data[1]), '(?:^|[;|()\]]|\s-)\s*Event: \[([^\]]*)\]')"/>
      <xsl:if test="($event_parts/fn:match//fn:group[@nr=1])[normalize-space(.)] or (System/Provider/@Name)[normalize-space(.)] or (System/EventID)[normalize-space(.)] or ($action_parts/fn:match//fn:group[@nr=1])[normalize-space(.)] or normalize-space(mcp:keyed_value($event_data_data, 'By User')) or normalize-space(mcp:keyed_value($event_data_data, 'Item Id')) or normalize-space(mcp:keyed_value($event_data_data, 'Client')) or normalize-space(mcp:keyed_value($event_data_data, 'Secret')) or normalize-space(mcp:keyed_value($event_data_data, 'Secret Id'))">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:apply-templates select="." mode="unknown"/>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from event) from
       System/Provider/@Name, System/EventID, action, by_user, item_id, client, secret, secret_id (action,
       by_user, item_id, client, secret, secret_id extracted from EventData/Data). Unknown on purpose: kept
       unknown for the test. -->
  <xsl:template match="node()" mode="event_type_other">
    <!-- other: EventDetail/Unknown on purpose: kept unknown for the test -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:variable name="event_parts" select="analyze-string(string((EventData/Data)[1]), '(?:^|[;|()\]]|\s-)\s*Event: \[([^\]]*)\]')"/>
          <xsl:value-of select="if (($event_parts/fn:match//fn:group[@nr=1])[normalize-space(.)]) then ($event_parts/fn:match//fn:group[@nr=1])[1] else 'other'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="unknown"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'user_logon', 'user_logoff', 'secret', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="(System/TimeCreated/@SystemTime)[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((System/TimeCreated/@SystemTime)[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'user_logon', 'user_logoff', 'secret', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
      <xsl:if test="normalize-space(mcp:keyed_value($event_data_data, 'Session Id'))">
        <SessionId>
          <xsl:value-of select="mcp:keyed_value($event_data_data, 'Session Id')[1]"/>
        </SessionId>
      </xsl:if>
      <System>
        <Name>Secret Server</Name>
        <Environment>Prod</Environment>
      </System>
      <Generator>Secret Server</Generator>
      <xsl:if test="(System/Computer)[normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="(System/Computer)[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="normalize-space(mcp:keyed_value($event_data_data, 'Source IP'))">
        <Client>
          <IPAddress>
            <xsl:value-of select="mcp:keyed_value($event_data_data, 'Source IP')[1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="normalize-space(mcp:keyed_value($event_data_data, 'User'))">
        <User>
          <Id>
            <xsl:value-of select="mcp:keyed_value($event_data_data, 'User')[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'user_logon', 'user_logoff', 'secret' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:variable name="event_parts" select="analyze-string(string((EventData/Data)[1]), '(?:^|[;|()\]]|\s-)\s*Event: \[([^\]]*)\]')"/>
    <xsl:if test="($event_parts/fn:match//fn:group[@nr=1])[normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="($event_parts/fn:match//fn:group[@nr=1])[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'user_logon', 'user_logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="normalize-space(mcp:keyed_value(EventData/Data, 'User'))">
      <User>
        <Id>
          <xsl:value-of select="mcp:keyed_value(EventData/Data, 'User')[1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- data_by_user_to_secret_id: shared by the rules 'user_logon', 'user_logoff', 'secret', 'other' -->
  <xsl:template match="node()" mode="data_by_user_to_secret_id">
    <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
    <xsl:sequence select="mcp:data('by_user', mcp:keyed_value($event_data_data, 'By User'))"/>
    <xsl:sequence select="mcp:data('item_id', mcp:keyed_value($event_data_data, 'Item Id'))"/>
    <xsl:sequence select="mcp:data('client', mcp:keyed_value($event_data_data, 'Client'))"/>
    <xsl:sequence select="mcp:data('secret', mcp:keyed_value($event_data_data, 'Secret'))"/>
    <xsl:sequence select="mcp:data('secret_id', mcp:keyed_value($event_data_data, 'Secret Id'))"/>
  </xsl:template>
  <!-- unknown: shared by the rules 'secret', 'other' -->
  <xsl:template match="node()" mode="unknown">
    <xsl:variable name="event_data_data" select="(EventData/Data)[normalize-space(.)]"/>
    <xsl:variable name="action_parts" select="analyze-string(string($event_data_data[1]), '(?:^|[;|()\]]|\s-)\s*Action: \[([^\]]*)\]')"/>
    <xsl:if test="(System/Provider/@Name)[normalize-space(.)] or (System/EventID)[normalize-space(.)] or ($action_parts/fn:match//fn:group[@nr=1])[normalize-space(.)] or normalize-space(mcp:keyed_value($event_data_data, 'By User')) or normalize-space(mcp:keyed_value($event_data_data, 'Item Id')) or normalize-space(mcp:keyed_value($event_data_data, 'Client')) or normalize-space(mcp:keyed_value($event_data_data, 'Secret')) or normalize-space(mcp:keyed_value($event_data_data, 'Secret Id'))">
      <Unknown>
        <xsl:sequence select="mcp:data('System/Provider/@Name', System/Provider/@Name)"/>
        <xsl:sequence select="mcp:data('System/EventID', System/EventID)"/>
        <xsl:sequence select="mcp:data('action', $action_parts/fn:match//fn:group[@nr=1])"/>
        <xsl:apply-templates select="." mode="data_by_user_to_secret_id"/>
      </Unknown>
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
  <xsl:function name="mcp:keyed_value" as="xs:string?">
    <!-- The value of a key in the text: the regex (?:^|[;|()\]]|\s-)\s*<key>:
       (.*?)(?:\s*[;|()]|\s+-\s+(?:\[|[A-Za-z][A-Za-z0-9_-]*(?: [A-Za-z][A-Za-z0-9_-]*){0,3}: )|\s*$), its first
       group -->
    <xsl:param name="text" as="item()*"/>
    <xsl:param name="key" as="xs:string"/>
    <xsl:sequence select="for $g in (analyze-string(string($text[1]), concat('(?:^|[;|()\]]|\s-)\s*', $key, ': (.*?)(?:\s*[;|()]|\s+-\s+(?:\[|[A-Za-z][A-Za-z0-9_-]*(?: [A-Za-z][A-Za-z0-9_-]*){0,3}: )|\s*$)'))//fn:group[@nr=1])[1] return string($g)"/>
  </xsl:function>
</xsl:stylesheet>
