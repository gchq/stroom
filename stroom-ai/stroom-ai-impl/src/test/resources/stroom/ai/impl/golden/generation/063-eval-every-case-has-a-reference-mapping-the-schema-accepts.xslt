<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="normalize-space(*[@key='user'])">
        <xsl:apply-templates select="." mode="event_type_user_access"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_device_access"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'user access': records where user present. Writes an Authenticate event (TypeId from result) from user,
       result, service_protocol (service_protocol extracted from service). -->
  <xsl:template match="node()" mode="event_type_user_access">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(*[@key='result'])) then *[@key='result'] else 'user access'"/>
        </TypeId>
        <Authenticate>
          <Action>Connect</Action>
          <xsl:if test="normalize-space(*[@key='user'])">
            <User>
              <Id>
                <xsl:value-of select="*[@key='user']"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:apply-templates select="." mode="outcome"/>
          <xsl:variable name="service_port_parts" select="analyze-string(string(*[@key='service'][1]), '^(\d+)/(\w+)$')"/>
          <xsl:sequence select="mcp:data('protocol', $service_port_parts/fn:match//fn:group[@nr=2])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'device access': records no other rule matches. Writes an Authenticate event (TypeId from result) from
       result, service_protocol (service_protocol extracted from service). -->
  <xsl:template match="node()" mode="event_type_device_access">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(*[@key='result'])) then *[@key='result'] else 'device access'"/>
        </TypeId>
        <Authenticate>
          <Action>Connect</Action>
          <xsl:if test="exists(replace(upper-case(replace(*[@key='mac'], '[.:-]', '')), '^(..)(..)(..)(..)(..)(..)$', '$1:$2:$3:$4:$5:$6')[normalize-space(.)])">
            <Device>
              <MACAddress>
                <xsl:value-of select="replace(upper-case(replace(*[@key='mac'], '[.:-]', '')), '^(..)(..)(..)(..)(..)(..)$', '$1:$2:$3:$4:$5:$6')"/>
              </MACAddress>
            </Device>
          </xsl:if>
          <xsl:apply-templates select="." mode="outcome"/>
          <xsl:variable name="service_port_parts" select="analyze-string(string(*[@key='service'][1]), '^(\d+)/(\w+)$')"/>
          <xsl:sequence select="mcp:data('protocol', $service_port_parts/fn:match//fn:group[@nr=2])"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'user access', 'device access' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(*[@key='time'])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(*[@key='time'][1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'user access', 'device access' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Network Access Control</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>nac</Generator>
      <xsl:variable name="service_port_parts" select="analyze-string(string(*[@key='service'][1]), '^(\d+)/(\w+)$')"/>
      <xsl:variable name="service_port" select="$service_port_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
      <xsl:variable name="device_mac_address" select="replace(upper-case(replace(*[@key='mac'], '[.:-]', '')), '^(..)(..)(..)(..)(..)(..)$', '$1:$2:$3:$4:$5:$6')[normalize-space(.)]"/>
      <xsl:variable name="ip" select="*[@key='ip'][normalize-space(.)]"/>
      <xsl:variable name="switch" select="*[@key='switch'][normalize-space(.)]"/>
      <xsl:if test="$switch or $ip or exists($device_mac_address) or $service_port or normalize-space(*[@key='port'])">
        <Device>
          <xsl:if test="$switch">
            <HostName>
              <xsl:value-of select="$switch"/>
            </HostName>
          </xsl:if>
          <xsl:if test="$ip">
            <IPAddress>
              <xsl:value-of select="lower-case($ip[1])"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="exists($device_mac_address)">
            <MACAddress>
              <xsl:value-of select="$device_mac_address"/>
            </MACAddress>
          </xsl:if>
          <xsl:if test="$service_port">
            <Port>
              <xsl:value-of select="$service_port"/>
            </Port>
          </xsl:if>
          <xsl:sequence select="mcp:data('switch_port', *[@key='port'])"/>
        </Device>
      </xsl:if>
      <xsl:if test="normalize-space(*[@key='user'])">
        <User>
          <Id>
            <xsl:value-of select="*[@key='user']"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- outcome: shared by the rules 'user access', 'device access' -->
  <xsl:template match="node()" mode="outcome">
    <xsl:if test="*[@key='result'] = ('accept', 'reject')">
      <Outcome>
        <Success>
          <xsl:value-of select="if (*[@key='result'] = 'accept') then 'true' else 'false'"/>
        </Success>
      </Outcome>
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
