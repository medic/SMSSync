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

package org.addhen.smssync.messages;

import org.addhen.smssync.MainApplication;
import org.addhen.smssync.Prefs;
import org.addhen.smssync.database.Messages;
import org.addhen.smssync.models.Message;
import org.addhen.smssync.util.Logger;
import org.addhen.smssync.util.ServicesConstants;

import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * This class has the main logic to dispatch the messages that comes to the device. It decides where
 * to post the messages to depending on the status of the device. If the message fails to send to
 * the configured web service, it saves them in the pending list and when it succeeds it saves them
 * in the sent list.
 *
 * @author eyedol
 */
public class ProcessSms {

    private static final Uri MMS_SMS_CONTENT_URI = Uri
            .parse("content://mms-sms/");

    private static final Uri CONVERSATION_CONTENT_URI = Uri.withAppendedPath(
            MMS_SMS_CONTENT_URI, "conversations");

    private static final String SMS_CONTENT_URI = "content://sms/conversations/";

    private static final String SMS_CONTENT_INBOX = "content://sms/inbox";

    public static final int PENDING = 0;

    private static final String CLASS_TAG = ProcessSms.class.getSimpleName();

    public static final int TASK = 1;

    private Context context;

    public ProcessSms(Context context) {
        this.context = context;
    }

