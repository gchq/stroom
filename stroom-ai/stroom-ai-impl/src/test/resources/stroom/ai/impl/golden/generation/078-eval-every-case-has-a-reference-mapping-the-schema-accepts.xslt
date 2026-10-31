<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="message" select="data[@name='message']/@value[normalize-space(.)]"/>
    <xsl:variable name="verb_parts" select="analyze-string(string($message[1]), '^statement: (\w+)[\s\S]*$')"/>
    <xsl:variable name="verb_2" select="$verb_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="exists($message[matches(., '^connection authorized')])">
        <xsl:apply-templates select="." mode="event_type_connect"/>
      </xsl:when>
      <xsl:when test="exists($message[matches(., '^disconnection')])">
        <xsl:apply-templates select="." mode="event_type_disconnect"/>
      </xsl:when>
      <xsl:when test="$verb_2 = 'SELECT'">
        <xsl:apply-templates select="." mode="event_type_query"/>
      </xsl:when>
      <xsl:when test="$verb_2 = 'UPDATE'">
        <xsl:apply-templates select="." mode="event_type_update"/>
      </xsl:when>
      <xsl:when test="$verb_2 = 'DELETE'">
        <xsl:apply-templates select="." mode="event_type_delete"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('message=', string(($message)[1])), concat('verb=', string(($verb_2)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'connect': records where message matches ^connection authorized. Writes an Authenticate event from user. -->
  <xsl:template match="node()" mode="event_type_connect">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:variable name="verb_parts" select="analyze-string(string(data[@name='message']/@value[1]), '^statement: (\w+)[\s\S]*$')"/>
          <xsl:value-of select="if (exists((($verb_parts/fn:match//fn:group[@nr=1], data[@name='level']/@value)[normalize-space(.)][1])[normalize-space(.)])) then (($verb_parts/fn:match//fn:group[@nr=1], data[@name='level']/@value)[normalize-space(.)][1]) else 'connect'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'disconnect': records where message matches ^disconnection. Writes an Authenticate event from user. -->
  <xsl:template match="node()" mode="event_type_disconnect">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:variable name="verb_parts" select="analyze-string(string(data[@name='message']/@value[1]), '^statement: (\w+)[\s\S]*$')"/>
          <xsl:value-of select="if (exists((($verb_parts/fn:match//fn:group[@nr=1], data[@name='level']/@value)[normalize-space(.)][1])[normalize-space(.)])) then (($verb_parts/fn:match//fn:group[@nr=1], data[@name='level']/@value)[normalize-space(.)][1]) else 'disconnect'"/>
        </TypeId>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'query': records where verb = SELECT. Writes a View event from database. -->
  <xsl:template match="node()" mode="event_type_query">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <View>
          <Object>
            <Type>Database query</Type>
            <xsl:apply-templates select="." mode="name"/>
          </Object>
        </View>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'update': records where verb = UPDATE. Writes an Update event from database. -->
  <xsl:template match="node()" mode="event_type_update">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Update>
          <After>
            <Object>
              <Type>Database rows</Type>
              <xsl:if test="normalize-space(data[@name='database']/@value)">
                <Name>
                  <xsl:value-of select="data[@name='database']/@value"/>
                </Name>
              </xsl:if>
            </Object>
          </After>
        </Update>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'delete': records where verb = DELETE. Writes a Delete event from database. -->
  <xsl:template match="node()" mode="event_type_delete">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Delete>
          <Object>
            <Type>Database rows</Type>
            <xsl:apply-templates select="." mode="name"/>
          </Object>
        </Delete>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'connect', 'disconnect', 'query', 'update', 'delete' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='time']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd HH:mm:ss.SSS', 'UTC')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'connect', 'disconnect', 'query', 'update', 'delete' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Finance Database</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>PostgreSQL</Generator>
      <Device>
        <HostName>pg01</HostName>
      </Device>
      <xsl:if test="normalize-space(data[@name='user']/@value)">
        <User>
          <Id>
            <xsl:value-of select="data[@name='user']/@value"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- description: shared by the rules 'connect', 'disconnect', 'query', 'update', 'delete' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="normalize-space(data[@name='message']/@value)">
      <Description>
        <xsl:value-of select="data[@name='message']/@value"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'connect', 'disconnect' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="normalize-space(data[@name='user']/@value)">
      <User>
        <Id>
          <xsl:value-of select="data[@name='user']/@value"/>
        </Id>
      </User>
    </xsl:if>
  </xsl:template>
  <!-- type_id: shared by the rules 'query', 'update', 'delete' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:variable name="verb_parts" select="analyze-string(string(data[@name='message']/@value[1]), '^statement: (\w+)[\s\S]*$')"/>
    <xsl:if test="exists((($verb_parts/fn:match//fn:group[@nr=1], data[@name='level']/@value)[normalize-space(.)][1])[normalize-space(.)])">
      <TypeId>
        <xsl:value-of select="(($verb_parts/fn:match//fn:group[@nr=1], data[@name='level']/@value)[normalize-space(.)][1])"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- name: shared by the rules 'query', 'delete' -->
  <xsl:template match="node()" mode="name">
    <xsl:if test="normalize-space(data[@name='database']/@value)">
      <Name>
        <xsl:value-of select="data[@name='database']/@value"/>
      </Name>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
