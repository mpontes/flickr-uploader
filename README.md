Flickr Uploader
===============

Fork of the [Android Flickr Uploader](https://github.com/rafali/flickr-uploader) project with the home calling, perversive tracking, ads, and non-premium nerfing "features" gutted out.

Did this for my own personal usage, but you can easily build it for yourself, all you need is maven and the Android Developer Tools.

* Create a Flickr App and insert your API secret / API key into res/values/strings.xml
* Set the ``ANDROID_HOME`` env variable to the root of your Android SDK
* Clone [mpontes/FlickrjApi4Android](https://github.com/mpontes/FlickrjApi4Android), do ``mvn install``
* Do ``mvn clean install -Prelease`` from the FlickrUploader folder
* You now have an apk inside the target folder!
