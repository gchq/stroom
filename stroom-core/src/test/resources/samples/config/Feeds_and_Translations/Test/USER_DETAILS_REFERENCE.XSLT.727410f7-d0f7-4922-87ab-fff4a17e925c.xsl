<?xml version="1.0" encoding="UTF-8" ?>
<xsl:stylesheet xpath-default-namespace="records:2" xmlns="reference-data:2" xmlns:evt="event-logging:3"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version="2.0">
    <xsl:template match="records">
        <referenceData xsi:schemaLocation="reference-data:2 file://reference-data-v2.0.1.xsd event-logging:3 file://event-logging-v3.0.0.xsd" version="2.0.1">
            <xsl:apply-templates/>
        </referenceData>
    </xsl:template>

    <xsl:template match="record">
        <reference>
            <map>STAFF_NO_TO_USER_DETAILS_MAP</map>
            <key><xsl:value-of select="data[@name='staffNo']/@value"/></key>
            <value>
                <evt:UserDetails>
                    <evt:StaffNumber><xsl:value-of select="data[@name='staffNo']/@value"/></evt:StaffNumber>
                    <evt:Surname><xsl:value-of select="data[@name='surname']/@value"/></evt:Surname>
                    <evt:Initials><xsl:value-of select="concat(substring(data[@name='firstName']/@value,1,1), ' ', substring(data[@name='middleName']/@value,1,1))"/></evt:Initials>
                    <evt:GivenName><xsl:value-of select="data[@name='firstName']/@value"/></evt:GivenName>
                    <evt:Location><xsl:value-of select="data[@name='location']/@value"/></evt:Location>
                    <evt:Phone><xsl:value-of select="data[@name='phone']/@value"/></evt:Phone>
                    <evt:SupervisorStaffNumber><xsl:value-of select="data[@name='supervisorStaffNo']/@value"/></evt:SupervisorStaffNumber>
                </evt:UserDetails>
            </value>
        </reference>
    </xsl:template>
</xsl:stylesheet>
