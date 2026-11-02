<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="type" select="data[@name='type']/@value[normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$type = 'traffic'">
        <xsl:apply-templates select="." mode="event_type_traffic"/>
      </xsl:when>
      <xsl:when test="$type = 'event' and data[@name='subtype']/@value = 'system'">
        <xsl:apply-templates select="." mode="event_type_admin_login"/>
      </xsl:when>
      <xsl:when test="$type = 'utm'">
        <xsl:apply-templates select="." mode="event_type_ips"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('type=', string(($type)[1])), concat('subtype=', string((normalize-space(data[@name='subtype']/@value))[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'traffic': records where type = traffic. Writes a Network/Open event (TypeId from logid) from srcip,
       srcport, dstip, dstport, service, action. -->
  <xsl:template match="node()" mode="event_type_traffic">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='logid']/@value)) then data[@name='logid']/@value else 'traffic'"/>
        </TypeId>
        <xsl:variable name="action" select="data[@name='action']/@value[normalize-space(.)]"/>
        <xsl:variable name="dstport" select="data[@name='dstport']/@value[normalize-space(.)]"/>
        <xsl:variable name="dstip" select="data[@name='dstip']/@value[normalize-space(.)]"/>
        <xsl:variable name="srcport" select="data[@name='srcport']/@value[normalize-space(.)]"/>
        <xsl:variable name="srcip" select="data[@name='srcip']/@value[normalize-space(.)]"/>
        <xsl:if test="$srcip or $srcport or $dstip or $dstport or $action = ('accept', 'deny') or normalize-space(data[@name='service']/@value)">
          <Network>
            <Open>
              <xsl:if test="$srcip or $srcport">
                <Source>
                  <xsl:if test="$srcip">
                    <Device>
                      <IPAddress>
                        <xsl:value-of select="$srcip"/>
                      </IPAddress>
                    </Device>
                  </xsl:if>
                  <xsl:if test="$srcport">
                    <Port>
                      <xsl:value-of select="$srcport"/>
                    </Port>
                  </xsl:if>
                </Source>
              </xsl:if>
              <xsl:if test="$dstip or $dstport">
                <Destination>
                  <xsl:if test="$dstip">
                    <Device>
                      <IPAddress>
                        <xsl:value-of select="$dstip"/>
                      </IPAddress>
                    </Device>
                  </xsl:if>
                  <xsl:if test="$dstport">
                    <Port>
                      <xsl:value-of select="$dstport"/>
                    </Port>
                  </xsl:if>
                </Destination>
              </xsl:if>
              <xsl:if test="$action = ('accept', 'deny')">
                <Outcome>
                  <Permitted>
                    <xsl:value-of select="if ($action = 'accept') then 'true' else 'false'"/>
                  </Permitted>
                </Outcome>
              </xsl:if>
              <xsl:sequence select="mcp:data('service', data[@name='service']/@value)"/>
            </Open>
          </Network>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'admin-login': records where type = event and subtype = system. Writes an Authenticate event (TypeId from
       logid) from user, status, msg. -->
  <xsl:template match="node()" mode="event_type_admin_login">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='logid']/@value)) then data[@name='logid']/@value else 'admin-login'"/>
        </TypeId>
        <xsl:if test="normalize-space(data[@name='msg']/@value)">
          <Description>
            <xsl:value-of select="data[@name='msg']/@value"/>
          </Description>
        </xsl:if>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="normalize-space(data[@name='user']/@value)">
            <User>
              <Id>
                <xsl:value-of select="data[@name='user']/@value"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="data[@name='status']/@value = ('success', 'failed')">
            <Outcome>
              <Success>
                <xsl:value-of select="if (data[@name='status']/@value = 'success') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'ips': records where type = utm. Writes an Alert event (TypeId from logid) from severity, attack, action. -->
  <xsl:template match="node()" mode="event_type_ips">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='logid']/@value)) then data[@name='logid']/@value else 'ips'"/>
        </TypeId>
        <Alert>
          <Type>Other</Type>
          <Severity>
            <xsl:variable name="severity" select="data[@name='severity']/@value[normalize-space(.)]"/>
            <xsl:value-of select="if ($severity = 'critical') then 'Critical' else if ($severity = 'high') then 'Major' else if ($severity = 'medium') then 'Minor' else 'Info'"/>
          </Severity>
          <xsl:if test="normalize-space(data[@name='attack']/@value)">
            <Description>
              <xsl:value-of select="data[@name='attack']/@value"/>
            </Description>
          </xsl:if>
          <xsl:sequence select="mcp:data('action', data[@name='action']/@value)"/>
        </Alert>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'traffic', 'admin-login', 'ips' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="exists(concat(data[@name='date']/@value, 'T', data[@name='time']/@value, data[@name='tz']/@value)[normalize-space(.)])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(concat(data[@name='date']/@value, 'T', data[@name='time']/@value, data[@name='tz']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssXX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'traffic', 'admin-login', 'ips' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>FortiGate</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>FortiOS</Generator>
      <xsl:if test="normalize-space(data[@name='devname']/@value)">
        <Device>
          <HostName>
            <xsl:value-of select="data[@name='devname']/@value"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="normalize-space(data[@name='srcip']/@value)">
        <Client>
          <IPAddress>
            <xsl:value-of select="data[@name='srcip']/@value"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:if test="normalize-space(data[@name='user']/@value)">
        <User>
          <Id>
            <xsl:value-of select="data[@name='user']/@value"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
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
