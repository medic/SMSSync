package org.addhen.smssync.messages;

import org.addhen.smssync.Prefs;
import org.addhen.smssync.R;
import org.addhen.smssync.models.Filter;
import org.addhen.smssync.models.Message;
import org.addhen.smssync.models.SyncUrl;
import org.addhen.smssync.net.MainHttpClient;
import org.addhen.smssync.net.MessageSyncHttpClient;
import org.addhen.smssync.util.Logger;
import org.addhen.smssync.util.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
 
import android.content.Context;
import android.text.TextUtils;

import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;

import static org.addhen.smssync.messages.ProcessSms.PENDING;

/**
 * Process messages
 */
public class ProcessMessage {

    private static final String CLASS_TAG = ProcessMessage.class.getSimpleName();

    private static final int ACTIVE_SYNC_URL = 1;

    private static JSONArray jsonArray;

    private Context context;

    private ProcessSms processSms;

    private String errorMessage;

    private String urlSecret;

    private int callbackCount;

    public ProcessMessage(Context context) {
        this.context = context;
        processSms = new ProcessSms(context);
    }

    /**
     * Save SMS that failed to be sent to the sync Url to the the db.
     */
    public boolean saveMessage(Message message) {
        Logger.log(CLASS_TAG,
                "saveMessage(): save text messages as received from the user's phone");
        return message.save();
    }

    /**
     * Sync received SMS to a configured sync URL.
     *
     * @param message The sms to be sync
     * @param syncUrl The sync URL to post the message to
     * @return boolean
     */
    public boolean syncReceivedSms(Message message, SyncUrl syncUrl) {
        Logger.log(CLASS_TAG, "syncReceivedSms(): Post received SMS to configured URL:" +
                message.toString() + " SyncUrlFragment: " + syncUrl.toString());

        MessageSyncHttpClient client = new MessageSyncHttpClient(
            context, syncUrl, message, Util.getPhoneNumber(context)
        );
        final boolean posted = client.postSmsToWebService();
        
        if (posted) {
            smsServerResponse(client.getServerSuccessResp());
        } else {
            String clientError = client.getClientError();
            String serverError = client.getServerError();
            if (clientError != null) {
                setErrorMessage(clientError);
            } else if (serverError != null) {
                setErrorMessage(serverError);
            }
        }

        return posted;
    }

    /**
     * Sync pending messages to the configured sync URL.
     *
     * @param uuid The message uuid
     */
    public boolean syncPendingMessages(String uuid) {
        Logger.log(CLASS_TAG, "syncPendingMessages: push pending messages to the Sync URL" + uuid);
        Message messageModel = new Message();
        List<Message> listMessages;
        // check if it should sync by id
        if (!TextUtils.isEmpty(uuid)) {
            messageModel.loadByUuid(uuid);

        } else {
            messageModel.load();
        }
        listMessages = messageModel.getMessageList();

        if (listMessages != null && listMessages.size() > 0) {

            for (Message message : listMessages) {
                if (routeMessage(message)) {
                    messageModel.deleteMessagesByUuid(message.getUuid());
                }

            }
            return true;
        }

        return false;

    }

    /**
     * Send the response received from the server as SMS
     *
     * @param response The JSON string response from the server.
     */
    public void smsServerResponse(String response) {
        Logger.log(CLASS_TAG, "performResponseFromServer(): " + " response:"
                + response);
        if (!Prefs.enableReplyFrmServer) {
            return;
        }

        if (!TextUtils.isEmpty(response)) {

            try {

                JSONObject jsonObject = new JSONObject(response);
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
            }
        }
    }

