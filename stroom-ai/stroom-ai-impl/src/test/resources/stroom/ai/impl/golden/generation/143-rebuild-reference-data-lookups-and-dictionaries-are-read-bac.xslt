<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:variable name="vip_users_list" select="tokenize(stroom:dictionary('VIP users'), '\r?\n') ! normalize-space(.)"/>
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
    <xsl:choose>
      <xsl:when test="data[@name='user']/@value = $vip_users_list">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_vip"/>
        <xsl:sequence select="stroom:log('INFO', concat('Kept as Unknown by rule ''vip'': record ', $record-no, ' (', string-join((concat('user=', string((data[@name='user']/@value[normalize-space(.)])[1]))), ' | '), ')'))"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('host', 'hostname', 'time', 'user'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'vip': records where user in the dictionary VIP users. Writes an Unknown event (TypeId vip) from user.
       Unknown on purpose: a VIP list match has no action of its own. -->
  <xsl:template match="node()" mode="event_type_vip">
    <!-- vip: EventDetail/Unknown on purpose: a VIP list match has no action of its own -->
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>vip</TypeId>
        <xsl:apply-templates select="." mode="unknown"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId x) from user. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>x</TypeId>
        <xsl:apply-templates select="." mode="unknown"/>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'vip', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="(data[@name='time']/@value)[1]"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'vip', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>A</Name>
        <Environment>D</Environment>
      </System>
      <Generator>g</Generator>
      <xsl:if test="exists(((data[@name='hostname']/@value, data[@name='host']/@value)[normalize-space(.)][1])[normalize-space(.)])">
        <Device>
          <HostName>
            <xsl:value-of select="lower-case(((data[@name='hostname']/@value, data[@name='host']/@value)[normalize-space(.)][1])[1])"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="user_to_phone" select="stroom:lookup('USER_TO_PHONE', string((data[@name='user']/@value)[1]))[normalize-space(.)]"/>
      <xsl:variable name="user" select="data[@name='user']/@value[normalize-space(.)]"/>
      <xsl:if test="$user or exists($user_to_phone)">
        <User>
          <xsl:if test="$user">
            <Id>
              <xsl:value-of select="replace(replace($user[1], '^[^\\]*\\', ''), '@.*$', '')"/>
            </Id>
          </xsl:if>
          <xsl:if test="$user">
            <Domain>
              <xsl:value-of select="replace($user[1], '^(?:([^\\]*)\\.*|[^@]*@(.*))$', '$1$2')"/>
            </Domain>
          </xsl:if>
          <xsl:if test="exists($user_to_phone)">
            <UserDetails>
              <Phone>
                <xsl:value-of select="$user_to_phone"/>
              </Phone>
            </UserDetails>
          </xsl:if>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- unknown: shared by the rules 'vip', 'other' -->
  <xsl:template match="node()" mode="unknown">
    <xsl:if test="data[@name='user']/@value[normalize-space(.)]">
      <Unknown>
        <Data Name="u" Value="{(data[@name='user']/@value)[1]}"/>
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
</xsl:stylesheet>
