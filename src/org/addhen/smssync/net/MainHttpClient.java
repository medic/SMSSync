/** 
 ** Copyright (c) 2010 Ushahidi Inc
 ** All rights reserved
 ** Contact: team@ushahidi.com
 ** Website: http://www.ushahidi.com
 ** 
 ** GNU Lesser General Public License Usage
 ** This file may be used under the terms of the GNU Lesser
 ** General Public License version 3 as published by the Free Software
 ** Foundation and appearing in the file LICENSE.LGPL included in the
 ** packaging of this file. Please review the following information to
 ** ensure the GNU Lesser General Public License version 3 requirements
 ** will be met: http://www.gnu.org/licenses/lgpl.html.	
 **	
 **
 ** If you have questions regarding the use of this file, please contact
 ** Ushahidi developers at team@ushahidi.com.
 ** 
 **/

package org.addhen.smssync.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;

import org.addhen.smssync.Prefs;
import org.addhen.smssync.util.Util;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;
import android.util.Base64;

public class MainHttpClient {

    private static DefaultHttpClient httpclient;

    private HttpParams httpParameters;

    private int timeoutConnection = 60000;

    private int timeoutSocket = 60000;

    private static final String CLASS_TAG = MainHttpClient.class.getSimpleName();

    public MainHttpClient() {
        httpParameters = new BasicHttpParams();
        httpParameters.setParameter(ConnManagerPNames.MAX_TOTAL_CONNECTIONS, 1);
        httpParameters.setParameter(ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE,
                new ConnPerRouteBean(1));

        httpParameters.setParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, false);
        HttpProtocolParams.setVersion(httpParameters, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(httpParameters, "utf8");
        // Set the timeout in milliseconds until a connection is established.
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);

