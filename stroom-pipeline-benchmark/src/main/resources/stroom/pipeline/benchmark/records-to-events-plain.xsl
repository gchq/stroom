<?xml version="1.0" encoding="UTF-8" ?>
<!-- UK Crown Copyright © 2016 -->
<!--
  A copy of records-to-events.xsl with the one Stroom extension function call, stroom:format-date,
  replaced by a plain concat of the same two fields.

  SaxonReuseBenchmark drives Saxon directly rather than through a Stroom pipeline, so Stroom's
  extension functions are not registered and the original stylesheet will not compile. Removing the
  single call is also what makes the benchmark measure what it is meant to: Saxon's per-transform
  machinery, with none of the format-date parsing cost mixed in. The element count and output shape
  are otherwise unchanged, so the transform does the same amount of work per record.
-->
<xsl:stylesheet
  xmlns="event-logging:3"
  xpath-default-namespace="records:2"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  version="2.0">

   <xsl:template match="records">
      <Events
        xsi:schemaLocation="event-logging:3 file://event-logging-v3.0.0.xsd"
        Version="3.0.0">
         <xsl:apply-templates/>
      </Events>
   </xsl:template>

   <xsl:template match="record">
      <xsl:variable name="user" select="data[@name='User']/@value"/>
     <Event>
        <xsl:call-template name="header"/>
        <EventDetail>
           <TypeId>XML Event</TypeId>
           <Description><xsl:value-of select="data[@name='Message']/@value"/></Description>
           <Authenticate>
              <Action>Logon</Action>
              <LogonType>Interactive</LogonType>
              <User>
                 <Id>user1</Id>
              </User>
              <Outcome>
                 <Success>true</Success>
              </Outcome>
              <Data Name="FileNo">
                <xsl:attribute name="Value" select="data[@name='FileNo']/@value"/>
              </Data>
              <Data Name="LineNo">
                <xsl:attribute name="Value" select="data[@name='LineNo']/@value"/>
              </Data>
           </Authenticate>
        </EventDetail>
     </Event>
   </xsl:template>

   <xsl:template name="header">
      <xsl:variable name="date" select="data[@name='Date']/@value"/>
      <xsl:variable name="time" select="data[@name='Time']/@value"/>
      <xsl:variable name="dateTime" select="concat($date, $time)"/>
      <xsl:variable name="formattedDateTime" select="$dateTime"/>
      <xsl:variable name="user" select="data[@name='User']/@value"/>

      <EventTime>
         <TimeCreated>
         	<xsl:value-of select="$formattedDateTime"/>
         </TimeCreated>
      </EventTime>
      <EventSource>
         <System>
            <Name>Test</Name>
            <Environment>Test</Environment>
         </System>
         <Generator>CSV</Generator>
         <Device>
            <IPAddress>1.1.1.1</IPAddress>
            <MACAddress>00-00-00-00-00-00</MACAddress>
            <Location>
               <Country>UK</Country>
               <Site>Site001</Site>
               <Building>Main</Building>
               <Floor>1</Floor>
               <Room>1</Room>
               <Rack>1</Rack>
               <Position>1</Position>
            </Location>
         </Device>

         <User>
            <Id><xsl:value-of select="$user"/></Id>
         </User>
      </EventSource>
   </xsl:template>
</xsl:stylesheet>
