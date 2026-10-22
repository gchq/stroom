<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="3.0" xpath-default-namespace="records:2" exclude-result-prefixes="stroom xs">
  <xsl:template match="records">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="record" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="." mode="event_type_copy_to_removable_media"/>
  </xsl:template>
  <!-- Rule 'copy to removable media': every record. Writes a Copy event (TypeId USB-Copy) from source, destination. -->
  <xsl:template match="node()" mode="event_type_copy_to_removable_media">
    <Event>
      <xsl:if test="normalize-space(data[@name='time']/@value)">
        <EventTime>
          <TimeCreated>
            <xsl:value-of select="stroom:format-date(data[@name='time']/@value[1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
          </TimeCreated>
        </EventTime>
      </xsl:if>
      <EventSource>
        <System>
          <Name>DLP</Name>
          <Environment>Eval</Environment>
        </System>
        <Generator>usb-monitor</Generator>
        <xsl:if test="normalize-space(data[@name='host']/@value)">
          <Device>
            <HostName>
              <xsl:value-of select="data[@name='host']/@value"/>
            </HostName>
          </Device>
        </xsl:if>
        <xsl:if test="normalize-space(data[@name='user']/@value)">
          <User>
            <Id>
              <xsl:value-of select="data[@name='user']/@value"/>
            </Id>
          </User>
        </xsl:if>
      </EventSource>
      <EventDetail>
        <TypeId>USB-Copy</TypeId>
        <xsl:variable name="destination" select="data[@name='destination']/@value[normalize-space(.)]"/>
        <xsl:variable name="source" select="data[@name='source']/@value[normalize-space(.)]"/>
        <xsl:if test="$source or $destination">
          <Copy>
            <xsl:if test="$source">
              <Source>
                <File>
                  <Path>
                    <xsl:value-of select="$source"/>
                  </Path>
                </File>
              </Source>
            </xsl:if>
            <xsl:if test="$destination">
              <Destination>
                <File>
                  <Path>
                    <xsl:value-of select="$destination"/>
                  </Path>
                </File>
              </Destination>
            </xsl:if>
          </Copy>
        </xsl:if>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