        // in milliseconds which is the timeout for waiting for data.
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);

        SchemeRegistry schemeRegistry = new SchemeRegistry();

        // http scheme
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        // https scheme
        try {
            schemeRegistry.register(new Scheme("https", new TrustedSocketFactory(Prefs.website,false), 443));
        } catch (KeyManagementException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager(httpParameters,
                schemeRegistry);

        httpclient = new DefaultHttpClient(manager, httpParameters);
    }

    public static String base64Encode(String str) {
        byte[] bytes = str.getBytes();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    };
    
    public static HttpResponse GetURL(String url) throws IOException {

        try {
            final HttpGet httpget = new HttpGet(url);
            httpget.addHeader("User-Agent", "SmsSync-Android/1.0)");

            // Post, check and show the result (not really spectacular, but
            // works):
            HttpResponse response = httpclient.execute(httpget);

            return response;

        } catch (final Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public static HttpResponse postJSON(String url, String json, JSONObject headers) throws IOException {
        Log.i(CLASS_TAG, "postJSON: url: " + url);
        try {
            final HttpPost httppost = new HttpPost(url);
            Iterator<String> iter = headers.keys();
            while (iter.hasNext()) {
                String k = iter.next();
                httppost.addHeader(k, headers.getString(k));
            }
            StringEntity data = new StringEntity(json,"UTF-8");
            data.setContentType("application/json; charset=utf-8");
            httppost.setEntity(data);
            httppost.addHeader("User-Agent", "SmsSync-Android/1.0)");
            HttpResponse response = httpclient.execute(httppost);
            return response;
        } catch (final Exception e) {
            Log.e(CLASS_TAG, "Exception: " + e.getMessage());
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public static HttpResponse putJSON(String url, String json, JSONObject headers) throws IOException {
        Log.i(CLASS_TAG, "putJSON: url: " + url);
        try {
            final HttpPut httpput = new HttpPut(url);
            Iterator<String> iter = headers.keys();
            while (iter.hasNext()) {
                String k = iter.next();
                httpput.addHeader(k, headers.getString(k));
            }
            StringEntity data = new StringEntity(json,"UTF-8");
            data.setContentType("application/json; charset=utf-8");
            httpput.setEntity(data);
            httpput.addHeader("User-Agent", "SmsSync-Android/1.0)");
            HttpResponse response = httpclient.execute(httpput);
            return response;
        } catch (final Exception e) {
            Log.e(CLASS_TAG, "Exception: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Upload SMS to a web service via HTTP POST
     * 
     * @param address
     * @throws MalformedURLException
     * @throws IOException
     * @return
     */
    public static boolean postSmsToWebService(String url, HashMap<String, String> params,
            Context context) {
        try {

            // support username:pass@ in URL String
            URI uri = new URI(url);
            String userInfo = uri.getUserInfo();
            Log.i(CLASS_TAG, "getUserInfo: " + userInfo);

            HttpPost httppost = new HttpPost(uri);

            if (userInfo != null) {
                httppost.addHeader("Authorization", "Basic " + base64Encode(userInfo));
            }

            // Add your data
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(3);
            nameValuePairs.add(new BasicNameValuePair("secret", params.get("secret")));
            nameValuePairs.add(new BasicNameValuePair("from", params.get("from")));
            nameValuePairs.add(new BasicNameValuePair("message", params.get("message")));
            nameValuePairs.add(new BasicNameValuePair("message_id",params.get("message_id")));
            nameValuePairs.add(new BasicNameValuePair("sent_timestamp", formatDate(params
                    .get("sent_timestamp"))));
            nameValuePairs.add(new BasicNameValuePair("sent_to", params.get("sent_to")));
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs, HTTP.UTF_8));


            // Execute HTTP Post Request
            HttpResponse response = httpclient.execute(httppost);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 200 || statusCode == 201) {
                String resp = getText(response);
                boolean success = Util.extractPayloadJSON(resp);
                Util.processResponseCallback(context, resp);
                if (success) {
                    return true;
                }
            }
            Log.e(CLASS_TAG, "SMSSync POST Failure");
            Log.e(CLASS_TAG, EntityUtils.toString(response.getEntity()));
            return false;
        } catch (final Exception e) {
            // fail gracefully
            Log.e(CLASS_TAG, "Exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    /**
     * Does a HTTP GET request
     * 
     * @param String url - The Callback URL to do the HTTP GET
     * @return String - the HTTP response
     */
    public static String getFromWebService(String url) {


        try {
            // Create a new HttpClient 
            URI uri = new URI(url);
            String userInfo = uri.getUserInfo();
            Log.i(CLASS_TAG, "getUserInfo: " + userInfo);
            final HttpGet httpGet = new HttpGet(uri);
            if (userInfo != null) {
                httpGet.addHeader("Authorization", "Basic " + base64Encode(userInfo));
            }
            httpGet.addHeader("User-Agent", "SMSSync-Android/1.0)");
            // Execute HTTP Get Request
            HttpResponse response = httpclient.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            Log.i(CLASS_TAG, "getFromWebService Status Code: " + statusCode);
            String resp = getText(response);

            if (statusCode == 200 || statusCode == 304) {
                return resp;
            } else {
                return "";
            }

        } catch (ClientProtocolException e) {
            Log.e(CLASS_TAG, "Exception: " + e.getMessage());
            return null;
        } catch (IOException e) {
            Log.e(CLASS_TAG, "Exception: " + e.getMessage());
            return null;
        } catch (java.net.URISyntaxException e) {
            Log.e(CLASS_TAG, "Exception: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static String getText(HttpResponse response) {
        String text = "";
        try {
            text = getText(response.getEntity().getContent());
        } catch (final Exception ex) {
            Log.e(CLASS_TAG, "getText Exception: " + ex.getMessage());
        }
        return text;
    }

    public static String getText(InputStream in) {
        String text = "";
        final BufferedReader reader = new BufferedReader(new InputStreamReader(in), 1024);
        final StringBuilder sb = new StringBuilder();
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            text = sb.toString();
        } catch (final Exception ex) {
        } finally {
            try {
                in.close();
            } catch (final Exception ex) {
            }
        }
        return text;
    }

    private static String formatDate(String date) {
        try {
           
            return Util.formatDateTime(Long.parseLong(date), "MM-dd-yy kk:mm");
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
