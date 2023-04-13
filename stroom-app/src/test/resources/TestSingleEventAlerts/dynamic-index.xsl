<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="event-logging:3"
  xmlns="documents:2" xmlns:stroom="stroom"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  version="2.0">
  <xsl:template match="/Events">
    <documents xsi:schemaLocation="documents:2 file://documents-v1.0.xsd"
      version="1.0">
      <xsl:apply-templates />
    </documents>
  </xsl:template>
  <xsl:template match="Event">
    <document>
      <field>
        <name>StreamId</name>
        <type>Id</type>
        <indexed>true</indexed>
        <stored>true</stored>
        <value><xsl:value-of select="@StreamId" /></value>
      </field>
      <field>
        <name>EventId</name>
        <type>Id</type>
        <indexed>true</indexed>
        <stored>true</stored>
        <value><xsl:value-of select="@EventId" /></value>
      </field>

      <!-- Add feed -->
      <field>
        <name>Feed</name>
        <analyser>Alpha numeric</analyser>
        <indexed>true</indexed>
        <value><xsl:value-of select="stroom:feed-name()" /></value>
      </field>

      <field>
        <name>Feed (Keyword)</name>
        <type>Text</type>
        <indexed>true</indexed>
        <value><xsl:value-of select="stroom:feed-name()" /></value>
      </field>

      <xsl:apply-templates select="*" />
    </document>
  </xsl:template>

  <!-- Index the action -->
  <xsl:template match="EventDetail">
    <xsl:for-each select="*">
      <xsl:if test="(name()!='TypeId' and name()!='Description')">
        <field>
          <name>Action</name>
          <analyser>Alpha numeric</analyser>
          <indexed>true</indexed>
          <value><xsl:value-of select="name()" /></value>
        </field>
      </xsl:if>
    </xsl:for-each>
    <xsl:apply-templates />
  </xsl:template>

  <!-- Index the time -->
  <xsl:template match="EventTime/TimeCreated">
    <field>
      <name>EventTime</name>
      <type>Date</type>
      <indexed>true</indexed>
      <value><xsl:value-of select="." /></value>
    </field>
  </xsl:template>

  <!-- Index the user various ways -->
  <xsl:template match="User/Id">
    <field>
      <name>UserId</name>
      <indexed>true</indexed>
      <value><xsl:value-of select="." /></value>
    </field>
  </xsl:template>

  <!-- Add system -->
  <xsl:template match="EventSource/System">
    <field>
      <name>System</name>
      <analyser>Alpha numeric</analyser>
      <indexed>true</indexed>
      <value><xsl:value-of select="." /></value>
    </field>
  </xsl:template>

  <!-- Add environment -->
  <xsl:template match="EventSource/Environment">
    <field>
      <name>Environment</name>
      <analyser>Alpha numeric</analyser>
      <indexed>true</indexed>
      <value><xsl:value-of select="." /></value>
    </field>
  </xsl:template>

  <!-- Add ip address -->
  <xsl:template match="IPAddress">
    <field>
      <name>IPAddress</name>
      <indexed>true</indexed>
      <value><xsl:value-of select="." /></value>
    </field>
  </xsl:template>

  <!-- Add host name -->
  <xsl:template match="HostName">
    <field>
      <name>HostName</name>
      <indexed>true</indexed>
      <value><xsl:value-of select="." /></value>
    </field>
  </xsl:template>

  <!-- Add generator -->
  <xsl:template match="Generator">
    <field>
      <name>Generator</name>
      <analyser>Alpha numeric</analyser>
      <indexed>true</indexed>
      <value><xsl:value-of select="." /></value>
    </field>
  </xsl:template>

  <!-- Add command -->
  <xsl:template match="Command">
    <field>
      <name>Command</name>
      <analyser>Alpha numeric</analyser>
      <indexed>true</indexed>
      <value><xsl:value-of select="." /></value>
    </field>
    <field>
      <name>Command (Keyword)</name>
      <indexed>true</indexed>
      <value><xsl:value-of select="." /></value>
    </field>
  </xsl:template>

  <!-- Add description both case sensitive and insensitive -->
  <xsl:template match="Description">
    <field>
      <name>Description</name>
      <analyser>Alpha numeric</analyser>
      <indexed>true</indexed>
      <value><xsl:value-of select="." /></value>
    </field>
    <field>
      <name>Description (Case Sensitive)</name>
      <analyser>Alpha numeric</analyser>
      <indexed>true</indexed>
      <caseSensitive>true</caseSensitive>
      <value><xsl:value-of select="." /></value>
    </field>
  </xsl:template>

  <!-- Suppress other text -->
  <xsl:template match="text()" />
</xsl:stylesheet>
