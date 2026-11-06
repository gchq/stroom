<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs mcp">
  <xsl:variable name="payload_result_to_success" as="map(xs:string, xs:string)">
    <xsl:map>
      <xsl:map-entry key="'success'" select="'true'"/>
      <xsl:map-entry key="'failure'" select="'false'"/>
    </xsl:map>
  </xsl:variable>
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:function name="mcp:parse-xml" as="node()?">
    <xsl:param name="text" as="item()*"/>
    <xsl:variable name="xml" select="string(($text)[1])"/>
    <xsl:if test="starts-with(normalize-space($xml), '&lt;')">
      <xsl:try>
        <xsl:sequence select="parse-xml($xml)"/>
        <xsl:catch/>
      </xsl:try>
    </xsl:if>
  </xsl:function>
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="operation" select="*[@key='operation'][normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$operation = 'Login'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_login"/>
      </xsl:when>
      <xsl:when test="$operation = 'GetAccount'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_read"/>
      </xsl:when>
      <xsl:when test="$operation = 'UpdateAccount'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_update"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('operation=', string(($operation)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="*[@key][not(@key = ('client_ip', 'operation', 'payload', 'service', 'time'))][normalize-space(string(.))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @key)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @key, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'login': records where operation = Login. Writes an Authenticate event (TypeId from operation) from
       payload//User, payload//Result. -->
  <xsl:template match="node()" mode="event_type_login">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="mcp:parse-xml((*[@key='payload'])[1])//*:User[normalize-space(.)]">
            <User>
              <Id>
                <xsl:value-of select="(mcp:parse-xml((*[@key='payload'])[1])//*:User)[1]"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:apply-templates select="." mode="outcome"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'read': records where operation = GetAccount. Writes a View event (TypeId from operation) from
       payload//Account/@id, payload//Result. -->
  <xsl:template match="node()" mode="event_type_read">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <View>
          <Object>
            <Type>Account</Type>
            <xsl:if test="mcp:parse-xml((*[@key='payload'])[1])//*:Account/@*:id[normalize-space(.)]">
              <Id>
                <xsl:value-of select="(mcp:parse-xml((*[@key='payload'])[1])//*:Account/@*:id)[1]"/>
              </Id>
            </xsl:if>
          </Object>
          <xsl:apply-templates select="." mode="outcome"/>
        </View>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'update': records where operation = UpdateAccount. Writes an Update event (TypeId from operation) from
       payload//Account/@id, payload//Result. -->
  <xsl:template match="node()" mode="event_type_update">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Update>
          <After>
            <Object>
              <Type>Account</Type>
              <xsl:if test="mcp:parse-xml((*[@key='payload'])[1])//*:Account/@*:id[normalize-space(.)]">
                <Id>
                  <xsl:value-of select="(mcp:parse-xml((*[@key='payload'])[1])//*:Account/@*:id)[1]"/>
                </Id>
              </xsl:if>
            </Object>
          </After>
          <xsl:apply-templates select="." mode="outcome"/>
        </Update>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'login', 'read', 'update' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="*[@key='time'][normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((*[@key='time'])[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'login', 'read', 'update' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>API Gateway</Name>
        <Environment>Eval</Environment>
      </System>
      <xsl:if test="*[@key='service'][normalize-space(.)]">
        <Generator>
          <xsl:value-of select="(*[@key='service'])[1]"/>
        </Generator>
      </xsl:if>
      <Device>
        <HostName>apigw01</HostName>
      </Device>
      <xsl:if test="*[@key='client_ip'][normalize-space(.)]">
        <Client>
          <IPAddress>
            <xsl:value-of select="(*[@key='client_ip'])[1]"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="mcp:parse-xml((*[@key='payload'])[1])//*:User[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(mcp:parse-xml((*[@key='payload'])[1])//*:User)[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'login', 'read', 'update' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="*[@key='operation'][normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="(*[@key='operation'])[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- outcome: shared by the rules 'login', 'read', 'update' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:if test="exists($payload_result_to_success?((mcp:parse-xml((*[@key='payload'])[1])//*:Result)[1]))">
      <Outcome>
        <Success>
          <xsl:value-of select="$payload_result_to_success?((mcp:parse-xml((*[@key='payload'])[1])//*:Result)[1])[1]"/>
        </Success>
      </Outcome>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
