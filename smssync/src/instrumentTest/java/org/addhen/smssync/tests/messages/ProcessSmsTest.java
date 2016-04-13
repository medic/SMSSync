package org.addhen.smssync.tests.messages;

import org.addhen.smssync.messages.ProcessSms;
import org.addhen.smssync.models.Message;
import org.addhen.smssync.tests.BaseTest;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.suitebuilder.annotation.Suppress;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Test process sms
 */
public class ProcessSmsTest extends BaseTest {
    private static final Uri SMS_CONTENT_INBOX = Uri.parse("content://sms/inbox");

    private static final String REGEX = "\\d{2}(am|pm)";

    private String longText;

    private ProcessSms mProcessSms;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        longText = "Hello, See you at tomorrow at the accra mall";
        mProcessSms = new ProcessSms(getContext());
    }

    @SmallTest
    @Suppress // Broken when code inherited
    public void testShouldFindMessageId() throws Exception {
        final String body = "foo bar";
        final String address = "1234";
        ContentValues values = new ContentValues();
        values.put("address", address);
        values.put("body", body);
        Uri uriSms  = getContext().getContentResolver()
                .insert(SMS_CONTENT_INBOX, values);

        assertNotNull("Could not add sms to sms inbox",uriSms);

        String[] projection = {
                "_id", "address", "date", "body"
        };

        Cursor c = getContext().getContentResolver().query(uriSms, projection, null,
                null, "date DESC");
        assertNotNull(c);
        c.moveToFirst();
        long timeStamp = c.getLong(c.getColumnIndex("date"));
        c.close();
        long threadId = mProcessSms.getThreadId(body, address);
        assertTrue("Could not find message ID ",mProcessSms.findMessageId(threadId,timeStamp) > 0);
        mProcessSms.delSmsFromInbox(body, address);
    }

    @SmallTest
    public void testShouldFilterTextByKeyword() throws Exception {
        final String keyword = "hello, accra, go, home , yes";
        final boolean filtered = mProcessSms.filterByKeywords(longText, keyword);
        assertTrue(filtered);
    }

    @SmallTest
    public void testShouldFailToFilterTextByKeyword() throws Exception {
        final String keyword = "foo, bar";
        final boolean filtered = mProcessSms.filterByKeywords(longText, keyword);
        assertFalse(filtered);
    }

    @SmallTest
    public void testShouldFilterTextByRegex() throws Exception {
        StringBuilder message = new StringBuilder(longText);
        message.append(" at 12pm");
        final boolean filtered = mProcessSms.filterByRegex(message.toString(), REGEX);
        assertTrue(" failed at " + message.toString(), filtered);
    }

    @SmallTest
    public void testShouldFailToFilterTextByRegex() throws Exception {
        final boolean filtered = mProcessSms.filterByRegex(longText, REGEX);
        assertFalse(filtered);
    }

    @SmallTest
    @Suppress // Broken when code inherited
    public void testShouldGetMessageThreadId() throws Exception {
        final String body = "foo bar";
        final String address = "123456789";
        ContentValues values = new ContentValues();
        values.put("address", address);
        values.put("body", body);
        assertNotNull("Could not add sms to sms inbox", getContext().getContentResolver()
                .insert(SMS_CONTENT_INBOX, values));

        final long msgThreadId = mProcessSms.getThreadId(body, address);
        assertTrue("Could not get sms thread Id", msgThreadId > 0);
        mProcessSms.delSmsFromInbox(body, address);

    }

    @SmallTest
    public void testShouldGetUuid() throws Exception {
        assertNotNullOrEmpty("Could not get UUID", mProcessSms.getUuid());
    }

    @SmallTest
    @Suppress // Broken when code inherited
    public void testShouldDeleteSmsFromSmsInbox() throws Exception {
        // given
        final String body = "foo bar";
        final String address = "123443";
        ContentValues values = new ContentValues();
        values.put("address", address);
        values.put("body", body);
        assertNotNull("Could not add sms to sms inbox", getContext().getContentResolver()
                .insert(SMS_CONTENT_INBOX, values));
        int oldCount = smsInboxCount();

        // when
        mProcessSms.delSmsFromInbox(body, address);

        // then
        assertEquals("Could not delete sms from sms inbox", oldCount - 1, smsInboxCount());
    }

    @SmallTest
    public void testShouldPostPendingMessageToSentInbox() throws Exception {
        Message message = new Message();
        message.setFrom("0243581806");
        message.setUuid(mProcessSms.getUuid());
        message.setTimestamp("1370831690572");
        message.setBody("foo bar");
        assertTrue("Could not add a new message ", message.save());
        mProcessSms.postToSentBox(message, ProcessSms.PENDING);
        assertEquals(1, message.totalMessages());
        assertTrue("Could not delete the message",message.deleteAllMessages());

    }

    @SmallTest
    public void testShouldPostTaskMessageToSentInbox() throws Exception {
        Message message = new Message();
        message.setFrom("0243581817");
        message.setUuid(mProcessSms.getUuid());
        message.setBody("foo bar");
        message.setTimestamp("1370831690572");
        assertTrue("Could not add a new message ",message.save());
        mProcessSms.postToSentBox(message, ProcessSms.TASK);
        assertEquals(1, message.totalMessages());
        assertTrue("Could not delete the message",message.deleteAllMessages());
    }

    @SmallTest
    @Suppress // Broken when code inherited
    public void testShouldImportMessagesFromSmsInbox() throws Exception {
        Message message = new Message();
        // Remove any message in the message inbox
        message.deleteAllMessages();

        // initialize some content in the sms inbox
        final String body = "foo bar";
        final String address = "123443";
        ContentValues values = new ContentValues();
        values.put("address", address);
        values.put("body", body);
        assertNotNull("Could not add sms to sms inbox", getContext().getContentResolver()
                .insert(SMS_CONTENT_INBOX, values));
        assertNotNull("Could not add sms to sms inbox", getContext().getContentResolver()
                .insert(SMS_CONTENT_INBOX, values));
        assertNotNull("Could not add sms to sms inbox", getContext().getContentResolver()
                .insert(SMS_CONTENT_INBOX, values));
        // import messages
        final boolean imported = mProcessSms.importMessages();
        assertTrue("Could not import messages", imported);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private int smsInboxCount() {
        return getContext().getContentResolver()
                .query(SMS_CONTENT_INBOX, null, null, null, null)
                .getColumnCount();
    }
}
