<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="message" select="data[@name='message']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="exists($message[matches(., '^User \S+ logged in')])">
        <xsl:apply-templates select="." mode="event_type_logon"/>
      </xsl:when>
      <xsl:when test="exists($message[matches(., '^User \S+ logged out')])">
        <xsl:apply-templates select="." mode="event_type_logoff"/>
      </xsl:when>
      <xsl:when test="exists($message[matches(., '^Login failed')])">
        <xsl:apply-templates select="." mode="event_type_logon_failed"/>
      </xsl:when>
      <xsl:when test="exists($message[matches(., '^Invoice \d+ created')])">
        <xsl:apply-templates select="." mode="event_type_invoice"/>
      </xsl:when>
      <xsl:when test="data[@name='level']/@value = 'ERROR'">
        <xsl:apply-templates select="." mode="event_type_payment_error"/>
      </xsl:when>
      <xsl:when test="exists($message[matches(., '^Setting ')])">
        <xsl:apply-templates select="." mode="event_type_config"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('message=', string(($message)[1])), concat('level=', string((normalize-space(data[@name='level']/@value))[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'logon': records where message matches ^User \S+ logged in. Writes an Authenticate event (TypeId from
       logger) from user (extracted from message). -->
  <xsl:template match="node()" mode="event_type_logon">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='logger']/@value)) then data[@name='logger']/@value else 'logon'"/>
        </TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logoff': records where message matches ^User \S+ logged out. Writes an Authenticate event (TypeId from
       logger) from user (extracted from message). -->
  <xsl:template match="node()" mode="event_type_logoff">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='logger']/@value)) then data[@name='logger']/@value else 'logoff'"/>
        </TypeId>
        <Authenticate>
          <Action>Logoff</Action>
          <xsl:apply-templates select="." mode="user"/>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'logon-failed': records where message matches ^Login failed. Writes an Authenticate event (TypeId from
       logger) from failed_user, message (failed_user extracted from message). -->
  <xsl:template match="node()" mode="event_type_logon_failed">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='logger']/@value)) then data[@name='logger']/@value else 'logon-failed'"/>
        </TypeId>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:variable name="message" select="data[@name='message']/@value[normalize-space(.)]"/>
          <xsl:variable name="failed_user_parts" select="analyze-string(string($message[1]), '^Login failed for user (\S+) from ([\d.]+).*$')"/>
          <xsl:if test="normalize-space($failed_user_parts/fn:match//fn:group[@nr=1])">
            <User>
              <Id>
                <xsl:value-of select="$failed_user_parts/fn:match//fn:group[@nr=1]"/>
              </Id>
            </User>
          </xsl:if>
          <Outcome>
            <Success>false</Success>
            <xsl:if test="$message">
              <Description>
                <xsl:value-of select="$message"/>
              </Description>
            </xsl:if>
          </Outcome>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'invoice': records where message matches ^Invoice \d+ created. Writes a Create event (TypeId from logger)
       from message. -->
  <xsl:template match="node()" mode="event_type_invoice">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='logger']/@value)) then data[@name='logger']/@value else 'invoice'"/>
        </TypeId>
        <Create>
          <Object>
            <Type>Invoice</Type>
            <xsl:if test="normalize-space(data[@name='message']/@value)">
              <Description>
                <xsl:value-of select="data[@name='message']/@value"/>
              </Description>
            </xsl:if>
          </Object>
        </Create>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'payment-error': records where level = ERROR. Writes an Alert event (TypeId from logger) from message. -->
  <xsl:template match="node()" mode="event_type_payment_error">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='logger']/@value)) then data[@name='logger']/@value else 'payment-error'"/>
        </TypeId>
        <Alert>
          <Type>Error</Type>
          <xsl:if test="normalize-space(data[@name='message']/@value)">
            <Description>
              <xsl:value-of select="data[@name='message']/@value"/>
            </Description>
          </xsl:if>
        </Alert>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'config': records where message matches ^Setting . Writes an Update event (TypeId from logger) from
       setting, old_value, new_value (extracted from message). -->
  <xsl:template match="node()" mode="event_type_config">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (normalize-space(data[@name='logger']/@value)) then data[@name='logger']/@value else 'config'"/>
        </TypeId>
        <Update>
          <xsl:variable name="setting_parts" select="analyze-string(string(data[@name='message']/@value[1]), '^Setting (\S+) changed from (\S+) to (\S+) by (\S+).*$')"/>
          <After>
            <Object>
              <Type>Setting</Type>
              <xsl:if test="normalize-space($setting_parts/fn:match//fn:group[@nr=1])">
                <Id>
                  <xsl:value-of select="$setting_parts/fn:match//fn:group[@nr=1]"/>
                </Id>
              </xsl:if>
            </Object>
          </After>
          <xsl:sequence select="mcp:data('old_value', $setting_parts/fn:match//fn:group[@nr=2])"/>
          <xsl:sequence select="mcp:data('new_value', $setting_parts/fn:match//fn:group[@nr=3])"/>
        </Update>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'logon', 'logoff', 'logon-failed', 'invoice', 'payment-error', 'config' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(data[@name='time']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd HH:mm:ss,SSS', 'UTC')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'logon', 'logoff', 'logon-failed', 'invoice', 'payment-error', 'config' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>Billing Service</Name>
        <Environment>Eval</Environment>
      </System>
      <xsl:if test="normalize-space(data[@name='logger']/@value)">
        <Generator>
          <xsl:value-of select="data[@name='logger']/@value"/>
        </Generator>
      </xsl:if>
      <Device>
        <HostName>billing01</HostName>
      </Device>
      <xsl:variable name="message" select="data[@name='message']/@value[normalize-space(.)]"/>
      <xsl:variable name="setting_parts" select="analyze-string(string($message[1]), '^Setting (\S+) changed from (\S+) to (\S+) by (\S+).*$')"/>
      <xsl:variable name="customer_parts" select="analyze-string(string($message[1]), '^.*?\(customer (\S+)\)[\s\S]*$')"/>
      <xsl:variable name="failed_user_parts" select="analyze-string(string($message[1]), '^Login failed for user (\S+) from ([\d.]+).*$')"/>
      <xsl:variable name="user_parts" select="analyze-string(string($message[1]), '^User (\S+) logged.*$')"/>
      <xsl:if test="exists((($user_parts/fn:match//fn:group[@nr=1], $failed_user_parts/fn:match//fn:group[@nr=1], $customer_parts/fn:match//fn:group[@nr=1], $setting_parts/fn:match//fn:group[@nr=4])[normalize-space(.)][1])[normalize-space(.)])">
        <User>
          <Id>
            <xsl:value-of select="(($user_parts/fn:match//fn:group[@nr=1], $failed_user_parts/fn:match//fn:group[@nr=1], $customer_parts/fn:match//fn:group[@nr=1], $setting_parts/fn:match//fn:group[@nr=4])[normalize-space(.)][1])"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- user: shared by the rules 'logon', 'logoff' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="user_parts" select="analyze-string(string(data[@name='message']/@value[1]), '^User (\S+) logged.*$')"/>
    <xsl:if test="normalize-space($user_parts/fn:match//fn:group[@nr=1])">
      <User>
        <Id>
          <xsl:value-of select="$user_parts/fn:match//fn:group[@nr=1]"/>
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
