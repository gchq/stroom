<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="event-logging:3" xmlns="nstat:1" xmlns:stroom="stroom"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version="2.0">
  <xsl:template match="/Events">
    <nstat>
      <xsl:apply-templates select="Event" />
    </nstat>
  </xsl:template>
  <xsl:template match="Event">
    <event>
      <time>
        <xsl:value-of select="EventTime/TimeCreated" />
      </time>
      <obj>
        <type>Host</type>
        <name>
          <xsl:value-of select="EventSource/Device/HostName" />
        </name>
      </obj>
      <obj>
        <type>Feed</type>
        <name>
          <xsl:value-of select="stroom:feed-name()" />
        </name>
      </obj>
      <obj>
        <type>Remote Host</type>
        <name>
          <xsl:value-of select="EventSource/Data[@Name='RemoteHost']/@Value" />
        </name>
      </obj>
      <associate>
        <obj>
          <type>Host</type>
          <name>
            <xsl:value-of select="EventSource/Device/HostName" />
          </name>
        </obj>
        <obj>
          <type>Feed</type>
          <name>
            <xsl:value-of select="stroom:feed-name()" />
          </name>
        </obj>
        <obj>
          <type>Remote Host</type>
          <name>
            <xsl:value-of select="EventSource/Data[@Name='RemoteHost']/@Value" />
          </name>
        </obj>
      </associate>
    </event>
  </xsl:template>
</xsl:stylesheet>
