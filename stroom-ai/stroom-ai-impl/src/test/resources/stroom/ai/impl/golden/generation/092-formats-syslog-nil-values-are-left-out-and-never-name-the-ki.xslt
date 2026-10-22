<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:choose>
      <xsl:when test="data[@name='action']/@value = 'allow'">
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
  <!-- Rule 'permitted': records where action = allow. Writes a Network/Permit event (TypeId from action) from src,
       proto, dst, dport, pri, pid, msgid, structured, action_2, src_2, ... (action_2, src_2, dst_2, dport_2,
       proto_2, user_2 extracted from message). -->
  <xsl:template match="node()" mode="event_type_permitted">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Network>
          <Permit>
            <Source>
              <xsl:apply-templates select="." mode="device"/>
              <TransportProtocol>
                <xsl:value-of select="if (data[@name='proto']/@value = 'tcp') then 'TCP' else if (data[@name='proto']/@value = 'udp') then 'UDP' else 'Other'"/>
              </TransportProtocol>
              <xsl:variable name="src_2_parts" select="analyze-string(string(data[@name='message']/@value[1]), '(?:^|[\s,;])src=([^\s,;&quot;]*)')"/>
              <xsl:sequence select="mcp:data('src_2', $src_2_parts/fn:match//fn:group[@nr=1])"/>
            </Source>
            <xsl:apply-templates select="." mode="destination"/>
            <xsl:apply-templates select="." mode="data_pri_to_user_2"/>
          </Permit>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'denied': records where action = deny. Writes a Network/Deny event (TypeId from action) from src, proto,
       dst, dport, pri, pid, msgid, structured, action_2, src_2, ... (action_2, src_2, dst_2, dport_2, proto_2,
       user_2 extracted from message). -->
  <xsl:template match="node()" mode="event_type_denied">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Network>
          <Deny>
            <Source>
              <xsl:apply-templates select="." mode="device"/>
              <TransportProtocol>
                <xsl:value-of select="if (data[@name='proto']/@value = 'tcp') then 'TCP' else 'Other'"/>
              </TransportProtocol>
              <xsl:variable name="src_2_parts" select="analyze-string(string(data[@name='message']/@value[1]), '(?:^|[\s,;])src=([^\s,;&quot;]*)')"/>
              <xsl:sequence select="mcp:data('src_2', $src_2_parts/fn:match//fn:group[@nr=1])"/>
            </Source>
            <xsl:apply-templates select="." mode="destination"/>
            <xsl:apply-templates select="." mode="data_pri_to_user_2"/>
          </Deny>
        </Network>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'other': records no other rule matches. Writes an Unknown event (TypeId from action) from pri, pid, msgid,
       structured, proto, action_2, src_2, dst_2, dport_2, proto_2, ... (action_2, src_2, dst_2, dport_2,
       proto_2, user_2 extracted from message). -->
  <xsl:template match="node()" mode="event_type_other">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (data[@name='action']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]) then data[@name='action']/@value else 'other'"/>
        </TypeId>
        <xsl:variable name="message" select="data[@name='message']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
        <xsl:variable name="user_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])user=([^\s,;&quot;]*)')"/>
        <xsl:variable name="proto_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])proto=([^\s,;&quot;]*)')"/>
        <xsl:variable name="dport_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])dport=([^\s,;&quot;]*)')"/>
        <xsl:variable name="dst_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])dst=([^\s,;&quot;]*)')"/>
        <xsl:variable name="src_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])src=([^\s,;&quot;]*)')"/>
        <xsl:variable name="action_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])action=([^\s,;&quot;]*)')"/>
        <xsl:if test="data[@name='pri']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))] or data[@name='pid']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))] or data[@name='msgid']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))] or data[@name='structured']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))] or data[@name='proto']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))] or $action_2_parts/fn:match//fn:group[@nr=1][normalize-space(.)][not(normalize-space(.) = ('-'))] or $src_2_parts/fn:match//fn:group[@nr=1][normalize-space(.)][not(normalize-space(.) = ('-'))] or $dst_2_parts/fn:match//fn:group[@nr=1][normalize-space(.)][not(normalize-space(.) = ('-'))] or $dport_2_parts/fn:match//fn:group[@nr=1][normalize-space(.)][not(normalize-space(.) = ('-'))] or $proto_2_parts/fn:match//fn:group[@nr=1][normalize-space(.)][not(normalize-space(.) = ('-'))] or $user_2_parts/fn:match//fn:group[@nr=1][normalize-space(.)][not(normalize-space(.) = ('-'))]">
          <Unknown>
            <xsl:sequence select="mcp:data('pri', data[@name='pri']/@value)"/>
            <xsl:sequence select="mcp:data('pid', data[@name='pid']/@value)"/>
            <xsl:sequence select="mcp:data('msgid', data[@name='msgid']/@value)"/>
            <xsl:sequence select="mcp:data('structured', data[@name='structured']/@value)"/>
            <xsl:sequence select="mcp:data('proto', data[@name='proto']/@value)"/>
            <xsl:sequence select="mcp:data('action_2', $action_2_parts/fn:match//fn:group[@nr=1])"/>
            <xsl:sequence select="mcp:data('src_2', $src_2_parts/fn:match//fn:group[@nr=1])"/>
            <xsl:sequence select="mcp:data('dst_2', $dst_2_parts/fn:match//fn:group[@nr=1])"/>
            <xsl:sequence select="mcp:data('dport_2', $dport_2_parts/fn:match//fn:group[@nr=1])"/>
            <xsl:sequence select="mcp:data('proto_2', $proto_2_parts/fn:match//fn:group[@nr=1])"/>
            <xsl:sequence select="mcp:data('user_2', $user_2_parts/fn:match//fn:group[@nr=1])"/>
          </Unknown>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'permitted', 'denied', 'other' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='time']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd''T''HH:mm:ss.SSSX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'permitted', 'denied', 'other' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>TODO system name</Name>
        <Environment>TODO e.g. Prod</Environment>
      </System>
      <xsl:if test="data[@name='app']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
        <Generator>
          <xsl:value-of select="data[@name='app']/@value"/>
        </Generator>
      </xsl:if>
      <xsl:if test="data[@name='host']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
        <Device>
          <HostName>
            <xsl:value-of select="data[@name='host']/@value"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:if test="data[@name='src']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
        <Client>
          <IPAddress>
            <xsl:value-of select="data[@name='src']/@value"/>
          </IPAddress>
        </Client>
      </xsl:if>
      <xsl:variable name="dport" select="data[@name='dport']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
      <xsl:variable name="dst" select="data[@name='dst']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
      <xsl:if test="$dst or $dport">
        <Server>
          <xsl:if test="$dst">
            <IPAddress>
              <xsl:value-of select="$dst"/>
            </IPAddress>
          </xsl:if>
          <xsl:if test="$dport">
            <Port>
              <xsl:value-of select="$dport"/>
            </Port>
          </xsl:if>
        </Server>
      </xsl:if>
      <xsl:if test="data[@name='user']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
        <User>
          <Id>
            <xsl:value-of select="data[@name='user']/@value"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'permitted', 'denied' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="data[@name='action']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
      <TypeId>
        <xsl:value-of select="data[@name='action']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- device: shared by the rules 'permitted', 'denied' -->
  <xsl:template match="node()" mode="device">
    <xsl:if test="data[@name='src']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]">
      <Device>
        <IPAddress>
          <xsl:value-of select="data[@name='src']/@value"/>
        </IPAddress>
      </Device>
    </xsl:if>
  </xsl:template>
  <!-- destination: shared by the rules 'permitted', 'denied' -->
  <xsl:template match="node()" mode="destination">
    <xsl:variable name="dst_2_parts" select="analyze-string(string(data[@name='message']/@value[1]), '(?:^|[\s,;])dst=([^\s,;&quot;]*)')"/>
    <xsl:variable name="dport" select="data[@name='dport']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
    <xsl:variable name="dst" select="data[@name='dst']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
    <xsl:if test="$dst or $dport or $dst_2_parts/fn:match//fn:group[@nr=1][normalize-space(.)][not(normalize-space(.) = ('-'))]">
      <Destination>
        <xsl:if test="$dst">
          <Device>
            <IPAddress>
              <xsl:value-of select="$dst"/>
            </IPAddress>
          </Device>
        </xsl:if>
        <xsl:if test="$dport">
          <Port>
            <xsl:value-of select="$dport"/>
          </Port>
        </xsl:if>
        <xsl:sequence select="mcp:data('dst_2', $dst_2_parts/fn:match//fn:group[@nr=1])"/>
      </Destination>
    </xsl:if>
  </xsl:template>
  <!-- data_pri_to_user_2: shared by the rules 'permitted', 'denied' -->
  <xsl:template match="node()" mode="data_pri_to_user_2">
    <xsl:sequence select="mcp:data('pri', data[@name='pri']/@value)"/>
    <xsl:sequence select="mcp:data('pid', data[@name='pid']/@value)"/>
    <xsl:sequence select="mcp:data('msgid', data[@name='msgid']/@value)"/>
    <xsl:sequence select="mcp:data('structured', data[@name='structured']/@value)"/>
    <xsl:variable name="message" select="data[@name='message']/@value[normalize-space(.)][not(normalize-space(.) = ('-'))]"/>
    <xsl:variable name="action_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])action=([^\s,;&quot;]*)')"/>
    <xsl:sequence select="mcp:data('action_2', $action_2_parts/fn:match//fn:group[@nr=1])"/>
    <xsl:variable name="dport_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])dport=([^\s,;&quot;]*)')"/>
    <xsl:sequence select="mcp:data('dport_2', $dport_2_parts/fn:match//fn:group[@nr=1])"/>
    <xsl:variable name="proto_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])proto=([^\s,;&quot;]*)')"/>
    <xsl:sequence select="mcp:data('proto_2', $proto_2_parts/fn:match//fn:group[@nr=1])"/>
    <xsl:variable name="user_2_parts" select="analyze-string(string($message[1]), '(?:^|[\s,;])user=([^\s,;&quot;]*)')"/>
    <xsl:sequence select="mcp:data('user_2', $user_2_parts/fn:match//fn:group[@nr=1])"/>
  </xsl:template>
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank, nor -) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)][not(normalize-space(.) = ('-'))])">
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
