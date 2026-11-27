<?xml version="1.1" encoding="UTF-8"?>
<!-- A hand-written translation no reader of the tools' reads back: every event made by a function of its own, called
     from the root template over the records (no template per record). -->
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:local="local" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="local" version="3.0">

  <xsl:template match="/">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd" Version="4.1.0">
      <xsl:sequence select="array/map[string[@key='action'] = ('login', 'logout')] ! local:event(.)"/>
    </Events>
  </xsl:template>

  <!-- A login or logout, its action from the record's. -->
  <xsl:function name="local:event">
    <xsl:param name="record"/>
    <Event>
      <EventTime>
        <TimeCreated><xsl:value-of select="$record/string[@key='ts']"/></TimeCreated>
      </EventTime>
      <EventSource>
        <System>
          <Name>Example VPN</Name>
          <Environment>Production</Environment>
        </System>
        <Generator>vpnd</Generator>
        <Device>
          <HostName><xsl:value-of select="$record/string[@key='host']"/></HostName>
        </Device>
        <User>
          <Id><xsl:value-of select="$record/string[@key='user']"/></Id>
        </User>
      </EventSource>
      <EventDetail>
        <TypeId><xsl:value-of select="'vpn-' || $record/string[@key='action']"/></TypeId>
        <Authenticate>
          <Action><xsl:value-of select="if ($record/string[@key='action'] = 'login') then 'Logon' else 'Logoff'"/></Action>
          <User>
            <Id><xsl:value-of select="$record/string[@key='user']"/></Id>
          </User>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:function>
</xsl:stylesheet>
