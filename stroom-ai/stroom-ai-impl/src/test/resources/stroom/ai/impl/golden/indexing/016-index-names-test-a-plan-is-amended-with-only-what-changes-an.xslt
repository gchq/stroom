<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="3.0">
  <xsl:template match="/Events">
    <array xsi:schemaLocation="http://www.w3.org/2005/xpath-functions file://xpath-functions.xsd">
      <xsl:apply-templates select="Event"/>
    </array>
  </xsl:template>
  <!-- Each Event: one document for the index ecs-firewall-v1 -->
  <xsl:template match="Event">
    <map>
      <xsl:if test="@StreamId">
        <number key="StreamId">
          <xsl:value-of select="@StreamId"/>
        </number>
      </xsl:if>
      <xsl:if test="@EventId">
        <number key="EventId">
          <xsl:value-of select="@EventId"/>
        </number>
      </xsl:if>
      <xsl:if test="EventTime/TimeCreated">
        <string key="@timestamp">
          <xsl:value-of select="EventTime/TimeCreated"/>
        </string>
      </xsl:if>
      <xsl:apply-templates select="." mode="observer"/>
      <xsl:apply-templates select="." mode="host"/>
      <xsl:apply-templates select="." mode="source"/>
      <xsl:apply-templates select="." mode="destination"/>
      <xsl:apply-templates select="." mode="event"/>
      <xsl:if test="EventDetail/Description">
        <string key="message">
          <xsl:value-of select="EventDetail/Description"/>
        </string>
      </xsl:if>
      <xsl:apply-templates select="." mode="process"/>
      <xsl:apply-templates select="." mode="firewall"/>
      <xsl:apply-templates select="." mode="acme"/>
    </map>
  </xsl:template>
  <!-- observer: observer.product from EventSource/System/Name -->
  <xsl:template match="Event" mode="observer">
    <xsl:if test="EventSource/System/Name">
      <map key="observer">
        <string key="product">
          <xsl:value-of select="EventSource/System/Name"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- host: host.name from EventSource/Device/HostName -->
  <xsl:template match="Event" mode="host">
    <xsl:if test="EventSource/Device/HostName">
      <map key="host">
        <string key="name">
          <xsl:value-of select="EventSource/Device/HostName"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- source: source.ip from EventDetail/Network/*/Source/Device/IPAddress; source.port from
       EventDetail/Network/*/Source/Port -->
  <xsl:template match="Event" mode="source">
    <xsl:variable name="source_ip" select="EventDetail/Network/*/Source/Device/IPAddress"/>
    <xsl:variable name="source_port" select="EventDetail/Network/*/Source/Port"/>
    <xsl:if test="$source_ip or $source_port">
      <map key="source">
        <xsl:if test="$source_ip">
          <string key="ip">
            <xsl:value-of select="$source_ip"/>
          </string>
        </xsl:if>
        <xsl:if test="$source_port">
          <number key="port">
            <xsl:value-of select="$source_port"/>
          </number>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- destination: destination.ip from EventDetail/Network/*/Destination/Device/IPAddress -->
  <xsl:template match="Event" mode="destination">
    <xsl:if test="EventDetail/Network/*/Destination/Device/IPAddress">
      <map key="destination">
        <string key="ip">
          <xsl:value-of select="EventDetail/Network/*/Destination/Device/IPAddress"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- event: event.code from EventDetail/TypeId; event.action from EventDetail/*/Action; event.outcome from
       EventDetail/*/Outcome/Success -->
  <xsl:template match="Event" mode="event">
    <xsl:variable name="event_code" select="EventDetail/TypeId"/>
    <xsl:variable name="event_action" select="EventDetail/*/Action"/>
    <xsl:variable name="event_outcome" select="EventDetail/*/Outcome/Success"/>
    <xsl:if test="$event_code or $event_action or $event_outcome">
      <map key="event">
        <xsl:if test="$event_code">
          <string key="code">
            <xsl:value-of select="$event_code"/>
          </string>
        </xsl:if>
        <xsl:if test="$event_action">
          <string key="action">
            <xsl:value-of select="$event_action"/>
          </string>
        </xsl:if>
        <xsl:if test="$event_outcome">
          <string key="outcome">
            <xsl:value-of select="$event_outcome ! (if (. = 'false') then 'failure' else 'success')"/>
          </string>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- process: process.command_line from EventDetail/Process/Command -->
  <xsl:template match="Event" mode="process">
    <xsl:if test="EventDetail/Process/Command">
      <map key="process">
        <string key="command_line">
          <xsl:value-of select="EventDetail/Process/Command"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- firewall: firewall.alert.severity from EventDetail/Alert/Severity; firewall.authenticate.user.id from
       EventDetail/Authenticate/User/Id; firewall.process.type from EventDetail/Process/Type;
       firewall.update.configuration.type from EventDetail/Update/After/Configuration/Type;
       firewall.configuration from EventDetail/Update/After/Configuration/Type -->
  <xsl:template match="Event" mode="firewall">
    <xsl:variable name="firewall_alert_severity" select="EventDetail/Alert/Severity"/>
    <xsl:variable name="firewall_authenticate_user_id" select="EventDetail/Authenticate/User/Id"/>
    <xsl:variable name="firewall_process_type" select="EventDetail/Process/Type"/>
    <xsl:variable name="firewall_configuration" select="EventDetail/Update/After/Configuration/Type"/>
    <xsl:if test="$firewall_alert_severity or $firewall_authenticate_user_id or $firewall_process_type or $firewall_configuration">
      <map key="firewall">
        <xsl:if test="$firewall_alert_severity">
          <map key="alert">
            <string key="severity">
              <xsl:value-of select="$firewall_alert_severity"/>
            </string>
          </map>
        </xsl:if>
        <xsl:if test="$firewall_authenticate_user_id">
          <map key="authenticate">
            <map key="user">
              <string key="id">
                <xsl:value-of select="$firewall_authenticate_user_id"/>
              </string>
            </map>
          </map>
        </xsl:if>
        <xsl:if test="$firewall_process_type">
          <map key="process">
            <string key="type">
              <xsl:value-of select="$firewall_process_type"/>
            </string>
          </map>
        </xsl:if>
        <xsl:if test="$firewall_configuration">
          <map key="update">
            <map key="configuration">
              <string key="type">
                <xsl:value-of select="$firewall_configuration"/>
              </string>
            </map>
          </map>
        </xsl:if>
        <xsl:if test="$firewall_configuration">
          <string key="configuration">
            <xsl:value-of select="$firewall_configuration"/>
          </string>
        </xsl:if>
      </map>
    </xsl:if>
  </xsl:template>
  <!-- acme: acme.ticket from EventDetail/Alert/Data[@Name='Ticket']/@Value -->
  <xsl:template match="Event" mode="acme">
    <xsl:if test="EventDetail/Alert/Data[@Name='Ticket']/@Value">
      <map key="acme">
        <string key="ticket">
          <xsl:value-of select="EventDetail/Alert/Data[@Name='Ticket']/@Value"/>
        </string>
      </map>
    </xsl:if>
  </xsl:template>
</xsl:stylesheet>
