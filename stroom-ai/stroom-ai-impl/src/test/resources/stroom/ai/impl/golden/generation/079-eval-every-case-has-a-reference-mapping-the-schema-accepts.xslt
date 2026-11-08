<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:variable name="result_to_success" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'success'" select="'true'"/>
      <xsl:map-entry key="'failure'" select="'false'"/>
    </xsl:map>
  </xsl:variable>
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
    <xsl:variable name="user_parts" select="analyze-string(string(replace(data[@name='message']/@value, '\n\S+ (stdout|stderr) [PF] ', '')[1]), '^user=(\S+) action=(\S+) result=(\S+) pod=(\S+)(?: detail=(\S+) rows=(\d+))?$')"/>
    <xsl:variable name="action" select="$user_parts/fn:match//fn:group[@nr=2][normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$action = 'login'">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$action = 'logout'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:when test="$action = 'export'">
        <xsl:apply-templates select="." mode="event_type_export"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('action=', string(($action)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('message', 'stream', 'time'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'logon': records where action = login. Writes an Authenticate event (TypeId from action) from user, result
       (extracted from an xpath). -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:apply-templates select="." mode="outcome"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where action = logout. Writes an Authenticate event (TypeId from action) from user
       (extracted from an xpath). -->
  <xsl:template match="node()" mode="event_type_logoff">
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
  <!-- Rule 'export': records where action = export. Writes an Export event (TypeId from action) from detail, rows,
       result (extracted from an xpath). -->
  <xsl:template match="node()" mode="event_type_export">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Export>
          <xsl:variable name="user_parts" select="analyze-string(string(replace(data[@name='message']/@value, '\n\S+ (stdout|stderr) [PF] ', '')[1]), '^user=(\S+) action=(\S+) result=(\S+) pod=(\S+)(?: detail=(\S+) rows=(\d+))?$')"/>
          <Source>
            <Object>
              <Type>Records</Type>
              <xsl:if test="$user_parts/fn:match//fn:group[@nr=5][normalize-space(.)]">
                <Description>
                  <xsl:value-of select="($user_parts/fn:match//fn:group[@nr=5])[1]"/>
                </Description>
              </xsl:if>
            </Object>
          </Source>
          <xsl:apply-templates select="." mode="outcome"/>
          <xsl:sequence select="mcp:data('rows', $user_parts/fn:match//fn:group[@nr=6])"/>
        </Export>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff', 'export' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((data[@name='time']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSSSSX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'logoff', 'export' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>API</Name>
        <Environment>Eval</Environment>
      </System>
      <xsl:if test="data[@name='stream']/@value[normalize-space(.)]">
        <Generator>
          <xsl:value-of select="(data[@name='stream']/@value)[1]"/>
        </Generator>
      </xsl:if>
      <xsl:variable name="user_parts" select="analyze-string(string(replace(data[@name='message']/@value, '\n\S+ (stdout|stderr) [PF] ', '')[1]), '^user=(\S+) action=(\S+) result=(\S+) pod=(\S+)(?: detail=(\S+) rows=(\d+))?$')"/>
      <xsl:if test="$user_parts/fn:match//fn:group[@nr=4][normalize-space(.)]">
        <Device>
          <HostName>
            <xsl:value-of select="($user_parts/fn:match//fn:group[@nr=4])[1]"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="$user_parts/fn:match//fn:group[@nr=1][normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="($user_parts/fn:match//fn:group[@nr=1])[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'logoff', 'export' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:variable name="user_parts" select="analyze-string(string(replace(data[@name='message']/@value, '\n\S+ (stdout|stderr) [PF] ', '')[1]), '^user=(\S+) action=(\S+) result=(\S+) pod=(\S+)(?: detail=(\S+) rows=(\d+))?$')"/>
    <xsl:if test="$user_parts/fn:match//fn:group[@nr=2][normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="($user_parts/fn:match//fn:group[@nr=2])[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="user_parts" select="analyze-string(string(replace(data[@name='message']/@value, '\n\S+ (stdout|stderr) [PF] ', '')[1]), '^user=(\S+) action=(\S+) result=(\S+) pod=(\S+)(?: detail=(\S+) rows=(\d+))?$')"/>
    <xsl:if test="$user_parts/fn:match//fn:group[@nr=1][normalize-space(.)]">
      <User>
        <Id>
          <xsl:value-of select="($user_parts/fn:match//fn:group[@nr=1])[1]"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- outcome: shared by the rules 'logon', 'export' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:variable name="user_parts" select="analyze-string(string(replace(data[@name='message']/@value, '\n\S+ (stdout|stderr) [PF] ', '')[1]), '^user=(\S+) action=(\S+) result=(\S+) pod=(\S+)(?: detail=(\S+) rows=(\d+))?$')"/>
    <xsl:if test="exists($result_to_success?(($user_parts/fn:match//fn:group[@nr=3])[1]))">
      <Outcome>
        <Success>
          <xsl:value-of select="$result_to_success?(($user_parts/fn:match//fn:group[@nr=3])[1])[1]"/>
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
