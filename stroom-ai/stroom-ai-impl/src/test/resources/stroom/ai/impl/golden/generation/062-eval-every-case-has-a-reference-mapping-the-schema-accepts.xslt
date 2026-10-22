<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs mcp">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:variable name="change" select="data[@name='change']/@value[normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$change = 'joiner'">
        <xsl:apply-templates select="." mode="event_type_joiner"/>
      </xsl:when>
      <xsl:when test="$change = 'mover'">
        <xsl:apply-templates select="." mode="event_type_mover"/>
      </xsl:when>
      <xsl:when test="$change = 'leaver'">
        <xsl:apply-templates select="." mode="event_type_leaver"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('change=', string(($change)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'joiner': records where change = joiner. Writes a Create event (TypeId from change) from employee_id,
       employee_name, department. -->
  <xsl:template match="node()" mode="event_type_joiner">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <xsl:if test="normalize-space(data[@name='change']/@value) or normalize-space(data[@name='employee_id']/@value) or normalize-space(data[@name='employee_name']/@value) or normalize-space(data[@name='department']/@value)">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:if test="normalize-space(data[@name='employee_id']/@value) or normalize-space(data[@name='employee_name']/@value) or normalize-space(data[@name='department']/@value)">
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
      <xsl:if test="normalize-space(data[@name='change']/@value) or $employee_id or $employee_name or $department">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:if test="$employee_id or $employee_name or $department">
            <Update>
              <After>
                <User>
                  <xsl:if test="$employee_id">
                    <Id>
                      <xsl:value-of select="$employee_id"/>
                    </Id>
                  </xsl:if>
                  <xsl:if test="$employee_name">
                    <Name>
                      <xsl:value-of select="$employee_name"/>
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
      <xsl:if test="normalize-space(data[@name='change']/@value) or normalize-space(data[@name='employee_id']/@value) or normalize-space(data[@name='employee_name']/@value) or normalize-space(data[@name='department']/@value)">
        <EventDetail>
          <xsl:apply-templates select="." mode="type_id"/>
          <xsl:if test="normalize-space(data[@name='employee_id']/@value) or normalize-space(data[@name='employee_name']/@value) or normalize-space(data[@name='department']/@value)">
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
    <xsl:if test="normalize-space(data[@name='time']/@value)">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
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
      <xsl:if test="normalize-space(data[@name='changed_by']/@value)">
        <User>
          <Id>
            <xsl:value-of select="data[@name='changed_by']/@value"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'joiner', 'mover', 'leaver' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(data[@name='change']/@value)">
      <TypeId>
        <xsl:value-of select="data[@name='change']/@value"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- user: shared by the rules 'joiner', 'leaver' -->
  <xsl:template match="node()" mode="user">
    <xsl:variable name="employee_name" select="data[@name='employee_name']/@value[normalize-space(.)]"/>
    <xsl:variable name="employee_id" select="data[@name='employee_id']/@value[normalize-space(.)]"/>
    <xsl:if test="$employee_id or $employee_name or normalize-space(data[@name='department']/@value)">
      <User>
        <xsl:if test="$employee_id">
          <Id>
            <xsl:value-of select="$employee_id"/>
          </Id>
        </xsl:if>
        <xsl:if test="$employee_name">
          <Name>
            <xsl:value-of select="$employee_name"/>
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
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
