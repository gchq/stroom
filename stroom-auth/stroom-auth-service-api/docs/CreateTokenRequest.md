
# CreateTokenRequest

## Properties
Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**userEmail** | **String** | The email of the user whom the token is for. | 
**tokenType** | **String** | The type of token to create: e.g. user, api, or email_reset. | 
**expiryDate** | [**DateTime**](DateTime.md) | The expiry date for an API key. |  [optional]
**comments** | **String** | Comments about the token. |  [optional]
**enabled** | **Boolean** | Whether or not the new token should be enabled. |  [optional]
**parsedTokenType** | [**ParsedTokenTypeEnum**](#ParsedTokenTypeEnum) |  |  [optional]


<a name="ParsedTokenTypeEnum"></a>
## Enum: ParsedTokenTypeEnum
Name | Value
---- | -----
USER | &quot;USER&quot;
API | &quot;API&quot;
EMAIL_RESET | &quot;EMAIL_RESET&quot;



