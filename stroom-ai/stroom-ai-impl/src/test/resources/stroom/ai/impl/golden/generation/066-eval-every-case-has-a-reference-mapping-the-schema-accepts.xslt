<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fn="http://www.w3.org/2005/xpath-functions" xmlns:mcp="stroom-mcp:functions" version="3.0" xpath-default-namespace="http://www.w3.org/2013/XSL/json" exclude-result-prefixes="stroom xs fn mcp">
  <xsl:template match="/">
    <Events Version="4.1.0" xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd">
      <xsl:apply-templates select="/map/map" mode="event"/>
    </Events>
  </xsl:template>
  <xsl:template match="*" mode="event">
    <xsl:apply-templates select="*[@key='Records']/*" mode="item">
      <xsl:with-param name="record" select="." tunnel="yes"/>
    </xsl:apply-templates>
  </xsl:template>
  <xsl:template match="*" mode="item">
    <xsl:variable name="event_name" select="*[@key='eventName'][normalize-space(.)]"/>
    <xsl:choose>
      <xsl:when test="$event_name = 'ConsoleLogin'">
        <xsl:apply-templates select="." mode="event_type_console_login"/>
      </xsl:when>
      <xsl:when test="$event_name = 'GetObject'">
        <xsl:apply-templates select="." mode="event_type_object_read"/>
      </xsl:when>
      <xsl:when test="$event_name = 'DeleteObject'">
        <xsl:apply-templates select="." mode="event_type_object_delete"/>
      </xsl:when>
      <xsl:when test="$event_name = 'CreateUser'">
        <xsl:apply-templates select="." mode="event_type_user_create"/>
      </xsl:when>
      <xsl:when test="$event_name = 'PutBucketPolicy'">
        <xsl:apply-templates select="." mode="event_type_policy_change"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:sequence select="stroom:log('WARN', concat('No event mapping matched record ', stroom:record-no(), ' (', string-join((concat('eventName=', string(($event_name)[1]))), ' | '), ')'))"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
  <!-- Rule 'console-login': records where eventName = ConsoleLogin. Writes an Authenticate event (TypeId from
       eventName) from userIdentity.userName, responseElements.ConsoleLogin. -->
  <xsl:template match="node()" mode="event_type_console_login">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Authenticate>
          <Action>Logon</Action>
          <xsl:if test="normalize-space(*[@key='userIdentity']/*[@key='userName'])">
            <User>
              <Id>
                <xsl:value-of select="*[@key='userIdentity']/*[@key='userName']"/>
              </Id>
            </User>
          </xsl:if>
          <xsl:if test="*[@key='responseElements']/*[@key='ConsoleLogin'] = ('Success', 'Failure')">
            <Outcome>
              <Success>
                <xsl:value-of select="if (*[@key='responseElements']/*[@key='ConsoleLogin'] = 'Success') then 'true' else 'false'"/>
              </Success>
            </Outcome>
          </xsl:if>
        </Authenticate>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'object-read': records where eventName = GetObject. Writes a View event (TypeId from eventName) from
       requestParameters.key, requestParameters.bucketName, errorCode. -->
  <xsl:template match="node()" mode="event_type_object_read">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <View>
          <xsl:apply-templates select="." mode="object"/>
          <xsl:sequence select="mcp:data('bucket', *[@key='requestParameters']/*[@key='bucketName'])"/>
          <xsl:sequence select="mcp:data('error', *[@key='errorCode'])"/>
        </View>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'object-delete': records where eventName = DeleteObject. Writes a Delete event (TypeId from eventName)
       from requestParameters.key, requestParameters.bucketName, errorCode. -->
  <xsl:template match="node()" mode="event_type_object_delete">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Delete>
          <xsl:apply-templates select="." mode="object"/>
          <xsl:sequence select="mcp:data('bucket', *[@key='requestParameters']/*[@key='bucketName'])"/>
          <xsl:sequence select="mcp:data('error', *[@key='errorCode'])"/>
        </Delete>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'user-create': records where eventName = CreateUser. Writes a Create event (TypeId from eventName) from
       requestParameters.userName, errorCode. -->
  <xsl:template match="node()" mode="event_type_user_create">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Create>
          <Object>
            <Type>IAM user</Type>
            <xsl:if test="normalize-space(*[@key='requestParameters']/*[@key='userName'])">
              <Id>
                <xsl:value-of select="*[@key='requestParameters']/*[@key='userName']"/>
              </Id>
            </xsl:if>
          </Object>
          <xsl:sequence select="mcp:data('error', *[@key='errorCode'])"/>
        </Create>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- Rule 'policy-change': records where eventName = PutBucketPolicy. Writes an Update event (TypeId from eventName)
       from requestParameters.bucketName, errorCode. -->
  <xsl:template match="node()" mode="event_type_policy_change">
    <Event>
      <xsl:apply-templates select="." mode="event_time"/>
      <xsl:apply-templates select="." mode="event_source"/>
      <EventDetail>
        <xsl:apply-templates select="." mode="type_id"/>
        <Update>
          <After>
            <Object>
              <Type>S3 bucket policy</Type>
              <xsl:if test="normalize-space(*[@key='requestParameters']/*[@key='bucketName'])">
                <Id>
                  <xsl:value-of select="*[@key='requestParameters']/*[@key='bucketName']"/>
                </Id>
              </xsl:if>
            </Object>
          </After>
          <xsl:sequence select="mcp:data('error', *[@key='errorCode'])"/>
        </Update>
      </EventDetail>
    </Event>
  </xsl:template>
  <!-- event_time: shared by the rules 'console-login', 'object-read', 'object-delete', 'user-create', 'policy-change' -->
  <xsl:template match="node()" mode="event_time">
    <xsl:if test="normalize-space(*[@key='eventTime'])">
      <EventTime>
        <TimeCreated>
          <xsl:value-of select="stroom:format-date(*[@key='eventTime'][1], 'yyyy-MM-dd''T''HH:mm:ssX')"/>
        </TimeCreated>
      </EventTime>
    </xsl:if>
  </xsl:template>
  <!-- event_source: shared by the rules 'console-login', 'object-read', 'object-delete', 'user-create',
       'policy-change' -->
  <xsl:template match="node()" mode="event_source">
    <EventSource>
      <System>
        <Name>AWS CloudTrail</Name>
        <Environment>Eval</Environment>
      </System>
      <xsl:if test="normalize-space(*[@key='eventSource'])">
        <Generator>
          <xsl:value-of select="*[@key='eventSource']"/>
        </Generator>
      </xsl:if>
      <xsl:if test="normalize-space(*[@key='awsRegion'])">
        <Device>
          <HostName>
            <xsl:value-of select="*[@key='awsRegion']"/>
          </HostName>
        </Device>
      </xsl:if>
      <xsl:variable name="source_ip_address" select="*[@key='sourceIPAddress'][normalize-space(.)]"/>
      <xsl:variable name="source_service_parts" select="analyze-string(string($source_ip_address[1]), '^([a-z][\w.-]*\.amazonaws\.com)$')"/>
      <xsl:variable name="source_service" select="$source_service_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
      <xsl:variable name="source_ip_parts" select="analyze-string(string($source_ip_address[1]), '^(\d{1,3}(?:\.\d{1,3}){3})$')"/>
      <xsl:variable name="source_ip" select="$source_ip_parts/fn:match//fn:group[@nr=1][normalize-space(.)]"/>
      <xsl:if test="$source_ip or $source_service">
        <Client>
          <xsl:if test="$source_service">
            <HostName>
              <xsl:value-of select="$source_service"/>
            </HostName>
          </xsl:if>
          <xsl:if test="$source_ip">
            <IPAddress>
              <xsl:value-of select="$source_ip"/>
            </IPAddress>
          </xsl:if>
        </Client>
      </xsl:if>
      <xsl:if test="normalize-space(*[@key='userIdentity']/*[@key='userName'])">
        <User>
          <Id>
            <xsl:value-of select="*[@key='userIdentity']/*[@key='userName']"/>
          </Id>
        </User>
      </xsl:if>
    </EventSource>
  </xsl:template>
  <!-- type_id: shared by the rules 'console-login', 'object-read', 'object-delete', 'user-create', 'policy-change' -->
  <xsl:template match="node()" mode="type_id">
    <xsl:if test="normalize-space(*[@key='eventName'])">
      <TypeId>
        <xsl:value-of select="*[@key='eventName']"/>
      </TypeId>
    </xsl:if>
  </xsl:template>
  <!-- object: shared by the rules 'object-read', 'object-delete' -->
  <xsl:template match="node()" mode="object">
    <Object>
      <Type>S3 object</Type>
      <xsl:if test="normalize-space(*[@key='requestParameters']/*[@key='key'])">
        <Id>
          <xsl:value-of select="*[@key='requestParameters']/*[@key='key']"/>
        </Id>
      </xsl:if>
    </Object>
  </xsl:template>
  <xsl:function name="mcp:data" as="element()?">
    <!-- A Data entry, Name and Value, when the value is present (not blank) -->
    <xsl:param name="name" as="xs:string"/>
    <xsl:param name="value" as="item()*"/>
    <xsl:if test="exists($value[normalize-space(.)])">
      <Data Name="{$name}" Value="{$value}"/>
    </xsl:if>
  </xsl:function>
</xsl:stylesheet>
