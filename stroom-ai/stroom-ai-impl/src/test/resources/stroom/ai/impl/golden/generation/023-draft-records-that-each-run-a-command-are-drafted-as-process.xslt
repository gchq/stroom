<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="records">
    <Events Version="3.5.2" xsi:schemaLocation="event-logging:3 file://event-logging-v3.5.2.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_command"/>
    <xsl:for-each select="data[@name][not(@name = ('app', 'command', 'host', 'message', 'pri', 'pwd', 'run_as', 'time', 'tty', 'user'))][normalize-space(string(@value))]">
      <!-- A field no rule reads: logged once a stream (ignored_fields: left out on purpose) -->
      <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
      <xsl:if test="not(string(stroom:get($mcp_unread)))">
        <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
        <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>
  <!-- Rule 'command': every record. Writes a Process event (TypeId Command) from command, pri, tty, pwd, tty_2,
       pwd_2, user_2, command_2 (tty_2, pwd_2, user_2, command_2 extracted from message). -->
  <xsl:template match="node()" mode="event_type_command">
    <Event>
      <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date((data[@name='time']/@value)[1], 'MMM dd HH:mm:ss', 'UTC')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>Web</Name>
          <Environment>Prod</Environment>
        </System>
        <xsl:if test="data[@name='app']/@value[normalize-space(.)]">
          <Generator>
            <xsl:value-of select="(data[@name='app']/@value)[1]"/>
          </Generator>
        </xsl:if>
        <xsl:if test="data[@name='host']/@value[normalize-space(.)]">
          <Device>
            <HostName>
              <xsl:value-of select="(data[@name='host']/@value)[1]"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="data[@name='user']/@value[normalize-space(.)]">
          <User>
            <Id>
              <xsl:value-of select="(data[@name='user']/@value)[1]"/>
            </Id>
          </User>
        </xsl:if>
        <xsl:if test="data[@name='run_as']/@value[normalize-space(.)]">
          <RunAs>
            <Id>
              <xsl:value-of select="(data[@name='run_as']/@value)[1]"/>
            </Id>
          </RunAs>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>Command</TypeId>
        <Process>
          <Action>Execute</Action>
          <Type>Application</Type>
          <xsl:if test="data[@name='command']/@value[normalize-space(.)]">
            <Command>
              <xsl:value-of select="(data[@name='command']/@value)[1]"/>
            </Command>
          </xsl:if>
          <xsl:sequence select="mcp:data('pri', data[@name='pri']/@value)"/>
          <xsl:sequence select="mcp:data('tty', data[@name='tty']/@value)"/>
          <xsl:sequence select="mcp:data('pwd', data[@name='pwd']/@value)"/>
          <xsl:variable name="message" select="data[@name='message']/@value[normalize-space(.)]"/>
          <xsl:variable name="tty_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])TTY=([^\s,;&quot;]*)')"/>
          <xsl:sequence select="mcp:data('tty_2', $tty_2_parts/fn:match//fn:group[@nr=1])"/>
          <xsl:variable name="pwd_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])PWD=([^\s,;&quot;]*)')"/>
          <xsl:sequence select="mcp:data('pwd_2', $pwd_2_parts/fn:match//fn:group[@nr=1])"/>
          <xsl:variable name="user_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])USER=([^\s,;&quot;]*)')"/>
          <xsl:sequence select="mcp:data('user_2', $user_2_parts/fn:match//fn:group[@nr=1])"/>
          <xsl:variable name="command_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])COMMAND=([^\s,;&quot;]*)')"/>
          <xsl:sequence select="mcp:data('command_2', $command_2_parts/fn:match//fn:group[@nr=1])"/>
        </Process>
      </EventDetail>
    </Event>
  </xsl:template>
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value[1]}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
