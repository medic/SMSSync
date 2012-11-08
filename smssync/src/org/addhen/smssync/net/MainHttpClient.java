/*****************************************************************************
 ** Copyright (c) 2010 - 2012 Ushahidi Inc
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
 *****************************************************************************/

package org.addhen.smssync.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import android.util.Log;

import org.addhen.smssync.util.Logger;

public class MainHttpClient {

	protected static DefaultHttpClient httpclient;

	private HttpParams httpParameters;

	private int timeoutConnection = 60000;

	private int timeoutSocket = 60000;

	protected String url;

	private static final String CLASS_TAG = MainHttpClient.class.getSimpleName();

	public MainHttpClient(String url) {
		this.url = url;
		httpParameters = new BasicHttpParams();
		httpParameters.setParameter(ConnManagerPNames.MAX_TOTAL_CONNECTIONS, 1);
		httpParameters.setParameter(
				ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE,
				new ConnPerRouteBean(1));

		httpParameters.setParameter(HttpProtocolParams.USE_EXPECT_CONTINUE,
				false);
		HttpProtocolParams.setVersion(httpParameters, HttpVersion.HTTP_1_1);
		HttpProtocolParams.setContentCharset(httpParameters, "utf8");
		// Set the timeout in milliseconds until a connection is established.
		HttpConnectionParams.setConnectionTimeout(httpParameters,
				timeoutConnection);

		// in milliseconds which is the timeout for waiting for data.
		HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);

		SchemeRegistry schemeRegistry = new SchemeRegistry();

		// http scheme
		schemeRegistry.register(new Scheme("http", PlainSocketFactory
				.getSocketFactory(), 80));
		// https scheme
		try {
			schemeRegistry.register(new Scheme("https",
					new TrustedSocketFactory(url, false), 443));
		} catch (KeyManagementException e) {
			// TODO Auto-generated catch block
			Log.e(CLASS_TAG, "Exception: " + e.getMessage());
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			Log.e(CLASS_TAG, "Exception: " + e.getMessage());
			e.printStackTrace();
		}
		ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager(
				httpParameters, schemeRegistry);

		httpclient = new DefaultHttpClient(manager, httpParameters);
	}

	protected void log(String message) {
		Logger.log(getClass().getName(), message);
	}

	protected void log(String format, Object... args) {
		Logger.log(getClass().getName(), format, args);
	}

	protected void log(String message, Exception ex) {
		Logger.log(getClass().getName(), message, ex);
	}

}
