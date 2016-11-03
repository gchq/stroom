<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="event-logging:3"
  xmlns="records:2" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.0">

  <xsl:output method="xml" version="1.0" encoding="UTF-8"
    indent="yes" />

  <xsl:template match="/Events">
    <records xsi:schema-location="records:2 file://records-v2.0.xsd"
      version="2.0">
      <xsl:apply-templates select="Event" />
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

      <data name="UserId">
        <xsl:attribute name="value" select="EventSource/User/Id" />
      </data>

      <data name="Action">
        <xsl:attribute name="value">
        <xsl:for-each select="EventDetail/*">
          <xsl:if test="(name()!='TypeId' and name()!='Description')">
            <xsl:value-of select="name()" />
          </xsl:if>
        </xsl:for-each>
        </xsl:attribute>
      </data>

      <data name="Generator">
        <xsl:attribute name="value" select="EventSource/Generator" />
      </data>

      <data name="EventTime">
        <xsl:attribute name="value" select="EventTime/TimeCreated" />
      </data>

      <data name="DeviceLocationFloor">
        <xsl:attribute name="value"
          select="EventSource/Device/Location/Floor" />
      </data>

      <data name="DeviceHostName">
        <xsl:attribute name="value" select="EventSource/Device/HostName" />
      </data>

      <xsl:if test="EventDetail/Process/Command">
        <data name="ProcessCommand">
          <xsl:attribute name="value"
            select="EventDetail/Process/Command" />
        </data>
      </xsl:if>
    </record>
  </xsl:template>
</xsl:stylesheet>
