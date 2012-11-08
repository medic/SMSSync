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
import android.text.TextUtils;
import android.util.Log;

/**
 * @author eyedol
 * 
 */
public class MessageSyncUtil extends Util {

	private Context context;

	private String url;

	private static JSONObject jsonObject;

	private static JSONArray jsonArray;

	private static final String CLASS_TAG = MessageSyncUtil.class.getSimpleName();

	private ProcessSms processSms;

	public MessageSyncUtil(Context context, String url) {
		this.context = context;
		this.url = url;
		processSms = new ProcessSms(context);
	}

	private static String formatDate(String date) {
		try {

			return Util.formatDateTime(Long.parseLong(date), "MM-dd-yy kk:mm");
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Posts received SMS to a configured callback URL.
	 * 
	 * @return boolean
	 */
	public boolean postToAWebService(String from, String message,
			String sentTimestamp, String messageID, String secret) {

		Prefs.loadPreferences(context);

		if (TextUtils.isEmpty(url)) { return false; }

		try {

			RestHttpClient client = new RestHttpClient(url);

			client.addParam("secret", secret);
			client.addParam("from", from);
			client.addParam("message", message);
			client.addParam("message_id", messageID);
			if (formatDate(sentTimestamp) != null) {
				client.addParam("sent_timestamp", sentTimestamp);
			}
			client.addParam("sent_to", getPhoneNumber(context));
			client.execute(RestHttpClient.RequestMethod.POST);
			int statusCode = client.getResponseCode();
			String resp = client.getResponse();

			if (statusCode == 200 || statusCode == 201) {
				boolean success = Util.extractPayloadJSON(resp);

				if (success) {
					// auto response message is enabled to be received from the
					// server.
					if (Prefs.enableReplyFrmServer) {
						if (Prefs.enableHttpCallbacks) {
							processResponseCallback(resp);
						} else {
							sendResponseFromServer(resp);
						}
					}
				}

				return success;
			}

			return false;

		} catch (Exception e) {
			Log.e(CLASS_TAG, "Exception: " + e.getMessage());
			e.printStackTrace();
			return false;
		}

	}

	/**
	 * Pushes pending messages to the configured URL.
	 * 
	 * @param int messageId - Sync by Id - 0 for no ID > 0 to for an id
	 * @param String
	 *            url The sync URL to push the message to.
	 * @param String
	 *            secret The secret key as set on the server.
	 * 
	 * @return int
	 */
	public int snycToWeb(int messageId, String secret) {
		Log.b(CLASS_TAG, "syncToWeb(): push pending messages to the Sync URL");
		MessagesModel model = new MessagesModel();
		List<MessagesModel> listMessages = new ArrayList<MessagesModel>();
		// check if it should sync by id
		if (messageId > 0) {
			model.loadById(messageId);
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
							String.valueOf(messages.getMessageId()))) {

					// / if it successfully pushes message, delete message
					// from db
					new MessagesModel().deleteMessagesById(messages
							.getMessageId());
					deleted = 0;
				} else {
					deleted = 1;
				}

			}
		}

