<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="event-logging:3" xmlns="records:2" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.0">
  <xsl:template match="/Events">
    <records xsi:schemaLocation="records:2 file://records-v2.0.xsd" version="2.0">
      <xsl:apply-templates />
    </records>
  </xsl:template>
  <xsl:template match="Event">
    <record>

      <!-- Create a prefix to send documents to shards grouped by month of creation -->
      <xsl:variable name="prefix" select="substring(replace(EventTime/TimeCreated, '-', ''), 0, 7)" />

      <!-- The number of bits from the shard key to use in the composite hash -->
      <xsl:variable name="num" select="'/2'" />

      <!-- Create a unique id for the event -->
      <xsl:variable name="id" select="concat(@StreamId, '-', @EventId)" />

      <!-- Create the id element -->
      <data name="id">
        <xsl:attribute name="value" select="concat($prefix, $num, '!', $id)" />
      </data>
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
      <data name="Feed_Keyword">
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
    <data name="Description_Case_Sensitive">
      <xsl:attribute name="value" select="." />
    </data>
  </xsl:template>

  <!-- Suppress other text -->
  <xsl:template match="text()" />
</xsl:stylesheet>
