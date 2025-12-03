<?xml version="1.1" encoding="UTF-8" ?>
<!--
  ~ Copyright 2016-2025 Crown Copyright
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<xsl:stylesheet xpath-default-namespace="records:2" xmlns="event-logging:3" xmlns:stroom="stroom" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.0">
	<xsl:template match="records">
		<Events xsi:schemaLocation="event-logging:3 file://event-logging-v3.0.0.xsd" Version="3.0.0">
			<xsl:apply-templates />
		</Events>
	</xsl:template>
	<xsl:template match="record">
		<xsl:variable name="user" select="data[@name='UserName']/@value" />
		<xsl:if test="data[@name='EventType']/@value = 'authenticationFailed'">
			<Event>
				<xsl:call-template name="header" />
				<EventDetail>
					<TypeId>0001</TypeId>
					<Authenticate>
						<Action>Logon</Action>
						<LogonType>Interactive</LogonType>
						<User>
							<Id><xsl:value-of select="data[@name='UserName']/@value" /></Id>
						</User>
						<Outcome>
							<Success>false</Success>
							<Description>Logon failure. Incorrect user name or password.</Description>
						</Outcome>
					</Authenticate>
				</EventDetail>
			</Event>
		</xsl:if>
		<xsl:if test="data[@name='EventType']/@value = 'authorisationFailed'">
			<Event>
				<xsl:call-template name="header" />
				<EventDetail>
					<TypeId>0001</TypeId>
					<Process>
						<Action>Execute</Action>
						<Type>Application</Type>
						<Command><xsl:value-of select="data[@name='Message']/@value" /></Command>
						<Rule>Rule</Rule>
						<Outcome>
							<Permitted>false</Permitted>
							<Description><xsl:value-of select="data[@name='ErrorCode']/@value" /></Description>
						</Outcome>
					</Process>
				</EventDetail>
			</Event>
		</xsl:if>
	</xsl:template>

	<xsl:template name="header">
		<xsl:variable name="date" select="data[@name='Date']/@value" />
		<xsl:variable name="time" select="data[@name='Time']/@value" />
		<xsl:variable name="dateTime" select="concat($date, $time)" />
		<xsl:variable name="formattedDateTime" select="stroom:format-date($dateTime, 'dd/MM/yyyyHH:mm:ss')" />
		<xsl:variable name="user" select="data[@name='UserName']/@value" />

		<EventTime>
			<TimeCreated><xsl:value-of select="$formattedDateTime" /></TimeCreated>
		</EventTime>
		<EventSource>
			<System>
				<Name>Test</Name>
				<Environment>Test</Environment>
			</System>
			<Generator>NetworkMonitoring</Generator>
			<Device>
				<HostName><xsl:value-of select="data[@name='Server']/@value" /></HostName>
				<IPAddress>1.1.1.1</IPAddress>
				<MACAddress>00-00-00-00-00-00</MACAddress>
				<xsl:variable name="location" select="stroom:lookup('HOSTNAME_TO_LOCATION_MAP', 'Device/Location', $formattedDateTime)" />
				<xsl:if test="$location">
					<xsl:copy-of select="$location" />
				</xsl:if>
			</Device>
			<User>
				<Id><xsl:value-of select="data[@name='UserName']/@value" /></Id>
			</User>
		</EventSource>
	</xsl:template>
</xsl:stylesheet>
