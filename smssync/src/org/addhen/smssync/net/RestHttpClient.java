/*
 * Rest Client based on Luke Lowrey's implementation.
 * http://lukencode.com/2010/04/27/calling-web-services-in-android-using-httpclient/
 */
package org.addhen.smssync.net;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.lang.StringBuilder;

import org.apache.http.NameValuePair;
import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.protocol.HTTP;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.util.Base64;
import android.util.Log;

public class RestHttpClient extends MainHttpClient {

    private ArrayList <NameValuePair> params;
    private ArrayList <NameValuePair> headers;
    private StringEntity entity;

    private int responseCode;
    private String message;

    private static final String CLASS_TAG = RestHttpClient.class.getSimpleName();

    private static final String USER_AGENT = "SMSSync-Android/2.0";

    public enum RequestMethod {
            GET, POST, PUT
    }

    private String response;

    public RestHttpClient(String url) {
        super(url);
        params = new ArrayList<NameValuePair>();
        headers = new ArrayList<NameValuePair>();
        headers.add(new BasicNameValuePair("User-Agent", USER_AGENT));

        try {
            URI uri = new URI(url);
            String userInfo = uri.getUserInfo();
            Log.d(CLASS_TAG, "getUserInfo: " + userInfo);
            if (userInfo != null) {
                headers.add(
                    new BasicNameValuePair(
                        "Authorization", "Basic " + base64Encode(userInfo)));
            }
        } catch (URISyntaxException e) {
			debug(e);
        }
    }

    public String getResponse() {
        return response;
    }

    public String getErrorMessage() {
        return message;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setEntity(String data) throws Exception {
        entity = new StringEntity(data, "UTF-8");
    }

    public String base64Encode(String str) {
        byte[] bytes = str.getBytes();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

	public static void debug(Exception e) {
		Log.d(CLASS_TAG, "Exception: " 
			+ e.getClass().getName()
			+ " " + getRootCause(e).getMessage()
		);
	}

	public static Throwable getRootCause(Throwable throwable) {
		if (throwable.getCause() != null)
			return getRootCause(throwable.getCause());
		return throwable;
	}
    public void addParam(String name, String value) {
        params.add(new BasicNameValuePair(name, value));
    }

    public void addHeader(String name, String value) {
        headers.add(new BasicNameValuePair(name, value));
    }


    public void execute(RequestMethod method) throws Exception {
        switch(method) {
            case GET:
            {
                //add parameters
                String combinedParams = "";
                if(!params.isEmpty()){
                    combinedParams += "?";
                    for(NameValuePair p : params)
                    {
                        String paramString = p.getName() + "=" + URLEncoder.encode(p.getValue(),"UTF-8");
                        if(combinedParams.length() > 1)
                        {
                            combinedParams  +=  "&" + paramString;
                        }
                        else
                        {
                            combinedParams += paramString;
                        }
                    }
                }

                HttpGet request = new HttpGet(url + combinedParams);

                //add headers
                for(NameValuePair h : headers)
                {
                    request.addHeader(h.getName(), h.getValue());
                }


                executeRequest(request, url);
                break;
            }
            case POST:
            {
                HttpPost request = new HttpPost(url);

                //add headers
                for(NameValuePair h : headers)
                {
                    request.addHeader(h.getName(), h.getValue());
                }

                if(!params.isEmpty()){
                    entity = new UrlEncodedFormEntity(params, HTTP.UTF_8);
                }

                request.setEntity(entity);
                executeRequest(request, url);
                break;
            }
            case PUT:
            {
                HttpPut request = new HttpPut(url);

                //add headers
                for(NameValuePair h : headers)
                {
                    request.addHeader(h.getName(), h.getValue());
                }

                if(!params.isEmpty()){
                    entity = new UrlEncodedFormEntity(params, HTTP.UTF_8);
                }

                request.setEntity(entity);
                executeRequest(request, url);
                break;
            }
        }
    }

    private void executeRequest(HttpUriRequest request, String url) throws Exception {

        HttpResponse httpResponse;

        try {
            httpResponse = httpclient.execute(request);
            responseCode = httpResponse.getStatusLine().getStatusCode();
            message = httpResponse.getStatusLine().getReasonPhrase();

            HttpEntity entity = httpResponse.getEntity();

            if (entity != null) {

                InputStream instream = entity.getContent();
                response = convertStreamToString(instream);

                // Closing the input stream will trigger connection release
                instream.close();
            }

        } catch (ClientProtocolException e)  {
            httpclient.getConnectionManager().shutdown();
			throw e;
        } catch (IOException e) {
            httpclient.getConnectionManager().shutdown();
            throw e;
        }
    }

    private static String convertStreamToString(InputStream is) {

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            debug(e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
				debug(e);
            }
        }
        return sb.toString();
    }
}
