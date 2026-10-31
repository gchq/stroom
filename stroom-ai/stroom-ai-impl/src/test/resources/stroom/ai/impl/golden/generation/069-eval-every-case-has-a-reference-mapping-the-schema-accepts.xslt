<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="data[@name='type']/@value = 'TRAFFIC'">
        <xsl:apply-templates select="." mode="event_type_session"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('type=', string((normalize-space(data[@name='type']/@value))[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'session': records where type = TRAFFIC. Writes a Network/Open event from src, sport, dst, dport, rule,
       app, session_end_reason, action. -->
  <xsl:template match="node()" mode="event_type_session">
    <Event>
      <xsl:if test="normalize-space(data[@name='generated_time']/@value)">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(data[@name='generated_time']/@value[1], 'yyyy/MM/dd HH:mm:ss', 'UTC')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <xsl:variable name="src" select="data[@name='src']/@value[normalize-space(.)]"/>
      <EventSource>
        <System>
          <Name>Palo Alto Firewall</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>PAN-OS</Generator>
        <xsl:if test="normalize-space(data[@name='host']/@value)">
          <Device>
            <HostName>
              <xsl:value-of select="data[@name='host']/@value"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="$src">
          <Client>
            <IPAddress>
              <xsl:value-of select="$src"/>
            </IPAddress>
          </Client>
        </xsl:if>
        <xsl:if test="normalize-space(data[@name='srcuser']/@value)">
          <User>
            <Id>
              <xsl:value-of select="replace(replace(data[@name='srcuser']/@value[1], '^[^\\]*\\', ''), '@.*$', '')"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>
          <xsl:value-of select="if (exists(concat(data[@name='type']/@value, '-', data[@name='subtype']/@value)[normalize-space(.)])) then concat(data[@name='type']/@value, '-', data[@name='subtype']/@value) else 'session'"/>
        </TypeId>
        <Network>
          <Open>
            <xsl:variable name="sport" select="data[@name='sport']/@value[normalize-space(.)]"/>
            <xsl:if test="$src or $sport">
              <Source>
                <xsl:if test="$src">
                  <Device>
                    <IPAddress>
                      <xsl:value-of select="$src"/>
                    </IPAddress>
                  </Device>
                </xsl:if>
                <xsl:if test="$sport">
                  <Port>
                    <xsl:value-of select="$sport"/>
                  </Port>
                </xsl:if>
              </Source>
            </xsl:if>
            <xsl:variable name="dport" select="data[@name='dport']/@value[normalize-space(.)]"/>
            <xsl:variable name="dst" select="data[@name='dst']/@value[normalize-space(.)]"/>
            <xsl:if test="$dst or $dport">
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
              </Destination>
            </xsl:if>
            <Outcome>
              <Permitted>
                <xsl:value-of select="if (data[@name='action']/@value = 'allow') then 'true' else 'false'"/>
              </Permitted>
            </Outcome>
            <xsl:sequence select="mcp:data('rule', data[@name='rule']/@value)"/>
            <xsl:sequence select="mcp:data('app', data[@name='app']/@value)"/>
            <xsl:sequence select="mcp:data('end_reason', data[@name='session_end_reason']/@value)"/>
          </Open>
        </Network>
      </EventDetail>
    </Event>
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