		return deleted;

	}

	/**
	 * Sends messages received from the server as SMS.
	 * 
	 * @param String
	 *            response - the response from the server.
	 */
	public void sendResponseFromServer(String response) {
		Log.d(CLASS_TAG, "performResponseFromServer(): " + " response:"
				+ response);

		if (!TextUtils.isEmpty(response) && response != null) {

			try {

				jsonObject = new JSONObject(response);
				JSONObject payloadObject = jsonObject.getJSONObject("payload");

				if (payloadObject != null) {

					jsonArray = payloadObject.getJSONArray("messages");

					for (int index = 0; index < jsonArray.length(); ++index) {
						jsonObject = jsonArray.getJSONObject(index);
						new Util().log("Send sms: To: "
								+ jsonObject.getString("to") + "Message: "
								+ jsonObject.getString("message"));

						processSms.sendSms(jsonObject.getString("to"),
								jsonObject.getString("message"));
					}

				}
			} catch (JSONException e) {
				new Util().log(CLASS_TAG, "Error: " + e.getMessage());
				showToast(context, R.string.no_task);
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
		int messageId = 0;
		int status = 1;
		MessagesModel messages = new MessagesModel();
		listMessages.add(messages);

		// check if messageId is actually initialized
		if (smsMap.get("messagesId") != null) {
			messageId = Integer.parseInt(smsMap.get("messagesId"));
		}

		messages.setMessageId(messageId);
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
		Log.d(CLASS_TAG, "performTask(): perform a task");
		// load Prefs
		Prefs.loadPreferences(context);

		// validate configured url
		int status = validateCallbackUrl(url);
		String response = "";

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
				response = client.getResponse();
				Log.d(CLASS_TAG, "TaskCheckResponse: " + response);
			} catch (Exception e) {
				Log.e(CLASS_TAG, "Exception: " + e.getMessage());
				e.printStackTrace();
			}
			String task = "";
			String secret = "";
			if (!TextUtils.isEmpty(response) && response != null) {

				try {

					jsonObject = new JSONObject(response);
					JSONObject payloadObject = jsonObject
						.getJSONObject("payload");

					if (payloadObject != null) {
						task = payloadObject.getString("task");
						secret = payloadObject.getString("secret");
						if ((task.equals("send")) && (secret.equals(urlSecret))) {
							jsonArray = payloadObject.getJSONArray("messages");

							for (int index = 0; index < jsonArray.length(); ++index) {
								jsonObject = jsonArray.getJSONObject(index);

								processSms.sendSms(jsonObject.getString("to"),
										jsonObject.getString("message"));
							}

						} else {
							// no task enabled on the callback url.
							showToast(context, R.string.no_task);
						}

					} else {

						showToast(context, R.string.no_task);
					}

				} catch (JSONException e) {
					Log.e(CLASS_TAG, "JSONException: " + e.getMessage());
					e.printStackTrace();
					showToast(context, R.string.no_task);
				}
			}
		}
	}


	/**
	 * Does a HTTP request based on callback json configuration data
	 * 
	 * @param resp - String string 
	 */
	public void processResponseCallback(String resp) {
		Log.d(CLASS_TAG, "processResponseCallback(): response: " + resp);
		try {
			boolean success = extractPayloadJSON(resp);
			if (success) {
				sendResponseFromServer(resp);
			}
			boolean callback = extractCallbackJSON(resp);
			if (callback) {
				JSONObject cb = new JSONObject(resp).getJSONObject("callback");
				String url = getCallbackURL(cb);
				String method = getCallbackMethod(cb);
				RestHttpClient client = new RestHttpClient(url);
				client.setEntity(getCallbackData(cb));

				JSONObject headers = getCallbackHeaders(cb);
				Iterator<String> iter = headers.keys();
				while (iter.hasNext()) {
					String k = iter.next();
					client.addHeader(k, headers.getString(k));
				}

				if (method.equals("POST")) {
					client.execute(RestHttpClient.RequestMethod.POST);
				} else if (method.equals("PUT")) {
					client.execute(RestHttpClient.RequestMethod.PUT);
				} else {
					client.execute(RestHttpClient.RequestMethod.GET);
				}

				processResponseCallback(client.getResponse());
			}
		} catch (Exception e) {
			Log.e(CLASS_TAG, "Exception: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Extract callback JSON data
	 * 
	 * @apram json_data - The json data to be formatted.
	 * @return boolean
	 */
	public static boolean extractCallbackJSON(String json_data) {
		Log.i(CLASS_TAG, "extractCallbackJSON(): Extracting callback JSON data" + json_data);
		try {
			JSONObject test = new JSONObject(json_data).getJSONObject("callback");
			return true;
		} catch (JSONException e) {
			Log.e(CLASS_TAG, "JSONException: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}


	/**
	 * @param JSONObject callback - JSONObject representing the callback 
	 * @return String url - The URL from the callback response
	 */
	public static String getCallbackURL(JSONObject callback) {
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
			Log.e(CLASS_TAG, "JSONException: " + e.getMessage());
		}
		return null;
	};

	/**
	 * @param JSONObject callback - JSONObject representing the callback 
	 * @return String method - The method string from the callback options
	 */
	public static String getCallbackMethod(JSONObject callback) {
		Log.d(CLASS_TAG, "getCallbackMethod()");
		try {
			JSONObject options = callback.getJSONObject("options");
			Log.d(CLASS_TAG, "getCallbackMethod: options" + options);
			return options.getString("method");
		} catch (JSONException e) {
			Log.e(CLASS_TAG, "JSONException: " + e.getMessage());
		}
		return null;
	};

	/**
	 * @param JSONObject callback - JSONObject representing the callback 
	 * @return String data - The data/entity of the callback json
	 */
	public static String getCallbackData(JSONObject callback) {
		Log.d(CLASS_TAG, "getCallbackData()");
		try {
			String data = callback.getJSONObject("data").toString();
			return data;
		} catch (JSONException e) {
			Log.e(CLASS_TAG, "JSONException: " + e.getMessage());
		}
		return null;
	};

	/**
	 * @param JSONObject callback - JSONObject representing the callback 
	 * @return JSONObject headers - The headers object of the callback json
	 */
	public static JSONObject getCallbackHeaders(JSONObject callback) {
		Log.d(CLASS_TAG, "getCallbackHeaders()");
		try {
			JSONObject options = callback.getJSONObject("options");
			JSONObject headers = options.getJSONObject("headers");
			return headers;
		} catch (JSONException e) {
			Log.e(CLASS_TAG, "JSONException: " + e.getMessage());
		}
		return null;
	};
}
