<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet
  xmlns="event-logging:3"
  xmlns:stroom="stroom"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
  version="2.0">
  
   <xsl:template match="SomeData">
      <Events 
        xsi:schemaLocation="event-logging:3 file://event-logging-v3.0.0.xsd"
        Version="3.0.0">
         <xsl:apply-templates/>
      </Events>
   </xsl:template>
   <xsl:template match="SomeEvent">
      <xsl:if test="SomeAction = 'OPEN'">
        <xsl:variable name="time" select="stroom:format-date(SomeTime, 'dd/MM/yyyy:HH:mm:ss')"/>
      
         <Event>
            <EventTime>
               <TimeCreated><xsl:value-of select="$time"/></TimeCreated>
            </EventTime>         
	     <EventSource>
				 <System>
					 <Name>Test</Name>
					 <Environment>Test</Environment>
				 </System>
	       <Generator>Very Simple Provider</Generator>
	       <Device>
	         <IPAddress>192.168.0.4</IPAddress>
	         <Location>
	           <Country>UK</Country>
	           <Site><xsl:value-of select="stroom:lookup('CONTEXT', 'Machine', $time)" /></Site>
	           <Building>Main</Building>
	           <Floor>1</Floor>              
	           <Room>1aaa</Room>
	         </Location>           
	       </Device>
	       <User><Id><xsl:value-of select="SomeUser"/></Id></User>
	     </EventSource>
	     <EventDetail>
	       <View>
             <Object>
	          <Type>Document</Type>
	          <Title>UNKNOWN</Title>
	          <Path><xsl:value-of select="SomeFile"/></Path>
             </Object>
	       </View>
	     </EventDetail>
         </Event>
      </xsl:if>
   </xsl:template>
</xsl:stylesheet>
