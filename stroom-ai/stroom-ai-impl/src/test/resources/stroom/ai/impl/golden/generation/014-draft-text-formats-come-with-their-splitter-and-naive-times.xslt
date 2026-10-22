<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="data[@name='action']/@value = 'accept'">
        <xsl:apply-templates select="." mode="event_type_permitted"/>
      </xsl:when>
      <xsl:when test="data[@name='action']/@value = 'deny'">
        <xsl:apply-templates select="." mode="event_type_denied"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="event_type_other"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'permitted': records where action = accept. Writes a Network/Permit event (TypeId from action) from srcip,
       dstip, dstport, logid. -->
  <xsl:template match="node()" mode="event_type_permitted">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="logid" select="data[@name='logid']/@value[normalize-space(.)]"/>
      <xsl:if test="normalize-space(data[@name='action']/@value) or normalize-space(data[@name='srcip']/@value) or normalize-space(data[@name='dstip']/@value) or normalize-space(data[@name='dstport']/@value) or $logid">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:if test="normalize-space(data[@name='srcip']/@value) or normalize-space(data[@name='dstip']/@value) or normalize-space(data[@name='dstport']/@value) or $logid">
            <Network>
              <Permit>
                <xsl:apply-templates select="." mode="source"/>
                <xsl:apply-templates select="." mode="destination"/>
                <xsl:sequence select="mcp:data('logid', $logid)"/>
              </Permit>
            </Network>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'denied': records where action = deny. Writes a Network/Deny event (TypeId from action) from srcip, dstip,
       dstport, logid. -->
  <xsl:template match="node()" mode="event_type_denied">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="logid" select="data[@name='logid']/@value[normalize-space(.)]"/>
      <xsl:if test="normalize-space(data[@name='action']/@value) or normalize-space(data[@name='srcip']/@value) or normalize-space(data[@name='dstip']/@value) or normalize-space(data[@name='dstport']/@value) or $logid">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:if test="normalize-space(data[@name='srcip']/@value) or normalize-space(data[@name='dstip']/@value) or normalize-space(data[@name='dstport']/@value) or $logid">
            <Network>
              <Deny>
                <xsl:apply-templates select="." mode="source"/>
                <xsl:apply-templates select="." mode="destination"/>
                <xsl:sequence select="mcp:data('logid', $logid)"/>
              </Deny>
            </Network>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from action) from logid. -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='action']/@value)) then data[@name='action']/@value else 'other'"/>
        </TypeId>
        <xsl:if test="normalize-space(data[@name='logid']/@value)">
          <Unknown>
            <Data Name="logid" Value="{data[@name='logid']/@value}"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'permitted', 'denied', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="exists(concat(data[@name='date']/@value, ' ', data[@name='time']/@value)[normalize-space(.)])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(concat(data[@name='date']/@value, ' ', data[@name='time']/@value)[1], 'yyyy-MM-dd HH:mm:ss', 'UTC')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'permitted', 'denied', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>FortiOS</Name>
        <Environment>TODO e.g. Prod</Environment>
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
      <xsl:variable name="dstport" select="data[@name='dstport']/@value[normalize-space(.)]"/>
      <xsl:variable name="dstip" select="data[@name='dstip']/@value[normalize-space(.)]"/>
      <xsl:if test="$dstip or $dstport">
        <Server>
          <xsl:if test="$dstip">
            <IPAddress>
              <xsl:value-of select="$dstip"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$dstport">
            <Port>
              <xsl:value-of select="$dstport"/>
            </Port>
          </xsl:if>
        </Server>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'permitted', 'denied' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(data[@name='action']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='action']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- source: shared by the rules 'permitted', 'denied' -->
  <xsl:template match="node()" mode="source">
    <xsl:if test="normalize-space(data[@name='srcip']/@value)">
      <Source>
        <Device>
          <IPAddress>
            <xsl:value-of select="data[@name='srcip']/@value"/>
          </IPAddress>
        </Device>
      </Source>
    </xsl:if>
  </xsl:template>
  <!-- destination: shared by the rules 'permitted', 'denied' -->
  <xsl:template match="node()" mode="destination">
    <xsl:variable name="dstport" select="data[@name='dstport']/@value[normalize-space(.)]"/>
    <xsl:variable name="dstip" select="data[@name='dstip']/@value[normalize-space(.)]"/>
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
