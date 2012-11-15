# HTTP Callbacks

HTTP Callbacks allows your server to send instructions to the gateway to make additional HTTP requests.  At the moment the callback request is not guaranteed or tracked in any way by SMSSync, other than what you can track on your server or whether or not the callback request was received by the target. 


In your usual response to SMSSync you include a `callback` object with  `options` and optionally `data` properties for SMSSync to execute the request.

## Configuration

In SMSSync you must enable the **Get Reply From Server** option.

## Example 

In this example our server wants to tell SMSSync to make a POST request to another web service as well as send a success message to our SMS client.

#### Response with callback property:

```
{
  "callback": {
    "options": {
      "host": "10.0.0.172",
      "port": "5984",
      "method": "POST",
      "headers": {
        "Content-Type": "application/json; charset=utf-8"
      },
      "path": "/v1/your-other-api/"
    },
    "data": {
      "uuid": "7cb8cfb7267c2296d3f4ff9e089d0076",
      "foo": "bar"
    }
  },
  "payload": {
    "success": true,
    "task": "send",
    "messages": [
      {
        "to": "+123",
        "message": "Your submission was received, thank you."
      }
    ]
  }
}
```
#### Request that gets created

```
POST /v1/your-other-api/
Host: 10.0.0.172:5984
Content-Type: application/json; charset=utf-8
User-Agent: SMSSync-Android/2.0
Content-Length: 56

{"uuid":"7cb8cfb7267c2296d3f4ff9e089d0076","foo":"bar"}
```

* The `User-Agent` header is automatically set.
* The JSON structure inside the `data` attribute is stringified and sent in the body of the request.

## Restrictions

We are open to changes and pull requests but currently there are some limitations.

* If the optional `data` attribute exists it must contain a string or a valid JSON object.
* Only the `PUT`, `POST` and `GET` methods are implemented.
* A callback chain fails if a 200 or 201 HTTP code is not found.
* The `data` attribute is ignored on `GET` requests.


## About

This came out of a specific architectural restriction using CouchDB because there is a limited amount of things CouchDB can do with one request.  For example we can create a document but cannot lookup another document in the same request.  This allows us to use the gateway to make additional requests and changes to our database or elsewhere.  It is also potentially useful to chain requests together when dealing with distributed systems.  

We also wanted to keep intrastructure requirments to a minimum, so we include this functionality in the gateway itself rather than another process.
