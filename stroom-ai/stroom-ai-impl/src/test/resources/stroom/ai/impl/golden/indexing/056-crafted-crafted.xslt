<?xml version="1.1" encoding="UTF-8"?>
<xsl:stylesheet xmlns="records:2" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xpath-default-namespace="event-logging:3" version="2.0">
  <xsl:template match="/Events">
    <records xsi:schemaLocation="records:2 file://records-v2.0.xsd" version="2.0">
      <xsl:apply-templates select="Event"/>
    </records>
  </xsl:template>
  <!-- Each Event: one record for the index acme-v1 -->
  <xsl:template match="Event">
    <record>
      <data name="StreamId" value="{@StreamId}"/>
      <data name="EventId" value="{@EventId}"/>
      <data name="@timestamp" value="{EventTime/TimeCreated}"/>
      <data name="user.name" value="{EventSource/User/Id}"/>
      <data name="user.domain" value="{EventSource/User/Domain}"/>
      <data name="source.ip" value="{EventSource/Client/IPAddress}"/>
      <data name="source.port" value="{EventSource/Client/Port}"/>
      <data name="http.request.method" value="{EventDetail/*/Resource/HTTPMethod}"/>
      <data name="http.request.body.bytes" value="{EventDetail/*/Resource/InboundSize}"/>
      <data name="event.outcome" value="{EventDetail/*/Outcome/Success ! (if (. = 'false') then 'failure' else 'success')}"/>
      <data name="acme.ticket" value="{EventDetail/Data[@Name='ticket']/@Value}"/>
      <xsl:for-each select="EventDetail/Data[@Name=&quot;port&quot;]/@Value">
        <data name="acme.ports" value="{.}"/>
      </xsl:for-each>
      <data name="message" value="{EventDetail/Description}"/>
    </record>
  </xsl:template>
</xsl:stylesheet>
