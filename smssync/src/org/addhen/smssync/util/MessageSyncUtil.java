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
package org.addhen.smssync.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;

import org.addhen.smssync.Prefs;
import org.addhen.smssync.ProcessSms;
import org.addhen.smssync.R;
import org.addhen.smssync.models.MessagesModel;
import org.addhen.smssync.net.RestHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

/**
 * @author eyedol
 * 
 */
public class MessageSyncUtil extends Util {

	private Context context;

	private String url;

	private String urlSecret;

	private boolean responseSuccess;

	private static final String CLASS_TAG = MessageSyncUtil.class.getSimpleName();

	private ProcessSms processSms;

	public MessageSyncUtil(Context context, String url) {
		this.context = context;
		this.url = url;
		this.urlSecret = "";
		this.responseSuccess = false;
		processSms = new ProcessSms(context);
	}

	private static String formatDate(String date) {
		try {

			return Util.formatDateTime(Long.parseLong(date), "MM-dd-yy kk:mm");
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static void debug(Exception e) {
		Log.d(CLASS_TAG, "Exception: " 
			+ e.getClass().getName()
			+ " " + getRootCause(e).getMessage()
		);
	}

	private static Throwable getRootCause(Throwable throwable) {
		if (throwable.getCause() != null)
			return getRootCause(throwable.getCause());
		return throwable;
	}

	/**
	 * Posts received SMS to a configured callback URL.
	 * 
	 * @return boolean
	 */
	public boolean postToAWebService(String from, String message,
			String sentTimestamp, String messageUuid, String secret) {

		if (TextUtils.isEmpty(url)) { return false; }

		try {

			RestHttpClient client = new RestHttpClient(url);

			client.addParam("secret", secret);
			client.addParam("from", from);
			client.addParam("message", message);
			client.addParam("message_id", messageUuid);

			if (formatDate(sentTimestamp) != null) {
				client.addParam("sent_timestamp", sentTimestamp);
			}

			client.addParam("sent_to", getPhoneNumber(context));
			client.execute(RestHttpClient.RequestMethod.POST);

			int statusCode = client.getResponseCode();
			String resp = client.getResponse();
			return processResponse(resp, statusCode);

		} catch (Exception e) {
			debug(e);
			return false;
		}

	}

	/**
	 * Pushes pending messages to the configured URL.
	 * 
	 * @param int messageId - Sync by Id - 0 for no ID > 0 to for an id
	 * @param String
	 *            url The sync URL to push the message to.
	 * 
	 * @return int
	 */
	public int syncToWeb(String messageUuid) {
		log("syncToWeb(): push pending messages to the Sync URL");
		MessagesModel model = new MessagesModel();
		List<MessagesModel> listMessages = new ArrayList<MessagesModel>();
		// check if it should sync by id
		if (messageUuid != null && !TextUtils.isEmpty(messageUuid)) {
			model.loadByUuid(messageUuid);
			listMessages = model.listMessages;

		} else {
			model.load();
			listMessages = model.listMessages;

		}
		int deleted = 0;

		if (listMessages != null) {
			if (listMessages.size() == 0) {
				return 2;
			}

			for (MessagesModel messages : listMessages) {
				Log.d(CLASS_TAG, "processing messages");
				if (processSms.routePendingMessages(messages.getMessageFrom(),
						messages.getMessage(), messages.getMessageDate(),
						messages.getMessageUuid())) {

					// / if it successfully pushes message, delete message
					// from db
					new MessagesModel().deleteMessagesByUuid(messages
							.getMessageUuid());
					deleted = 0;
				} else {
					deleted = 1;
				}

			}
		}

		return deleted;

	}

    private void sendSms(String to, String message) {

        String lineNumber = "+" + getLineNumber(context);

        // Avoid potential endless loop and costly sms bill.  
        if (!PhoneNumberUtils.compare(lineNumber,to)) {
            processSms.sendSms(to, message);
        } else {
            Log.e(
                CLASS_TAG, "SMS NOT sent, destination is same "
                + "as device lineNumber: " + lineNumber
            );
        }
    }

	/**
	 * Sends messages received from the server as SMS. Only send outgoing
	 * messages when success is true.
	 * 
	 * @param String
	 *            response - the response from the server.
	 */
	public void sendResponseFromServer(String response) {
		Log.d(CLASS_TAG, "sendResponseFromServer(): " + " response:"
				+ response);

		String task = "";
		String secret = "";
		JSONObject jsonObject;
		JSONObject payloadObject = null;
		JSONArray jsonArray;

		if (!TextUtils.isEmpty(response) && response != null) {
			try {
				jsonObject = new JSONObject(response);
				payloadObject = jsonObject.getJSONObject("payload");
			} catch (JSONException e) {
				debug(e);
			}
		}

		if (payloadObject != null) {
			try {
				//secret is optional
				secret = payloadObject.getString("secret");
			} catch (JSONException e) {
				secret = "";
			}
		}

		if (payloadObject != null) {
			try {
				task = payloadObject.getString("task");
				if ((task.equals("send")) && (secret.equals(urlSecret))) {
					jsonArray = payloadObject.getJSONArray("messages");

					for (int index = 0; index < jsonArray.length(); ++index) {
						jsonObject = jsonArray.getJSONObject(index);

						new Util().log("Send sms: To: "
								+ jsonObject.getString("to") + "Message: "
								+ jsonObject.getString("message"));

						sendSms(
							jsonObject.getString("to"),
							jsonObject.getString("message")
						);
					}
				}
			} catch (JSONException e) {
				debug(e);
			}
		}

	}

	/**
	 * Process messages as received from the user; 0 - successful 1 - failed
	 * fetching categories
	 * 
	 * @return int - status
	 */
	public static int processMessages() {
		Log.d(CLASS_TAG,
				"processMessages(): Process text messages as received from the user's phone");
		List<MessagesModel> listMessages = new ArrayList<MessagesModel>();
		String messageUuid = "";
		int status = 1;
		MessagesModel messages = new MessagesModel();
		listMessages.add(messages);

		// check if messageId is actually initialized
		if (smsMap.get("messagesUuid") != null) {
			messageUuid = smsMap.get("messagesUuid");
		}

		messages.setMessageUuid(messageUuid);
		messages.setMessageFrom(smsMap.get("messagesFrom"));
		messages.setMessage(smsMap.get("messagesBody"));
		messages.setMessageDate(smsMap.get("messagesDate"));

		if (listMessages != null) {
			MessagesModel model = new MessagesModel();
			model.listMessages = listMessages;
			model.save();

			status = 0;
		}
		return status;

	}

	/**
	 * Performs a task based on what callback URL tells it.
	 * 
	 * @param Context
	 *            context - the activity calling this method.
	 * @return void
	 */
	public void performTask(String urlSecret) {
		Log.d(CLASS_TAG, "performTask");

		this.urlSecret = urlSecret;

		// validate configured url
		int status = validateCallbackUrl(url);
		String response = "";
		String task = "";
		boolean success;

		if (status == 1) {
			showToast(context, R.string.no_configured_url);
		} else if (status == 2) {
			showToast(context, R.string.invalid_url);
		} else if (status == 3) {
			showToast(context, R.string.no_connection);
		} else {

			StringBuilder uriBuilder = new StringBuilder(url);

			uriBuilder.append("?task=send");

			try {
				RestHttpClient client = new RestHttpClient(uriBuilder.toString());
				client.execute(RestHttpClient.RequestMethod.GET);
				success = processResponse(
					client.getResponse(),
					client.getResponseCode()
				);
				if (!success) {
					showToast(context, R.string.no_connection);
				}
			} catch (Exception e) {
				debug(e);
				showToast(context, R.string.no_connection);
			}
		}
	}


	/**
	 * Does a HTTP request based on callback json configuration data
	 */
	private boolean processResponse(String resp, int statusCode) throws Exception {
		Log.d(CLASS_TAG, "processResponse(): response: " + resp);

		// any req in the chain fails, return false
		if (statusCode != 200 && statusCode != 201) {
			return false;
		}

		// load Prefs
		// for now just enable callbacks when reply from server is enabled
		Prefs.loadPreferences(context);

		boolean success = Util.getJsonSuccessStatus(resp);
		boolean callback = extractCallbackJSON(resp);

		// if we have a success payload anywhere in chain we succeeded.
		// also we should potentially send out responses.
		if (success) {
			responseSuccess = true;
			if (Prefs.enableReplyFrmServer) {
				sendResponseFromServer(resp);
			}
		}

		JSONObject cb;

		try {
			cb = new JSONObject(resp).getJSONObject("callback");
		} catch(Exception e) {
			// callback is optional
			debug(e);
			return responseSuccess;
		}

		try {
			String url = getCallbackURL(cb);
			String method = getCallbackMethod(cb);
			JSONObject headers = getCallbackHeaders(cb);
			RestHttpClient client = new RestHttpClient(url);

			Iterator<String> iter = headers.keys();
			while (iter.hasNext()) {
				String k = iter.next();
				client.addHeader(k, headers.getString(k));
			}

			if (method.equals("POST")) {
				client.setEntity(getCallbackData(cb));
				client.execute(RestHttpClient.RequestMethod.POST);
			} else if (method.equals("PUT")) {
				client.setEntity(getCallbackData(cb));
				client.execute(RestHttpClient.RequestMethod.PUT);
			} else {
				client.execute(RestHttpClient.RequestMethod.GET);
			}

			return processResponse(
				client.getResponse(),
				client.getResponseCode()
			);
		} catch (Exception e) {
			throw e;
		}
	}

	/**
	 * Extract callback JSON data
	 * 
	 * @apram json_data - The json data to be formatted.
	 * @return boolean
	 */
	private static boolean extractCallbackJSON(String json_data) {
		Log.d(CLASS_TAG, "extractCallbackJSON(): Extracting callback JSON data" + json_data);
		try {
			JSONObject test = new JSONObject(json_data).getJSONObject("callback");
			return true;
		} catch (JSONException e) {
			debug(e);
			return false;
		}
	}


	/**
	 * @param JSONObject callback - JSONObject representing the callback 
	 * @return String url - The URL from the callback response
	 */
	private static String getCallbackURL(JSONObject callback) {
		Log.d(CLASS_TAG, "getCallbackURL:");
		try {
			JSONObject options = callback.getJSONObject("options");
			String host = options.getString("host");
			String port = options.getString("port");
			String path = options.getString("path");
			String url = "";
			if (port == "null" || TextUtils.isEmpty(port)) {
				url = "http://" + host + path;
			} else if (port == "443") {
				url = "https://" + host + path;
			} else {
				url = "http://" + host + ":" + port + path;
			}
			Log.d(CLASS_TAG, "callback URL is: " + url);
			return url;
		} catch (JSONException e) {
			debug(e);
		}
		return null;
	};

	/**
	 * @param JSONObject callback - JSONObject representing the callback 
	 * @return String method - The method string from the callback options
	 */
	private static String getCallbackMethod(JSONObject callback) {
		Log.d(CLASS_TAG, "getCallbackMethod()");
		try {
			JSONObject options = callback.getJSONObject("options");
			Log.d(CLASS_TAG, "getCallbackMethod: options" + options);
			return options.getString("method");
		} catch (JSONException e) {
			debug(e);
		}
		return null;
	};

	/**
	 * @param JSONObject callback - JSONObject representing the callback object
	 *
	 * @return String data - The string value of the data property from the
	 * callback object.  The data attribute can be a string or valid JSON
	 * object.  
	 *
	 */
	private static String getCallbackData(JSONObject callback) {
		Log.d(CLASS_TAG, "getCallbackData()");
		try {
			return callback.getJSONObject("data").toString();
		} catch (JSONException e) {
			try {
				return callback.getString("data");
			} catch (JSONException f) {
				debug(f);
			}
		}
		return null;
	};

	/**
	 * @param JSONObject callback - JSONObject representing the callback 
	 * @return JSONObject headers - The headers object of the callback json
	 */
	private static JSONObject getCallbackHeaders(JSONObject callback) {
		Log.d(CLASS_TAG, "getCallbackHeaders()");
		try {
			JSONObject options = callback.getJSONObject("options");
			JSONObject headers = options.getJSONObject("headers");
			return headers;
		} catch (JSONException e) {
			debug(e);
		}
		return null;
	};
}
