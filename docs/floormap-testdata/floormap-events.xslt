<?xml version="1.0" encoding="UTF-8" ?>
<!--
  Events CSV -> reference-data:2, for a FloorMap Event Store (a Plan B TEMPORAL_STATE store).

  Same input and same output shape as floormap-facts.xslt - only the value schema differs, because
  the events query reads a different set of jq paths:

      jq(Value, '.location')     -> Location      (literal coordinates, "x, y")
      jq(Value, '.locationRef')  -> Location Ref  (the key of the fact the event happened at)
      jq(Value, '.type')         -> Type          (the entity's kind; drives its layer and icon)
      jq(Value, '.status')       -> Status
      jq(Value, '.message')      -> Message

  location and locationRef are two SEPARATE fields, split 2026-09-07. One field used to carry both
  and was told apart by shape - two numbers meant a position, anything else a key - which made a
  fact key that looked like two numbers, or contained a comma, impossible to express, and reported
  a malformed coordinate as a missing desk.

  Set exactly one per event. An event may carry either:
    location    - "120.5, 340"   drawn exactly there, and fixed: it will NOT follow the fact if
                                 the fact is later moved in the Editor
    locationRef - "desk-114"     resolved against the facts store at the selected time, so moving
                                 the desk in the Editor moves its occupants, retroactively

  Both set is contradictory data. location wins, because it needs no lookup, and the map reports
  the clash once in the console. A locationRef naming a fact that does not exist at the selected
  time is silently dropped - there is nowhere to draw it. The generated data uses both forms
  deliberately, one entity per form.
-->
<xsl:stylesheet
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns="reference-data:2"
    xmlns:records="records:2"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:fm="urn:floormap-testdata"
    exclude-result-prefixes="records xs fm"
    version="2.0">

  <xsl:output method="xml" indent="yes"/>

  <xsl:function name="fm:str" as="xs:string">
    <xsl:param name="s" as="xs:string"/>
    <xsl:sequence select="concat('&quot;',
                                 replace(replace($s, '\\', '\\\\'), '&quot;', '\\&quot;'),
                                 '&quot;')"/>
  </xsl:function>

  <xsl:template match="records:records">
    <referenceData>
      <xsl:apply-templates select="records:record"/>
    </referenceData>
  </xsl:template>

  <xsl:template match="records:record">
    <xsl:variable name="location" select="string(records:data[@name='location']/@value)"/>
    <xsl:variable name="locationRef" select="string(records:data[@name='locationRef']/@value)"/>
    <xsl:variable name="type" select="string(records:data[@name='type']/@value)"/>
    <xsl:variable name="status" select="string(records:data[@name='status']/@value)"/>
    <xsl:variable name="message" select="string(records:data[@name='message']/@value)"/>

    <xsl:variable name="members" as="xs:string*">
      <xsl:if test="$location != ''">
        <xsl:sequence select="concat('&quot;location&quot;:', fm:str($location))"/>
      </xsl:if>
      <xsl:if test="$locationRef != ''">
        <xsl:sequence select="concat('&quot;locationRef&quot;:', fm:str($locationRef))"/>
      </xsl:if>
      <xsl:if test="$type != ''">
        <xsl:sequence select="concat('&quot;type&quot;:', fm:str($type))"/>
      </xsl:if>
      <xsl:if test="$status != ''">
        <xsl:sequence select="concat('&quot;status&quot;:', fm:str($status))"/>
      </xsl:if>
      <xsl:if test="$message != ''">
        <xsl:sequence select="concat('&quot;message&quot;:', fm:str($message))"/>
      </xsl:if>
    </xsl:variable>

    <temporal-state>
      <!-- The FloorMap Event Store's name, which must match ^[a-z_0-9]+$. -->
      <map><xsl:value-of select="records:data[@name='map']/@value"/></map>
      <key><xsl:value-of select="records:data[@name='key']/@value"/></key>
      <!--
        Explicit per-event time, and for movement data this is not optional: omit it and every
        event in the stream lands at the stream's effective time, i.e. all at one instant, and
        playback has nothing to animate. With neither, ingest fails with
        "Temporal state 'time' is null".
      -->
      <time><xsl:value-of select="records:data[@name='time']/@value"/></time>
      <value><xsl:value-of select="concat('{', string-join($members, ','), '}')"/></value>
    </temporal-state>
  </xsl:template>

</xsl:stylesheet>
