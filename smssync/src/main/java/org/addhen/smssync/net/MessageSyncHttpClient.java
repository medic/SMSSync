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

import android.content.Context;
import android.content.res.Resources;

import org.addhen.smssync.R;
import org.addhen.smssync.models.Message;
import org.addhen.smssync.models.SyncUrl;
import org.addhen.smssync.net.SyncScheme.SyncDataFormat;
import org.addhen.smssync.net.SyncScheme.SyncDataKey;
import org.addhen.smssync.net.SyncScheme.SyncMethod;
import org.addhen.smssync.util.DataFormatUtil;
import org.addhen.smssync.util.Logger;

import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;

import java.io.IOException;
import java.util.Locale;

public class MessageSyncHttpClient extends MainHttpClient {

    private SyncUrl syncUrl;

    private String serverError;

    private String clientError;

    public MessageSyncHttpClient(
        Context context, SyncUrl syncUrl, Message message, String toNumber
    ) {
        super(syncUrl.getUrl(), context);
        this.syncUrl = syncUrl;
        initRequest(message, toNumber);
    }

    private void initRequest(Message message, String toNumber) {

        SyncScheme syncScheme = syncUrl.getSyncScheme();
        SyncMethod method = syncScheme.getMethod();
        SyncDataFormat format = syncScheme.getDataFormat();

        setHeader("Content-Type", syncScheme.getContentType());
        addParam(syncScheme.getKey(SyncDataKey.SECRET), syncUrl.getSecret());
        addParam(syncScheme.getKey(SyncDataKey.FROM), message.getFrom());
        addParam(syncScheme.getKey(SyncDataKey.MESSAGE), message.getBody());
        addParam(
            syncScheme.getKey(SyncDataKey.SENT_TIMESTAMP), message.getTimestamp()
        );
        addParam(syncScheme.getKey(SyncDataKey.SENT_TO), toNumber);
        addParam(syncScheme.getKey(SyncDataKey.MESSAGE_ID), message.getUuid());

        try {
            setHttpEntity(format);
        } catch (Exception e) {
            log("Failed to set request body", e);
            setClientError("Failed to format request body");
        }

        try {
            switch (method){
                case POST:
                    setMethod("POST");
                    break;
                case PUT:
                    setMethod("PUT");
                    break;
                default:
                    log("Invalid server method");
                    setClientError("Failed to set request method.");
            }
        } catch (Exception e) {
            log("failed to set request method.", e);
            setClientError("Failed to set request method.");
        }

    }

    public Response postSmsToWebService() throws IOException, JSONException {
        try {
            return execute();
        } catch (IOException e) {
            log("Request failed", e);
            setClientError("Request failed. " + e.getMessage());
            throw e;
        } catch (JSONException e) {
            log("Request failed", e);
            setClientError("Request failed. " + e.getMessage());
            throw e;
        }
    }

    /**
     *
     * Get HTTP Entity populated with data in a format specified by the current sync scheme
     *
     * @param SyncDataFormat format
     */
    private void setHttpEntity(SyncDataFormat format) throws Exception {

        switch (format){
            case JSON:
                setEntity(DataFormatUtil.makeJSONString(getParams()));
                log("setHttpEntity format JSON");
                break;
            case XML:
                //TODO: Make parent node URL specific as well
                setEntity(DataFormatUtil.makeXMLString(getParams(), "payload", HTTP.UTF_8));
                log("setHttpEntity format XML");
                break;
            case YAML:
                setEntity(DataFormatUtil.makeYAMLString(getParams()));
                log("setHttpEntity format YAML");
                break;
            case URLEncoded:
                log("setHttpEntity format URLEncoded");
                setEntity(new UrlEncodedFormEntity(getParams(), HTTP.UTF_8));
                break;
            default:
                throw  new Exception("Invalid data format");
        }

    }

    public String getClientError() {
        return this.clientError;
    }

    public String getServerError() {
        return this.serverError;
    }

    public void setClientError(String error) {
        log("Client error " + error);
        Resources res = context.getResources();
        this.serverError = String.format(
            Locale.getDefault(), 
            "%s", 
            String.format(
                res.getString(R.string.sending_failed_custom_error),
                error
            )
        );
    }

    public void setServerError(String error, int statusCode) {
        log("Server error " + error);
        Resources res = context.getResources();
        this.serverError = String.format(
            Locale.getDefault(), 
            "%s, %s ", 
            String.format(
                res.getString(R.string.sending_failed_custom_error),
                error
            ), 
            String.format(
                res.getString(R.string.sending_failed_http_code),
                statusCode
            )
        );
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