    public void performTask(SyncUrl syncUrl) {
        Logger.log(CLASS_TAG, "performTask(): perform a task");

        // load Prefs
        Prefs.loadPreferences(context);

        // validate configured url
        int status = Util.validateCallbackUrl(syncUrl.getUrl());

        Logger.log(CLASS_TAG, "status "+ status);
        Logger.log(CLASS_TAG, "syncURL "+syncUrl.getUrl());
        if (status == 1) {
            setErrorMessage(context.getString(R.string.no_configured_url));
            return;
        }
        if (status == 2) {
            setErrorMessage(context.getString(R.string.invalid_url));
            return;
        }
        if (status == 3) {
            setErrorMessage(context.getString(R.string.no_connection));
            return;
        }

        StringBuilder uriBuilder = new StringBuilder(syncUrl.getUrl());
        urlSecret = syncUrl.getSecret();
        uriBuilder.append("?task=send");


        if (!TextUtils.isEmpty(urlSecret)) {
            String urlSecretEncoded = urlSecret;
            uriBuilder.append("&secret=");
            try {
                urlSecretEncoded = URLEncoder.encode(urlSecret, "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                Logger.log(CLASS_TAG, e.getMessage());
            }
            uriBuilder.append(urlSecretEncoded);
            syncUrl.setUrl(uriBuilder.toString());
        }

        MainHttpClient client = new MainHttpClient(syncUrl.getUrl(), context);
        String response = null;
        try {
            client.execute();
            response = client.getResponse();
        } catch (Exception e) {
            setErrorMessage(e.getMessage());
            return;
        }

        // nothing to do if we have no response 
        if (response == null || TextUtils.isEmpty(response)) {
            setErrorMessage("Failed to get response.");
            return;
        }

        // process callback and payload properties
        JSONObject json = null;
        JSONObject payload = null;
        JSONObject callback = null;

        try {
            json = new JSONObject(response);
            payload = json.getJSONObject("payload");
            callback = json.getJSONObject("callback");
        } catch (JSONException e) {
            Logger.log(CLASS_TAG, e.getMessage());
        }

        try {
            processPayload(payload);
        } catch (Exception e) {
            Logger.log(CLASS_TAG, e.getMessage());
        }

        try {
            processCallback(callback);
        } catch (Exception e) {
            Logger.log(CLASS_TAG, e.getMessage());
        }

    }

    /**
     * Processes the incoming SMS to figure out how to exactly to route the message. If it fails to
     * be synced online, cache it and queue it up for the scheduler to process it.
     *
     * @param message The sms to be routed
     * @return boolean
     */
    public boolean routeSms(Message message) {
        Logger.log(CLASS_TAG, "routeSms uuid: " + message.toString());

        // is SMSSync service running?
        if (Prefs.enabled) {
            // send auto response from phone not server.
            if (Prefs.enableReply) {
                // send auto response as SMS to user's phone
                processSms.sendSms(message.getFrom(), Prefs.reply);
            }

            if (routeMessage(message)) {

                // Delete messages from message app's inbox, only
                // when SMSSync has that feature turned on
                if (Prefs.autoDelete) {
                    processSms.delSmsFromInbox(message.getBody(), message.getFrom());
                }
                return true;
            } else {
                //only save to pending when the number is not blacklisted
                if(!Prefs.enableBlacklist){
                    saveMessage(message);
                }
            }
        }
        return false;

    }

    public boolean routePendingMessage(Message message) {
        if (routeMessage(message)) {
            return message.deleteMessagesByUuid(message.getUuid());
        }
        return false;
    }

    /**
     *
     * @param message
     * @param syncUrl
     * @return
     */
    private boolean processMessage(Message message, SyncUrl syncUrl) {
        boolean posted = false;

        // process filter text (keyword or RegEx)
        if (!TextUtils.isEmpty(syncUrl.getKeywords())) {
            String filterText = syncUrl.getKeywords();
            if (processSms.filterByKeywords(message.getBody(), filterText)
                    || processSms.filterByRegex(message.getBody(), filterText)) {
                Logger.log(CLASS_TAG, syncUrl.getUrl());

                posted = syncReceivedSms(message, syncUrl);
                if (!posted) {
                    // Note: HTTP Error code or custom error message
                    // will have been shown already

                    // attempt to make a data connection to sync
                    // the failed messages.
                    Util.connectToDataNetwork(context);

                } else {

                    processSms.postToSentBox(message, PENDING);
                }

            }

        } else { // there is no filter text set up on a sync URL
            posted = syncReceivedSms(message, syncUrl);
            setErrorMessage(syncUrl.getUrl());
            if (!posted) {

                // attempt to make a data connection to the sync
                // url
                Util.connectToDataNetwork(context);

            } else {

                processSms.postToSentBox(message, PENDING);
            }
        }
        return posted;
    }

    /**
     * Routes both incoming SMS and pending messages.
     *
     * @param message The message to be rounted
     */
    private boolean routeMessage(Message message) {

        // load preferences
        Prefs.loadPreferences(context);
        boolean posted = false;
        // is SMSSync service running?
        if (!Prefs.enabled || !Util.isConnected(context)) {
            return posted;
        }
        SyncUrl model = new SyncUrl();
        Filter filters = new Filter();
        // get enabled Sync URLs
        for (SyncUrl syncUrl : model.loadByStatus(ACTIVE_SYNC_URL)) {
            // white listed is enabled
            if (Prefs.enableWhitelist) {
                filters.loadByStatus(Filter.Status.WHITELIST);
                for (Filter filter : filters.getFilterList()) {
                    if (filter.getPhoneNumber().equals(message.getFrom())) {
                        return processMessage(message, syncUrl);
                    }
                }
                return false;
            }

            if (Prefs.enableBlacklist) {

                filters.loadByStatus(Filter.Status.BLACKLIST);
                for (Filter filter : filters.getFilterList()) {

                    if (filter.getPhoneNumber().equals(message.getFrom())) {
                        Logger.log("message", " from:"+message.getFrom()+" filter:"+ filter.getPhoneNumber());
                        return false;
                    }
                }
            } else {

                return processMessage(message, syncUrl);
            }

        }

        return posted;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        Logger.log(CLASS_TAG, errorMessage);
    }


    /**
     * Send messages in response payload
     */
    private void processPayload(JSONObject payload) throws Exception {

        if (payload == null) {
            return;
        }

        try {
            String task = payload.getString("task");
            boolean secretOk = TextUtils.isEmpty(urlSecret) ||
                urlSecret.equals(payload.getString("secret"));
            if (secretOk && task.equals("send")) {
                jsonArray = payload.getJSONArray("messages");
                JSONObject jsonObject = null;
                for (int index = 0; index < jsonArray.length(); ++index) {
                    jsonObject = jsonArray.getJSONObject(index);
                    processSms.sendSms(
                        jsonObject.getString("to"),
                        jsonObject.getString("message")
                    );
                }
            } else {
                setErrorMessage(context.getString(R.string.no_task));
            }

        } catch (JSONException e) {
            setErrorMessage(e.getMessage());
        }
    }


    /**
     * Make additional http requests based on response.
     */
    private void processCallback(JSONObject cb) throws Exception {
        // load Prefs
        Prefs.loadPreferences(context);

        if (cb == null) {
            return;
        }

        try {
            String url = getCallbackURL(cb);
            String method = getCallbackMethod(cb);
            JSONObject headers = getCallbackHeaders(cb);

            MainHttpClient client = new MainHttpClient(url, context);
            client.setMethod(method);

            // add headers
            Iterator<String> iter = headers.keys();
            while (iter.hasNext()) {
                String k = iter.next();
                client.setHeader(k, headers.getString(k));
            }

            if (method.equals("POST") || method.equals("PUT")) {
                client.setEntity(getCallbackData(cb));
            }

            client.execute();
            processResponse(client.getResponse(), client.getResponseCode());

        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Does a HTTP request based on callback json configuration data
     */
    private void processResponse(String response, int statusCode) throws Exception {

        if (callbackCount > 10) {
            return;
        } else {
            callbackCount++;
        }

        // any req in the chain fails, return
        if (statusCode != 200 && statusCode != 201) {
            return;
        }

        // load Prefs
        // for now just enable callbacks when reply from server is enabled
        Prefs.loadPreferences(context);

        if (!Prefs.enableReplyFrmServer) {
            return;
        }

        // continue processing payload and callback properties
        JSONObject json = null;
        JSONObject payload = null;
        JSONObject callback = null;

        try {
            json = new JSONObject(response);
            payload = json.getJSONObject("payload");
            callback = json.getJSONObject("callback");
        } catch (JSONException e) {
            // throw everything other than json parse failed
        }

        try {
            processPayload(payload);
        } catch (Exception e) {
            Logger.log(CLASS_TAG, e.getMessage());
        }

        try {
            processCallback(callback);
        } catch (Exception e) {
            Logger.log(CLASS_TAG, e.getMessage());
        }

    }


    /**
     * Extract callback JSON data
     * 
     * @apram json_data - The json data to be formatted.
     * @return boolean
     */
    private boolean extractCallbackJSON(String json_data) {
        Logger.log(CLASS_TAG, "extractCallbackJSON(): Extracting callback JSON data" + json_data);
        try {
            JSONObject test = new JSONObject(json_data).getJSONObject("callback");
            return true;
        } catch (JSONException e) {
            setErrorMessage(e.getMessage());
            return false;
        }
    }


    /**
     * @param JSONObject callback - JSONObject representing the callback 
     * @return String url - The URL from the callback response
     */
    private String getCallbackURL(JSONObject callback) {
        Logger.log(CLASS_TAG, "getCallbackURL:");
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
            Logger.log(CLASS_TAG, "callback URL is: " + url);
            return url;
        } catch (JSONException e) {
            setErrorMessage(e.getMessage());
        }
        return null;
    };

    /**
     * @param JSONObject callback - JSONObject representing the callback 
     * @return String method - The method string from the callback options
     */
    private String getCallbackMethod(JSONObject callback) {
        Logger.log(CLASS_TAG, "getCallbackMethod()");
        try {
            JSONObject options = callback.getJSONObject("options");
            Logger.log(CLASS_TAG, "getCallbackMethod: options" + options);
            return options.getString("method");
        } catch (JSONException e) {
            setErrorMessage(e.getMessage());
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
    private String getCallbackData(JSONObject callback) {
        Logger.log(CLASS_TAG, "getCallbackData()");
        try {
            return callback.getJSONObject("data").toString();
        } catch (JSONException e) {
            try {
                return callback.getString("data");
            } catch (JSONException f) {
                setErrorMessage(f.getMessage());
            }
        }
        return null;
    };

    /**
     * @param JSONObject callback - JSONObject representing the callback 
     * @return JSONObject headers - The headers object of the callback json
     */
    private JSONObject getCallbackHeaders(JSONObject callback) {
        Logger.log(CLASS_TAG, "getCallbackHeaders()");
        try {
            JSONObject options = callback.getJSONObject("options");
            JSONObject headers = options.getJSONObject("headers");
            return headers;
        } catch (JSONException e) {
            setErrorMessage(e.getMessage());
        }
        return null;
    };

}
