<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="records:2" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="2.0">
  <xsl:template match="/Events">
    <records xsi:schemaLocation="records:2 file://records-v2.0.xsd" version="2.0">
      <xsl:variable name="event" select="Event"/>
      <xsl:apply-templates select="$event"/>
    </records>
  </xsl:template>
  <!-- Each Event: one record for the index acme-v1 -->
  <xsl:template match="Event">
    <record>
      <xsl:variable name="stream_id" select="@StreamId"/>
      <data name="StreamId" value="{$stream_id}"/>
      <xsl:variable name="event_id" select="@EventId"/>
      <data name="EventId" value="{$event_id}"/>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:apply-templates select="." mode="event_detail"/>
    </record>
  </xsl:template>
  <!-- event_time: @timestamp from EventTime/TimeCreated -->
  <xsl:template match="Event" mode="event_time">
    <xsl:variable name="timestamp" select="EventTime/TimeCreated"/>
    <data name="@timestamp" value="{$timestamp}"/>
  </xsl:template>
  <!-- event_source: user.name from EventSource/User/Id; user.domain from EventSource/User/Domain; source.ip from
       EventSource/Client/IPAddress; source.port from EventSource/Client/Port -->
  <xsl:template match="Event" mode="event_source">
    <xsl:variable name="user_name" select="EventSource/User/Id"/>
    <data name="user.name" value="{$user_name}"/>
    <xsl:variable name="user_domain" select="EventSource/User/Domain"/>
    <data name="user.domain" value="{$user_domain}"/>
    <xsl:variable name="source_ip" select="EventSource/Client/IPAddress"/>
    <data name="source.ip" value="{$source_ip}"/>
    <xsl:variable name="source_port" select="EventSource/Client/Port"/>
    <data name="source.port" value="{$source_port}"/>
  </xsl:template>
  <!-- event_detail: http.request.method from EventDetail/*/Resource/HTTPMethod; http.request.body.bytes from
       EventDetail/*/Resource/InboundSize; event.outcome from EventDetail/*/Outcome/Success; acme.ticket from
       EventDetail/Data[@Name='ticket']/@Value; acme.ports from EventDetail/Data[@Name="port"]/@Value; message
       from EventDetail/Description -->
  <xsl:template match="Event" mode="event_detail">
    <xsl:variable name="http_request_method" select="EventDetail/*/Resource/HTTPMethod"/>
    <data name="http.request.method" value="{$http_request_method}"/>
    <xsl:variable name="http_request_body_bytes" select="EventDetail/*/Resource/InboundSize"/>
    <data name="http.request.body.bytes" value="{$http_request_body_bytes}"/>
    <xsl:variable name="event_outcome" select="EventDetail/*/Outcome/Success"/>
    <data name="event.outcome" value="{$event_outcome ! (if (. = 'false') then 'failure' else 'success')}"/>
    <xsl:variable name="acme_ticket" select="EventDetail/Data[@Name='ticket']/@Value"/>
    <data name="acme.ticket" value="{$acme_ticket}"/>
    <xsl:variable name="acme_ports" select="EventDetail/Data[@Name=&quot;port&quot;]/@Value"/>
    <xsl:for-each select="$acme_ports">
      <data name="acme.ports" value="{.}"/>
    </xsl:for-each>
    <xsl:variable name="message" select="EventDetail/Description"/>
    <data name="message" value="{$message}"/>
  </xsl:template>
</xsl:stylesheet>
