<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="event-logging:3" xmlns="records:2" xmlns:stroom="stroom" xmlns:sm="stroom-meta" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.0">
  <xsl:template match="/Events">
    <records xsi:schemaLocation="records:2 file://records-v2.0.xsd" version="2.0">
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

      <!-- Add feed -->
      <data name="Feed">
        <xsl:attribute name="value" select="stroom:feed-name()" />
      </data>
      <data name="Feed (Keyword)">
        <xsl:attribute name="value" select="stroom:feed-name()" />
      </data>
      <xsl:apply-templates select="*" />
    </record>
  </xsl:template>

  <!-- Index the action -->
  <xsl:template match="EventDetail">
    <xsl:for-each select="*">
      <xsl:if test="(name()!='TypeId' and name()!='Description')">
        <data name="Action">
          <xsl:attribute name="value" select="name()" />
        </data>
      </xsl:if>
    </xsl:for-each>
    <xsl:apply-templates />
  </xsl:template>

  <!-- Index the time -->
  <xsl:template match="EventTime/TimeCreated">
    <data name="EventTime">
      <xsl:attribute name="value" select="." />
    </data>
  </xsl:template>

  <!-- Index the user various ways -->
  <xsl:template match="User/Id">
    <data name="UserId" analyzer="KEYWORD">
      <xsl:attribute name="value" select="." />
    </data>
  </xsl:template>

  <!-- Add system -->
  <xsl:template match="EventSource/System">
    <data name="System">
      <xsl:attribute name="value" select="." />
    </data>
  </xsl:template>

  <!-- Add environment -->
  <xsl:template match="EventSource/Environment">
    <data name="Environment">
      <xsl:attribute name="value" select="." />
    </data>
  </xsl:template>

  <!-- Add ip address -->
  <xsl:template match="IPAddress">
    <data name="IPAddress">
      <xsl:attribute name="value" select="." />
    </data>
  </xsl:template>

  <!-- Add host name -->
  <xsl:template match="HostName">
    <data name="HostName">
      <xsl:attribute name="value" select="." />
    </data>
  </xsl:template>

  <!-- Add generator -->
  <xsl:template match="Generator">
    <data name="Generator">
      <xsl:attribute name="value" select="." />
    </data>
  </xsl:template>

  <!-- Add command -->
  <xsl:template match="Command">
    <data name="Command">
      <xsl:attribute name="value" select="." />
    </data>
  </xsl:template>

  <!-- Add description both case sensitive and insensitive -->
  <xsl:template match="Description">
    <data name="Description">
      <xsl:attribute name="value" select="." />
    </data>
    <data name="Description (Case Sensitive)">
      <xsl:attribute name="value" select="." />
    </data>
  </xsl:template>

  <!-- Add Meta source info if available-->
  <xsl:template match="Meta">
    <xsl:if test="stroom:pipeline-name() eq 'Example extraction'">
      <xsl:element name="data">
        <xsl:attribute name="name">
          <xsl:text>src-id</xsl:text>
        </xsl:attribute>
        <xsl:attribute name="value" select="sm:source/sm:id" />
      </xsl:element>
      <xsl:element name="data">
        <xsl:attribute name="name">
          <xsl:text>src-partNo</xsl:text>
        </xsl:attribute>
        <xsl:attribute name="value" select="sm:source/sm:partNo" />
      </xsl:element>
      <xsl:element name="data">
        <xsl:attribute name="name">
          <xsl:text>src-recordNo</xsl:text>
        </xsl:attribute>
        <xsl:attribute name="value" select="sm:source/sm:recordNo" />
      </xsl:element>
      <xsl:element name="data">
        <xsl:attribute name="name">
          <xsl:text>src-lineFrom</xsl:text>
        </xsl:attribute>
        <xsl:attribute name="value" select="sm:source/sm:lineFrom" />
      </xsl:element>
      <xsl:element name="data">
        <xsl:attribute name="name">
          <xsl:text>src-colFrom</xsl:text>
        </xsl:attribute>
        <xsl:attribute name="value" select="sm:source/sm:colFrom" />
      </xsl:element>
      <xsl:element name="data">
        <xsl:attribute name="name">
          <xsl:text>src-lineTo</xsl:text>
        </xsl:attribute>
        <xsl:attribute name="value" select="sm:source/sm:lineTo" />
      </xsl:element>
      <xsl:element name="data">
        <xsl:attribute name="name">
          <xsl:text>src-colTo</xsl:text>
        </xsl:attribute>
        <xsl:attribute name="value" select="sm:source/sm:colTo" />
      </xsl:element>
    </xsl:if>
  </xsl:template>

  <!-- Suppress other text -->
  <xsl:template match="text()" />
</xsl:stylesheet>
