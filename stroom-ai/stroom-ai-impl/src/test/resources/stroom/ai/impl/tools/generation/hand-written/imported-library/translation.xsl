<?xml version="1.1" encoding="UTF-8"?>
<!-- A hand-written translation importing a helper library for its EventSource and user ids. -->
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:helper="urn:example:helper" xpath-default-namespace="records:2" version="2.0">

  <xsl:import href="Example Common Helpers"/>

  <xsl:template match="records">
    <Events xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd" Version="4.1.0">
      <xsl:apply-templates/>
    </Events>
  </xsl:template>

  <xsl:template match="record">
    <Event>
      <EventTime>
        <TimeCreated><xsl:value-of select="data[@name='timestamp']/@value"/></TimeCreated>
      </EventTime>
      <xsl:call-template name="database-source">
        <xsl:with-param name="host" select="data[@name='host']/@value"/>
        <xsl:with-param name="user" select="data[@name='db_user']/@value"/>
      </xsl:call-template>
      <EventDetail>
        <xsl:choose>
          <xsl:when test="data[@name='statement']/@value = 'SELECT'">
            <TypeId>db-select</TypeId>
            <Description>Rows were read from a table</Description>
            <View>
              <Object>
                <Type>Table</Type>
                <Id><xsl:value-of select="data[@name='object']/@value"/></Id>
              </Object>
              <Data Name="rows" Value="{data[@name='rows']/@value}"/>
            </View>
          </xsl:when>
          <xsl:when test="data[@name='statement']/@value = 'DELETE'">
            <TypeId>db-delete</TypeId>
            <Description>Rows were deleted from a table</Description>
            <Delete>
              <Object>
                <Type>Table</Type>
                <Id><xsl:value-of select="data[@name='object']/@value"/></Id>
              </Object>
              <Data Name="rows" Value="{data[@name='rows']/@value}"/>
            </Delete>
          </xsl:when>
          <xsl:otherwise>
            <TypeId><xsl:value-of select="concat('db-', lower-case(data[@name='statement']/@value))"/></TypeId>
            <Unknown>
              <Data Name="statement" Value="{data[@name='statement']/@value}"/>
              <Data Name="object" Value="{helper:user-id(data[@name='object']/@value)}"/>
            </Unknown>
          </xsl:otherwise>
        </xsl:choose>
      </EventDetail>
    </Event>
  </xsl:template>
</xsl:stylesheet>
