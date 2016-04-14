
package org.addhen.smssync.tests.util;

import android.test.suitebuilder.annotation.SmallTest;

import org.addhen.smssync.tests.BaseTest;
import org.addhen.smssync.util.Util;

public class UtilTest extends BaseTest {

    Long timestamp;
    String expected;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        timestamp = 1370831690572l;
        expected = "Jun 10, 2013 at 02:34 AM";
    }

    /**
     * Test date and time formatter
     */
    @SmallTest
    public void ShouldFormatDate() throws NumberFormatException {
        final String formatted = Util.formatDateTime(timestamp, "MMM dd, yyyy 'at' hh:mm a");

        assertNotNullOrEqual("Timestamp cannot be null or empty", expected, formatted);

    }

    /**
     * Test that two strings can be joined together
     */
    @SmallTest
    public void testShouldJoinTwoStrings() {
        final String expected = "Hello World!";
        assertNotNullOrEqual("Two strings couldn't be joined", expected, Util.joinString("Hello ", "World!"));
    }

    /**
     * Test should check that device is connected to the
     * network and has internet.
     */
    @SmallTest
    public void testShouldCheckDeviceHasInternet() {
        final boolean connected = Util.isConnected(getContext());
        assertTrue("The device is not connected to the internet", connected);
    }

    /**
     * Test that a string first letter is capitalized
     */
    @SmallTest
    public void testShouldCapitalizeFirstLetterOfAText(){
        final String actual = Util.capitalizeFirstLetter("hello world where are you");
        assertNotNullOrEqual("Could not capitalize the string ", "Hello world where are you", actual);
    }

    @SmallTest
    public void testShouldCheckUrlIsValid() {
        boolean valid = Util.isValidCallbackUrl("http://demo.ushahidi.com/smssync");
        assertTrue("The provided URL is not a valid one", valid);
    }

    @SmallTest
    public void testShouldFailCheckUrlIsInvalid() {
        boolean valid = Util.isValidCallbackUrl("demo.ushahidi.com/smssync");
        assertFalse(valid);
    }

    /**
     * A Sync/Callback URL with special characters in the Basic Auth part of
     * the URL is valid.
     */
    @SmallTest
    public void testURLWithBasicAuthSpecialCharsIsValid(){
        boolean valid = Util.isValidCallbackUrl("http://admin:$&#%?=~_|!,.;@example.com/test");
        assertTrue(valid);
    }

    /** Test that an unix timestamp will be formatted to
     * Jun 10, 2013 at 2:34 AM
     */
    @SmallTest
    public void ShouldFormatTimestampToHumanFriendly() {
        final String formatted = Util.formatTimestamp(getContext(),timestamp);
        assertNotNullOrEqual("Timestamp cannot be null or empty", "2:34 AM", formatted);
    }
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        timestamp = null;
        expected = null;
    }
}
