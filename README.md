# ConfigCat SDK for Android
https://configcat.com

ConfigCat SDK for Android provides easy integration for your application to ConfigCat.

ConfigCat is a feature flag and configuration management service that lets you separate releases from deployments. You can turn your features ON/OFF using <a href="http://app.configcat.com" target="_blank">ConfigCat Dashboard</a> even after they are deployed. ConfigCat lets you target specific groups of users based on region, email or any other custom user attribute.

ConfigCat is a <a href="https://configcat.com" target="_blank">hosted feature flag service</a>. Manage feature toggles across frontend, backend, mobile, desktop apps. <a href="https://configcat.com" target="_blank">Alternative to LaunchDarkly</a>. Management app + feature flag SDKs.

[![Build Status](https://travis-ci.com/configcat/android-sdk.svg?branch=master)](https://travis-ci.com/configcat/android-sdk)
[![Download](https://api.bintray.com/packages/configcat/releases/configcat-android-client/images/download.svg)](https://bintray.com/configcat/releases/configcat-android-client/_latestVersion)
[![Coverage Status](https://img.shields.io/codecov/c/github/ConfigCat/android-sdk.svg)](https://codecov.io/gh/ConfigCat/android-sdk)
[![Javadocs](http://javadoc.io/badge/com.configcat/configcat-android-client.svg)](http://javadoc.io/doc/com.configcat/configcat-android-client)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=configcat_android-sdk&metric=alert_status)](https://sonarcloud.io/dashboard?id=configcat_android-sdk)
![License](https://img.shields.io/github/license/configcat/android-sdk.svg)

## Getting started

### 1. Install the package
*Gradle:*
```groovy
implementation 'com.configcat:configcat-android-client:6.+'
```

### 2. Go to <a href="https://app.configcat.com/sdkkey" target="_blank">Connect your application</a> tab to get your *SDK Key*:
![SDK-KEY](https://raw.githubusercontent.com/ConfigCat/android-sdk/master/media/readme01.png  "SDK-KEY")

### 3. Import *com.configcat.** to your application
```kotlin
import com.configcat.*
```

### 4. Create the *ConfigCat* client instance
```kotlin
val client = new ConfigCatClient("#YOUR-SDK-KEY#")
```

### 5. Get your setting value:
```kotlin
val isMyAwesomeFeatureEnabled = client.getValue(Boolean::class.javaObjectType, "isMyAwesomeFeatureEnabled", false)
if(isMyAwesomeFeatureEnabled) {
    doTheNewThing()
} else{
    doTheOldThing()
}
```
Or use the async APIs:
```kotlin
client.getValueAsync(Boolean::class.javaObjectType, "isMyAwesomeFeatureEnabled", false)
    .thenAccept({ isMyAwesomeFeatureEnabled ->
        if(isMyAwesomeFeatureEnabled) {
            doTheNewThing()
        } else {
            doTheOldThing()
        }
    })
```
You also have to put this line into your manifest xml to enable the library access to the network.
```xml
<uses-permission android:name="android.permission.INTERNET" />
```
The minimum supported sdk version is 18 (Jelly Bean). Java 1.8 or later is required.

## Getting user specific setting values with Targeting
Using this feature, you will be able to get different setting values for different users in your application by passing a `User Object` to the `getValue()` function.

Read more about [Targeting here](https://configcat.com/docs/advanced/targeting/).


## User object
Percentage and targeted rollouts are calculated by the user object you can optionally pass to the configuration requests.
The user object must be created with a **mandatory** identifier parameter which should uniquely identify each user:
```kotlin
val user = User.newBuilder().build("#USER-IDENTIFIER#") // mandatory

val isMyAwesomeFeatureEnabled = client.getValue(Boolean::class.javaObjectType, "isMyAwesomeFeatureEnabled", user, false)
if(isMyAwesomeFeatureEnabled) {
    doTheNewThing()
} else{
    doTheOldThing()
}
```

## Sample/Demo app
* [Sample Android app](https://github.com/ConfigCat/android-sdk/tree/master/samples/android)

## Polling Modes
The ConfigCat SDK supports 3 different polling mechanisms to acquire the setting values from ConfigCat. After latest setting values are downloaded, they are stored in the internal cache then all requests are served from there. Read more about Polling Modes and how to use them at [ConfigCat Java Docs](https://configcat.com/docs/sdk-reference/java/) or [ConfigCat Android Docs](https://configcat.com/docs/sdk-reference/android/).

## Need help?
https://configcat.com/support

## Contributing
Contributions are welcome.

## About ConfigCat
- [Official ConfigCat SDK's for other platforms](https://github.com/configcat)
- [Documentation](https://configcat.com/docs)
- [Blog](https://configcat.com/blog)
