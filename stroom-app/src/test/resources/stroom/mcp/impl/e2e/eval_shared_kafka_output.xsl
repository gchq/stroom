<?xml version="1.1" encoding="UTF-8"?>
<!-- The shared Kafka output of a site's forwarding template (eval case 110): each record's pair named cef, written by
     the forwarder's own flattener, to one Kafka record. The topic is set here, once, for every forwarder. -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="kafka-records:1" version="2.0">
  <xsl:template match="/records">
    <kafkaRecords xsi:schemaLocation="kafka-records:1 file://kafka-records-v1.1.xsd">
      <xsl:apply-templates select="record"/>
    </kafkaRecords>
  </xsl:template>
  <xsl:template match="record">
    <kafkaRecord topic="arcsight-prod">
      <value><xsl:value-of select="data[@name='cef']/@value"/></value>
    </kafkaRecord>
  </xsl:template>
</xsl:stylesheet>
