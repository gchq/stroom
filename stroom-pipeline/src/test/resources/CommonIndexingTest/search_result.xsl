<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="event-logging:3"
  xmlns="records:2" xmlns:stroom="stroom"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  version="2.0">
  <xsl:template match="/Events">
    <records xsi:schemaLocation="records:2 file://records-v2.0.xsd"
      version="2.0">
      <xsl:apply-templates />
    </records>
  </xsl:template>
  <xsl:template match="Event">
    <record>
      <data name="StreamId">
        <xsl:attribute name="value" select="@StreamId" />
      </data>
      <data name="EventId">
        <xsl:attribute name="value" select="@EventId" />
      </data>
      <data name="EventTime">
        <xsl:attribute name="value" select="EventTime/TimeCreated" />
      </data>
      <data name="User">
        <xsl:attribute name="value" select="EventSource/User/Id" />
      </data>
      <xsl:choose>
        <xsl:when test="EventDetail/Alert">
          <data name="Action">
            <xsl:attribute name="value"><xsl:text>Alert </xsl:text><xsl:value-of
              select="EventDetail/Alert/*[1]/name()" /></xsl:attribute>
          </data>
        </xsl:when>
        <xsl:when test="EventDetail/Authenticate/Action">
          <data name="Action">
            <xsl:attribute name="value"
              select="EventDetail/Authenticate/Action" />
          </data>
        </xsl:when>
        <xsl:when test="EventDetail/Authenticate">
          <data name="Action" value="Authenticate" />
        </xsl:when>
        <xsl:when test="EventDetail/Authorise">
          <data name="Action">
            <xsl:attribute name="value"><xsl:text>Authorise </xsl:text><xsl:value-of
              select="EventDetail/Authorise/Action" /></xsl:attribute>
            Authorise
          </data>
        </xsl:when>
        <xsl:when test="EventDetail/Copy">
          <data name="Action" value="Copy" />
        </xsl:when>
        <xsl:when test="EventDetail/Create">
          <data name="Action" value="Create" />
        </xsl:when>
        <xsl:when test="EventDetail/Delete">
          <data name="Action" value="Delete" />
        </xsl:when>
        <xsl:when test="EventDetail/Install">
          <data name="Action" value="Install" />
        </xsl:when>
        <xsl:when test="EventDetail/Move">
          <data name="Action" value="Move" />
        </xsl:when>
        <xsl:when test="EventDetail/Print">
          <data name="Action" value="Print" />
        </xsl:when>
        <xsl:when test="EventDetail/Process">
          <data name="Action" value="Process" />
        </xsl:when>
        <xsl:when test="EventDetail/Search">
          <data name="Action" value="Search" />
        </xsl:when>
        <xsl:when test="EventDetail/Uninstall">
          <data name="Action" value="Uninstall" />
        </xsl:when>
        <xsl:when test="EventDetail/Unknown">
          <data name="Action" value="Unknown" />
        </xsl:when>
        <xsl:when test="EventDetail/Update">
          <data name="Action" value="Update" />
        </xsl:when>
        <xsl:when test="EventDetail/View">
          <data name="Action">
            <xsl:attribute name="value"><xsl:text>View </xsl:text><xsl:value-of
              select="EventDetail/View/Type" /></xsl:attribute>
            Authorise
          </data>
        </xsl:when>
        <xsl:otherwise>
          <data name="Action">
            <xsl:attribute name="value" select="EventDetail/TypeId" />
          </data>
        </xsl:otherwise>
      </xsl:choose>
      
      <xsl:apply-templates select="EventDetail" mode="text" />
    </record>
  </xsl:template>
  
  <xsl:template match="text()" mode="text">
    <data name="Text">
	  <xsl:attribute name="value" select="." />
    </data>
  </xsl:template>
</xsl:stylesheet>
