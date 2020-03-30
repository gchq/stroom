# ApiKeyApi

All URIs are relative to *http://localhost:8080/*

Method | HTTP request | Description
------------- | ------------- | -------------
[**create**](ApiKeyApi.md#create) | **POST** /token/v1 | Create a new token.
[**delete**](ApiKeyApi.md#delete) | **DELETE** /token/v1/byToken/{token} | Delete a token by the token string itself.
[**deleteAll**](ApiKeyApi.md#deleteAll) | **DELETE** /token/v1 | Delete all tokens.
[**delete_0**](ApiKeyApi.md#delete_0) | **DELETE** /token/v1/{id} | Delete a token by ID.
[**getPublicKey**](ApiKeyApi.md#getPublicKey) | **GET** /token/v1/publickey | Provides access to this service&#39;s current public key. A client may use these keys to verify JWTs issued by this service.
[**read**](ApiKeyApi.md#read) | **GET** /token/v1/byToken/{token} | Read a token by the token string itself.
[**read_0**](ApiKeyApi.md#read_0) | **GET** /token/v1/{id} | Read a token by ID.
[**search**](ApiKeyApi.md#search) | **POST** /token/v1/search | Submit a search request for tokens
[**toggleEnabled**](ApiKeyApi.md#toggleEnabled) | **GET** /token/v1/{id}/state | Enable or disable the state of a token.


<a name="create"></a>
# **create**
> Token create(body)

Create a new token.



### Example
```java
// Import classes:
//import ApiException;
//import ApiKeyApi;


ApiKeyApi apiInstance = new ApiKeyApi();
CreateTokenRequest body = new CreateTokenRequest(); // CreateTokenRequest | CreateTokenRequest
try {
    Token result = apiInstance.create(body);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling ApiKeyApi#create");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **body** | [**CreateTokenRequest**](CreateTokenRequest.md)| CreateTokenRequest | [optional]

### Return type

[**Token**](Token.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a name="delete"></a>
# **delete**
> String delete(token)

Delete a token by the token string itself.



### Example
```java
// Import classes:
//import ApiException;
//import ApiKeyApi;


ApiKeyApi apiInstance = new ApiKeyApi();
String token = "token_example"; // String | 
try {
    String result = apiInstance.delete(token);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling ApiKeyApi#delete");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **token** | **String**|  |

### Return type

**String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a name="deleteAll"></a>
# **deleteAll**
> String deleteAll()

Delete all tokens.



### Example
```java
// Import classes:
//import ApiException;
//import ApiKeyApi;


ApiKeyApi apiInstance = new ApiKeyApi();
try {
    String result = apiInstance.deleteAll();
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling ApiKeyApi#deleteAll");
    e.printStackTrace();
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

**String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a name="delete_0"></a>
# **delete_0**
> String delete_0(id)

Delete a token by ID.



### Example
```java
// Import classes:
//import ApiException;
//import ApiKeyApi;


ApiKeyApi apiInstance = new ApiKeyApi();
Integer id = 56; // Integer | 
try {
    String result = apiInstance.delete_0(id);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling ApiKeyApi#delete_0");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **id** | **Integer**|  |

### Return type

**String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a name="getPublicKey"></a>
# **getPublicKey**
> String getPublicKey()

Provides access to this service&#39;s current public key. A client may use these keys to verify JWTs issued by this service.



### Example
```java
// Import classes:
//import ApiException;
//import ApiKeyApi;


ApiKeyApi apiInstance = new ApiKeyApi();
try {
    String result = apiInstance.getPublicKey();
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling ApiKeyApi#getPublicKey");
    e.printStackTrace();
}
```

### Parameters
This endpoint does not need any parameter.

### Return type

**String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a name="read"></a>
# **read**
> Token read(token)

Read a token by the token string itself.



### Example
```java
// Import classes:
//import ApiException;
//import ApiKeyApi;


ApiKeyApi apiInstance = new ApiKeyApi();
String token = "token_example"; // String | 
try {
    Token result = apiInstance.read(token);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling ApiKeyApi#read");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **token** | **String**|  |

### Return type

[**Token**](Token.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a name="read_0"></a>
# **read_0**
> Token read_0(id)

Read a token by ID.



### Example
```java
// Import classes:
//import ApiException;
//import ApiKeyApi;


ApiKeyApi apiInstance = new ApiKeyApi();
Integer id = 56; // Integer | 
try {
    Token result = apiInstance.read_0(id);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling ApiKeyApi#read_0");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **id** | **Integer**|  |

### Return type

[**Token**](Token.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a name="search"></a>
# **search**
> SearchResponse search(body)

Submit a search request for tokens



### Example
```java
// Import classes:
//import ApiException;
//import ApiKeyApi;


ApiKeyApi apiInstance = new ApiKeyApi();
SearchRequest body = new SearchRequest(); // SearchRequest | SearchRequest
try {
    SearchResponse result = apiInstance.search(body);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling ApiKeyApi#search");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **body** | [**SearchRequest**](SearchRequest.md)| SearchRequest | [optional]

### Return type

[**SearchResponse**](SearchResponse.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

<a name="toggleEnabled"></a>
# **toggleEnabled**
> String toggleEnabled(id, enabled)

Enable or disable the state of a token.



### Example
```java
// Import classes:
//import ApiException;
//import ApiKeyApi;


ApiKeyApi apiInstance = new ApiKeyApi();
Integer id = 56; // Integer | 
Boolean enabled = true; // Boolean | 
try {
    String result = apiInstance.toggleEnabled(id, enabled);
    System.out.println(result);
} catch (ApiException e) {
    System.err.println("Exception when calling ApiKeyApi#toggleEnabled");
    e.printStackTrace();
}
```

### Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **id** | **Integer**|  |
 **enabled** | **Boolean**|  |

### Return type

**String**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

