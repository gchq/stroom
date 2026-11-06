<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <!-- Fields of the source no rule reads are logged (WARN, once a stream) to the Error stream: false() to stop it
       (the mapping's log_unmapped_fields) -->
  <xsl:variable name="log_unmapped_fields" as="xs:boolean" select="true()"/>
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="record-no" select="stroom:record-no()"/>
    <xsl:variable name="change" select="data[@name='change']/@value[normalize-space(.)]"/>
    <xsl:sequence select="$record-no[string(.) = '#']"/>
    <xsl:choose>
      <xsl:when test="$change = 'joiner'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_joiner"/>
      </xsl:when>
      <xsl:when test="$change = 'mover'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_mover"/>
      </xsl:when>
      <xsl:when test="$change = 'leaver'">
        <xsl:call-template name="mcp_unread_fields"/>
        <xsl:apply-templates select="." mode="event_type_leaver"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', $record-no, ' (', string-join((concat('change=', string(($change)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- mcp_unread_fields: a field of the record no rule reads, logged once a stream (ignored_fields: left out on
       purpose); called by the rules that translate their records -->
  <xsl:template name="mcp_unread_fields">
    <xsl:if test="$log_unmapped_fields">
      <xsl:for-each select="data[@name][not(@name = ('change', 'changed_by', 'department', 'employee_id', 'employee_name', 'time'))][normalize-space(string(@value))]">
        <xsl:variable name="mcp_unread" select="concat('mcp-unread:', @name)"/>
        <xsl:if test="not(string(stroom:get($mcp_unread)))">
          <xsl:value-of select="stroom:put($mcp_unread, 'y')"/>
          <xsl:sequence select="stroom:log('WARN', concat('No rule reads field ', @name, ' (map it, or list it in ignored_fields to leave it out)'))"/>
        </xsl:if>
      </xsl:for-each>
    </xsl:if>
  </xsl:template>
  <!-- Rule 'joiner': records where change = joiner. Writes a Create event (TypeId from change) from employee_id,
       employee_name, department. -->
  <xsl:template match="node()" mode="event_type_joiner">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:if test="data[@name='change']/@value[normalize-space(.)] or data[@name='employee_id']/@value[normalize-space(.)] or data[@name='employee_name']/@value[normalize-space(.)] or data[@name='department']/@value[normalize-space(.)]">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:if test="data[@name='employee_id']/@value[normalize-space(.)] or data[@name='employee_name']/@value[normalize-space(.)] or data[@name='department']/@value[normalize-space(.)]">
            <Create>
              <xsl:apply-templates select="." mode="user"/>
            </Create>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'mover': records where change = mover. Writes an Update event (TypeId from change) from employee_id,
       employee_name, department. -->
  <xsl:template match="node()" mode="event_type_mover">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:variable name="department" select="data[@name='department']/@value[normalize-space(.)]"/>
      <xsl:variable name="employee_name" select="data[@name='employee_name']/@value[normalize-space(.)]"/>
      <xsl:variable name="employee_id" select="data[@name='employee_id']/@value[normalize-space(.)]"/>
      <xsl:if test="data[@name='change']/@value[normalize-space(.)] or $employee_id or $employee_name or $department">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:if test="$employee_id or $employee_name or $department">
            <Update>
              <After>
                <User>
                  <xsl:if test="$employee_id">
                    <Id>
                      <xsl:value-of select="$employee_id[1]"/>
                    </Id>
                  </xsl:if>
                  <xsl:if test="$employee_name">
                    <Name>
                      <xsl:value-of select="$employee_name[1]"/>
                    </Name>
                  </xsl:if>
                  <xsl:sequence select="mcp:data('department', $department)"/>
                </User>
              </After>
            </Update>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- Rule 'leaver': records where change = leaver. Writes a Delete event (TypeId from change) from employee_id,
       employee_name, department. -->
  <xsl:template match="node()" mode="event_type_leaver">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:if test="data[@name='change']/@value[normalize-space(.)] or data[@name='employee_id']/@value[normalize-space(.)] or data[@name='employee_name']/@value[normalize-space(.)] or data[@name='department']/@value[normalize-space(.)]">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:if test="data[@name='employee_id']/@value[normalize-space(.)] or data[@name='employee_name']/@value[normalize-space(.)] or data[@name='department']/@value[normalize-space(.)]">
            <Delete>
              <xsl:apply-templates select="." mode="user"/>
            </Delete>
          </xsl:if>
        </EventDetail>
      </xsl:if>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'joiner', 'mover', 'leaver' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="data[@name='time']/@value[normalize-space(.)]">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date((data[@name='time']/@value)[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'joiner', 'mover', 'leaver' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>HR System</Name>
        <Environment>Eval</Environment>
      </System>
      <Generator>hr-export</Generator>
      <Device>
        <HostName>hr01.example.org</HostName>
      </Device>
      <xsl:if test="data[@name='changed_by']/@value[normalize-space(.)]">
        <User>
          <Id>
            <xsl:value-of select="(data[@name='changed_by']/@value)[1]"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'joiner', 'mover', 'leaver' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="data[@name='change']/@value[normalize-space(.)]">
      <TypeId>
        <xsl:value-of select="(data[@name='change']/@value)[1]"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'joiner', 'leaver' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="employee_name" select="data[@name='employee_name']/@value[normalize-space(.)]"/>
    <xsl:variable name="employee_id" select="data[@name='employee_id']/@value[normalize-space(.)]"/>
    <xsl:if test="$employee_id or $employee_name or data[@name='department']/@value[normalize-space(.)]">
      <User>
        <xsl:if test="$employee_id">
          <Id>
            <xsl:value-of select="$employee_id[1]"/>
          </Id>
        </xsl:if>
        <xsl:if test="$employee_name">
          <Name>
            <xsl:value-of select="$employee_name[1]"/>
          </Name>
        </xsl:if>
        <xsl:sequence select="mcp:data('department', data[@name='department']/@value)"/>
      </User>
    </xsl:if>
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