    /**
     * Tries to locate the message id (from the system database), given the message thread id and
     * the timestamp of the message.
     *
     * @param threadId  - The message's thread ID.
     * @param timestamp - The timestamp of the message.
     * @return the message id
     */
    public long findMessageId(long threadId, long timestamp) {
        Logger.log(CLASS_TAG,
                "findMessageId(): get the message id using thread id and timestamp: threadId: "
                        + threadId + " timestamp: " + timestamp);
        long id = 0;
        if (threadId > 0) {
            Cursor cursor = context.getContentResolver().query(
                    ContentUris.withAppendedId(CONVERSATION_CONTENT_URI,
                            threadId),
                    new String[]{
                            "_id", "date", "thread_id"
                    },
                    "date=" + timestamp, null, "date desc");

            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        id = cursor.getLong(0);
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        return id;
    }

    /**
     * Filter message string for particular keywords
     *
     * @param message    The message to be tested against the keywords
     * @param filterText A CSV string listing keywords to match against message
     * @return boolean
     */
    public boolean filterByKeywords(String message, String filterText) {
        String[] keywords = filterText.split(",");

        for (String keyword : keywords) {
            if (message.toLowerCase(Locale.ENGLISH)
                    .contains(keyword.toLowerCase(Locale.ENGLISH).trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Filter message string for RegEx match
     *
     * @param message    The message to be tested against the RegEx
     * @param filterText A string representing the regular expression to test against.
     * @return boolean
     */
    public boolean filterByRegex(String message, String filterText) {
        Pattern pattern = null;
        try {
            pattern = Pattern.compile(filterText, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            // invalid RegEx
            return true;
        }
        Matcher matcher = pattern.matcher(message);

        return (matcher.find());
    }

    /**
     * Import messages from the messages app's table and puts them in SMSSync's outbox table. This
     * will allow messages the imported messages to be sync'd to the configured Sync URL.
     *
     * @return {@code true} for success, {@code false} otherwise
     */
    public boolean importMessages() {
        Logger.log(CLASS_TAG,
                "importMessages(): import messages from messages app");
        Prefs.loadPreferences(context);
        Uri uriSms = Uri.parse(SMS_CONTENT_INBOX);
        uriSms = uriSms.buildUpon().appendQueryParameter("LIMIT", "10").build();
        String[] projection = {
                "_id", "address", "date", "body"
        };
        String messageDate;

        Cursor c = context.getContentResolver().query(uriSms, projection, null,
                null, "date DESC");

        if (c != null && c.getCount() > 0) {
            if (c.moveToFirst()) {

                do {
                    Message message = new Message();

                    messageDate = String.valueOf(c.getLong(c
                            .getColumnIndex("date")));
                    message.setTimestamp(messageDate);

                    message.setFrom(c.getString(c
                            .getColumnIndex("address")));
                    message.setBody(c.getString(c.getColumnIndex("body")));
                    message.setUuid(getUuid());
                    message.save();
                } while (c.moveToNext());
            }
            c.close();
            return true;
        } else {
            return false;
        }

    }

    /**
     * Tries to locate the thread id given the address (phone number or email) of the message
     * sender.
     *
     * @return the thread id, or {@code 0} if no thread was found
     */
    public long getThreadId(String body, String address) {
        Logger.log(CLASS_TAG, "getId(): thread id");
        Uri uriSms = Uri.parse(SMS_CONTENT_INBOX);

        StringBuilder sb = new StringBuilder();
        sb.append("address=" + DatabaseUtils.sqlEscapeString(address) + " AND ");
        sb.append("body=" + DatabaseUtils.sqlEscapeString(body));

        Cursor c = context.getContentResolver().query(uriSms, null, sb.toString(), null,
                "date DESC ");

        if (c != null) {
            if (c.getCount() > 0) {
                c.moveToFirst();
                long threadId = c.getLong(c.getColumnIndex("thread_id"));
                c.close();
                return threadId;
            }
        }

        return 0;
    }

    public String getUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Sends SMS to a number.
     *
     * @param sendTo - Number to send SMS to.
     * @param msg    - The message to be sent.
     */
    public void sendSms(String sendTo, String msg) {

        ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>();
        ArrayList<PendingIntent> deliveryIntents = new ArrayList<PendingIntent>();
        Logger.log(CLASS_TAG, "sendSms(): Sends SMS to a number: sendTo: "
                + sendTo + " message: " + msg);

        SmsManager sms = SmsManager.getDefault();
        ArrayList<String> parts = sms.divideMessage(msg);

        for (int i = 0; i < parts.size(); i++) {
            PendingIntent sentIntent = PendingIntent.getBroadcast(context, 0,
                    new Intent(ServicesConstants.SENT), 0);

            PendingIntent deliveryIntent = PendingIntent.getBroadcast(context,
                    0, new Intent(ServicesConstants.DELIVERED), 0);
            sentIntents.add(sentIntent);

            deliveryIntents.add(deliveryIntent);
        }

        if (PhoneNumberUtils.isGlobalPhoneNumber(sendTo)) {
            /*
             * sms.sendMultipartTextMessage(sendTo, null, parts, sentIntents,
             * deliveryIntents);
             */

            sms.sendMultipartTextMessage(sendTo, null, parts, sentIntents,
                    null);

            // Get current Time Millis
            final Long timeMills = System.currentTimeMillis();
            // Log to sent table
            Message message = new Message();
            message.setBody(msg);
            message.setTimestamp(timeMills.toString());
            message.setFrom(sendTo);
            postToSentBox(message, TASK);
        }
    }

    /**
     * Delete SMS from the message app inbox
     *
     * @param body    The message body
     * @param address The address / from
     */
    public void delSmsFromInbox(String body, String address) {
        Logger.log(CLASS_TAG, "delSmsFromInbox(): Delete SMS message app inbox");
        final long threadId = getThreadId(body, address);

        if (threadId >= 0) {
            context.getContentResolver().delete(
                    Uri.parse(SMS_CONTENT_URI + threadId), null, null);
        }
    }

    /**
     * Saves successfully sent messages into the db
     *
     * @param messageType the message type
     */
    public void postToSentBox(Message message, int messageType) {
        Logger.log(CLASS_TAG, "postToSentBox(): post message to sentbox");

        Messages messages = new Messages();

        String messageUuid = "";
        if (message.getUuid() != null) {
            messageUuid = message.getUuid();
        }
        messages.setMessageUuid(messageUuid);

        messages.setMessageFrom(message.getFrom());
        messages.setMessageBody(message.getBody());
        messages.setMessageDate(message.getTimestamp());
        messages.setMessageType(messageType);

        MainApplication.mDb.addSentMessages(messages);
    }
}
