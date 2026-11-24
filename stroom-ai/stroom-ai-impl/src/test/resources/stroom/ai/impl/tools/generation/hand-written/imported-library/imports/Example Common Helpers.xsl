<?xml version="1.1" encoding="UTF-8"?>
<!-- A helper library several hand-written translations import: shared templates and functions. -->
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:helper="urn:example:helper" xpath-default-namespace="records:2" version="2.0">

  <!-- The EventSource every database audit event has. -->
  <xsl:template name="database-source">
    <xsl:param name="host"/>
    <xsl:param name="user"/>
    <EventSource>
      <System>
        <Name>Example Database</Name>
        <Environment>Production</Environment>
      </System>
      <Generator>audit-trail</Generator>
      <Device>
        <HostName><xsl:value-of select="$host"/></HostName>
      </Device>
      <User>
        <Id><xsl:value-of select="helper:user-id($user)"/></Id>
      </User>
    </EventSource>
  </xsl:template>

  <!-- A user id as every translation writes it: lower case. -->
  <xsl:function name="helper:user-id">
    <xsl:param name="value"/>
    <xsl:sequence select="lower-case(normalize-space($value))"/>
  </xsl:function>
</xsl:stylesheet>
