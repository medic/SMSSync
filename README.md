[![Build Status](https://travis-ci.org/ushahidi/SMSSync.png?branch=develop)](https://travis-ci.org/ushahidi/SMSSync)

# SMSSync

Welcome to **"SMSSync"**, an android application that turns your android powered phone into an SMS gateway.

Read about it at **[smssync.ushahidi.com](http://smssync.ushahidi.com/)**.

## Installation

Insallation and configuration details are [here][1].

## Development

### CI

There is a build on travis: <a href="https://travis-ci.org/medic/SMSSync"><img src="https://travis-ci.org/medic/SMSSync.svg?branch=master"/></a>

### Testing locally

To run the test suite locally:

	make test

The test suite currently relies on a few factors, including the operating system running on the test device, and internet connectivity of the test device.  This means you may see some unexpected failures.  Travis should not see these failures.

### Building/installing locally

To build a debug build locally and deploy to a connected device:

	make

## Support

Post on our [forums][3]

[1]: http://smssync.ushahidi.com/howto
[2]: http://smssync.ushahidi.com/doc
[3]: https://wiki.ushahidi.com/pages/viewpage.action?pageId=8357140
