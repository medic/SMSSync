# HTTP Callbacks

## What

HTTP Callbacks allows your server to send instructions to the gateway to make additional HTTP requests.  At the moment the callback request is not guaranteed or tracked in any way by SMSSync, other than what you can track on your server or whether or not the callback request was received by the target. 


## How
In your usual response to SMSSync you include a `callback` object with  `options` and optionally `data` properties for SMSSync to execute the request.

### Example response with callback:

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

## Why

This came out of a specific architectural restriction using CouchDB because there is a limited amount of things CouchDB can do with one request.  For example we can create a document but cannot lookup another document in the same request.  This allows us to use the gateway to make additional requests and changes to our database or elsewhere.  It is also potentially useful to chain requests together when dealing with distributed systems.  

We also wanted to keep intrastructure requirments to a minimum, so we include this functionality in the gateway itself rather than another process.
