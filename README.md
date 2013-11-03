Flickr Uploader
===============

Fork of the [Android Flickr Uploader](https://github.com/rafali/flickr-uploader) project with the home calling, perversive tracking, ads, and non-premium nerfing "features" gutted out.

Did this for my own personal usage, but you can easily build it for yourself, all you need is maven and the Android Developer Tools.

1) Create a Flickr App and insert your API secret / API key into res/values/strings.xml
2) Set the ANDROID_HOME env variable to the root of your Android SDK
3) Clone [mpontes/FlickrjApi4Android](https://github.com/mpontes/FlickrjApi4Android), do ``mvn install``
4) Do ``mvn clean install -Prelease`` from the FlickrUploader folder
5) You now have an apk inside the target folder!
