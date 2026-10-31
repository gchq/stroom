<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="cef_signature_id" select="data[@name='cef_signature_id']/@value[normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$cef_signature_id = ('1001', '1002')">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="$cef_signature_id = '1003'">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:when test="$cef_signature_id = ('2001', '2002')">
        <xsl:apply-templates select="." mode="event_type_web_request"/>
      </xsl:when>
      <xsl:when test="$cef_signature_id = '3001'">
        <xsl:apply-templates select="." mode="event_type_malware"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('cef_signature_id=', string(($cef_signature_id)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where cef_signature_id in 1001, 1002. Writes an Authenticate event (TypeId from
       cef_signature_id) from suser, outcome. -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
          <xsl:if test="data[@name='outcome']/@value = ('success', 'failure')">
            <Outcome>
              <Success>
                <xsl:value-of select="if (data[@name='outcome']/@value = 'success') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where cef_signature_id = 1003. Writes an Authenticate event (TypeId from
       cef_signature_id) from suser. -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'web request': records where cef_signature_id in 2001, 2002. Writes a Network/Open event (TypeId from
       cef_signature_id) from src, dst, dpt, request, cat, act. -->
  <xsl:template match="node()" mode="event_type_web_request">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="act" select="data[@name='act']/@value[normalize-space(.)]"/>
      <xsl:variable name="cat" select="data[@name='cat']/@value[normalize-space(.)]"/>
      <xsl:variable name="request" select="data[@name='request']/@value[normalize-space(.)]"/>
      <xsl:variable name="dpt" select="data[@name='dpt']/@value[normalize-space(.)]"/>
      <xsl:variable name="dst" select="data[@name='dst']/@value[normalize-space(.)]"/>
      <xsl:variable name="src" select="data[@name='src']/@value[normalize-space(.)]"/>
      <xsl:if test="normalize-space(data[@name='cef_signature_id']/@value) or exists(((data[@name='msg']/@value, data[@name='cef_name']/@value)[normalize-space(.)][1])[normalize-space(.)]) or $src or $dst or $dpt or $request or $cat or $act = ('allow', 'block')">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:apply-templates select="." mode="description"/>
          <xsl:if test="$src or $dst or $dpt or $request or $cat or $act = ('allow', 'block')">
            <Network>
              <Open>
                <xsl:if test="$src">
                  <Source>
                    <Device>
                      <IPAddress>
                        <xsl:value-of select="$src"/>
                      </IPAddress>
                    </Device>
                  </Source>
                </xsl:if>
                <xsl:if test="$dst or $dpt or $request or $cat">
                  <Destination>
                    <xsl:if test="$dst">
                      <Device>
                        <IPAddress>
                          <xsl:value-of select="$dst"/>
                        </IPAddress>
                      </Device>
                    </xsl:if>
                    <xsl:if test="$dpt">
                      <Port>
                        <xsl:value-of select="$dpt"/>
                      </Port>
                    </xsl:if>
                    <xsl:sequence select="mcp:data('url', $request)"/>
                    <xsl:sequence select="mcp:data('category', $cat)"/>
                  </Destination>
                </xsl:if>
                <xsl:if test="$act = ('allow', 'block')">
                  <Outcome>
                    <Permitted>
                      <xsl:value-of select="if ($act = 'allow') then 'true' else 'false'"/>
                    </Permitted>
                  </Outcome>
                </xsl:if>
              </Open>
            </Network>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'malware': records where cef_signature_id = 3001. Writes an Alert event (TypeId from cef_signature_id)
       from fname, fileHash, act. -->
  <xsl:template match="node()" mode="event_type_malware">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <xsl:apply-templates select="." mode="description"/>
        <Alert>
          <Type>Malware</Type>
          <Severity>Major</Severity>
          <xsl:sequence select="mcp:data('file', data[@name='fname']/@value)"/>
          <xsl:sequence select="mcp:data('sha_or_md5', data[@name='fileHash']/@value)"/>
          <xsl:sequence select="mcp:data('action', data[@name='act']/@value)"/>
        </Alert>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff', 'web request', 'malware' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="exists(concat('2026 ', replace(data[@name='time']/@value, '  ', ' '))[normalize-space(.)])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(concat('2026 ', replace(data[@name='time']/@value, '  ', ' '))[1], 'yyyy MMM d HH:mm:ss', 'UTC')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'logoff', 'web request', 'malware' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Secure Web Gateway</Name>
        <Environment>Eval</Environment>
      </System>
      <xsl:if test="normalize-space(data[@name='cef_product']/@value)">
        <Generator>
          <xsl:value-of select="data[@name='cef_product']/@value"/>
        </Generator>
      </xsl:if>
      <xsl:if test="normalize-space(data[@name='host']/@value)">
        <Device>
          <HostName>
            <xsl:value-of select="data[@name='host']/@value"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="normalize-space(data[@name='src']/@value)">
        <Client>
          <IPAddress>
            <xsl:value-of select="data[@name='src']/@value"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="normalize-space(data[@name='suser']/@value)">
        <User>
          <Id>
            <xsl:value-of select="data[@name='suser']/@value"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'logon', 'logoff', 'web request', 'malware' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(data[@name='cef_signature_id']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='cef_signature_id']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- description: shared by the rules 'logon', 'logoff', 'web request', 'malware' -->
  <xsl:template match="node()" mode="description">
    <xsl:if test="exists(((data[@name='msg']/@value, data[@name='cef_name']/@value)[normalize-space(.)][1])[normalize-space(.)])">
      <Description>
        <xsl:value-of select="((data[@name='msg']/@value, data[@name='cef_name']/@value)[normalize-space(.)][1])"/>
      </Description>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:if test="normalize-space(data[@name='suser']/@value)">
      <User>
        <Id>
          <xsl:value-of select="data[@name='suser']/@value"/>
        </Id>
      </User>
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
