[![Build Status](https://travis-ci.org/medic/SMSSync.png?branch=develop)](https://travis-ci.org/medic/SMSSync)

# SMSSync

Welcome to **"SMSSync"**, an android application that turns your android powered phone into an SMS gateway.

Read about it at **[smssync.ushahidi.com](http://smssync.ushahidi.com/)**.

## Installation

Insallation and configuration details are [here][1].

## Development

The build setup supports Android Studio and Gradle.

To build this project make sure you've either set the `ANDROID_HOME` environment variable to point to where
you have your Android SDK installed, or you've defined the location of the Android
SDK in file `local.property` at the root of this project.

An example `local.property` file:

	sdk.dir=/home/username/android-sdk-linux_x86

### Building/installing locally

To build a debug build locally and deploy to a connected device:

	make

### Testing locally

To run the test suite locally:

	make test

The test suite currently relies on a few factors, including the operating system running on the test device, and internet connectivity of the test device.  This means you may see some unexpected failures.  Travis should not see these failures.

### CI

There is a build on travis: <a href="https://travis-ci.org/medic/SMSSync"><img src="https://travis-ci.org/medic/SMSSync.svg?branch=master"/></a>

## Support

Post on our [forums][3]

[1]: http://smssync.ushahidi.com/howto
[2]: http://smssync.ushahidi.com/doc
[3]: https://wiki.ushahidi.com/pages/viewpage.action?pageId=8357140
