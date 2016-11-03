<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet
  xmlns="event-logging:3"
  xpath-default-namespace="records:2"
  xmlns:stroom="stroom"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
  version="2.0">

   <!--
      ~ Copyright 2016 Crown Copyright
      ~
      ~ Licensed under the Apache License, Version 2.0 (the "License");
      ~ you may not use this file except in compliance with the License.
      ~ You may obtain a copy of the License at
      ~
      ~     http://www.apache.org/licenses/LICENSE-2.0
      ~
      ~ Unless required by applicable law or agreed to in writing, software
      ~ distributed under the License is distributed on an "AS IS" BASIS,
      ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
      ~ See the License for the specific language governing permissions and
      ~ limitations under the License.
      -->

   <xsl:template match="records">
      <Events
        xsi:schemaLocation="event-logging:3 file://event-logging-v3.0.0.xsd"
        Version="3.0.0">
         <xsl:apply-templates/>
      </Events>
   </xsl:template>
   
   <!-- EG
   <Event>
Date,Time,FileNo,LineNo,User,Message
01/01/2010,00:00:00,1,1,user1,Some message 1
01/01/2010,00:01:00,1,2,user2,Some message 2
01/01/2010,00:02:00,1,3,user3,Some message 3
01/01/2010,00:03:00,1,4,user4,Some message 4
01/01/2010,00:04:00,1,5,user5,Some message 5
01/01/2010,00:05:00,1,6,user6,Some message 6
01/01/2010,00:06:00,1,7,user7,Some message 7
01/01/2010,00:07:00,1,8,user8,Some message 8
01/01/2010,00:08:00,1,9,user9,Some message 9
01/01/2010,00:09:00,1,10,user10,Some message 10
    </Event>
    -->
   <xsl:template match="record">
      <xsl:variable name="user" select="data[@name='User']/@value"/>
     <Event>
        <xsl:call-template name="header"/>
        <EventDetail>
           <Description><xsl:value-of select="data[@name='Message']/@value"/></Description>
           <Authenticate>
              <Action>Logon</Action>
              <LogonType>Interactive</LogonType>
              <User>
                 <Id>
                    <xsl:value-of select="$user"/>
                 </Id>
              </User>
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
      <xsl:variable name="formattedDateTime" select="stroom:format-date($dateTime, 'dd/MM/yyyyHH:mm:ss')"/>
      <xsl:variable name="user" select="data[@name='User']/@value"/>
   
      <EventTime>
         <TimeCreated>
         	<xsl:value-of select="$formattedDateTime"/>
         </TimeCreated>
      </EventTime>
      <EventSource>
         <Generator>CSV</Generator>
         <Device>
            <IPAddress>1.1.1.1</IPAddress>
            <MACAddress>00-00-00-00-00-00</MACAddress>
            
            <xsl:variable name="location" select="stroom:lookup('FILENO_TO_LOCATION_MAP', data[@name='FileNo']/@value, $formattedDateTime)"/>
       		<xsl:if test="$location">
       			<xsl:copy-of select="$location"/>
       		</xsl:if>
            
         </Device>
         
         <User>
            <Id><xsl:value-of select="$user"/></Id>
         </User>
      </EventSource>
   </xsl:template>
</xsl:stylesheet>
