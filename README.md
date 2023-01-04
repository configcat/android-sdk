# ConfigCat SDK for Android
https://configcat.com

ConfigCat SDK for Android provides easy integration for your application to ConfigCat.

ConfigCat is a feature flag and configuration management service that lets you separate releases from deployments. You can turn your features ON/OFF using <a href="http://app.configcat.com" target="_blank">ConfigCat Dashboard</a> even after they are deployed. ConfigCat lets you target specific groups of users based on region, email or any other custom user attribute.

ConfigCat is a <a href="https://configcat.com" target="_blank">hosted feature flag service</a>. Manage feature toggles across frontend, backend, mobile, desktop apps. <a href="https://configcat.com" target="_blank">Alternative to LaunchDarkly</a>. Management app + feature flag SDKs.

[![Android CI](https://github.com/configcat/android-sdk/actions/workflows/android-ci.yml/badge.svg?branch=master)](https://github.com/configcat/android-sdk/actions/workflows/android-ci.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.configcat/configcat-android-client/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.configcat/configcat-android-client)
[![Javadocs](http://javadoc.io/badge/com.configcat/configcat-android-client.svg)](http://javadoc.io/doc/com.configcat/configcat-android-client)
[![Coverage Status](https://img.shields.io/sonar/coverage/configcat_android-sdk?logo=SonarCloud&server=https%3A%2F%2Fsonarcloud.io)](https://sonarcloud.io/project/overview?id=configcat_android-sdk)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=configcat_android-sdk&metric=alert_status)](https://sonarcloud.io/dashboard?id=configcat_android-sdk)
![License](https://img.shields.io/github/license/configcat/android-sdk.svg)

## Getting started

### 1. Install the package
*Gradle:*
```groovy
implementation 'com.configcat:configcat-android-client:8.+'
```

### 2. Go to the <a href="https://app.configcat.com/sdkkey" target="_blank">ConfigCat Dashboard</a> to get your *SDK Key*:
![SDK-KEY](https://raw.githubusercontent.com/ConfigCat/android-sdk/master/media/readme02-3.png  "SDK-KEY")

### 3. Import *com.configcat.** to your application
```java
import com.configcat.*;
```

### 4. Create the *ConfigCat* client instance
```java
ConfigCatClient client = ConfigCatClient.get("#YOUR-SDK-KEY#");
```

### 5. Get your setting value:
```java
boolean isMyAwesomeFeatureEnabled = client.getValue(Boolean.class, "isMyAwesomeFeatureEnabled", false);
if(isMyAwesomeFeatureEnabled) {
    doTheNewThing();
} else {
    doTheOldThing();
}
```
Or use the async APIs:

```java
client.getValueAsync(Boolean.class, "isMyAwesomeFeatureEnabled", false)
    .thenAccept(isMyAwesomeFeatureEnabled -> {
        if(isMyAwesomeFeatureEnabled) {
            doTheNewThing();
        } else {
            doTheOldThing();
        }
    });
```

## Compatibility
The minimum supported Android SDK version is 21 (Lollipop).

## R8 (ProGuard)
When you use R8 or ProGuard, the aar artifact automatically applies the [included rules](configcat-proguard-rules.pro) for the SDK.

## Getting user specific setting values with Targeting
Using this feature, you will be able to get different setting values for different users in your application by passing a `User Object` to the `getValue()` function.

Read more about [Targeting here](https://configcat.com/docs/advanced/targeting/).


## User object
Percentage and targeted rollouts are calculated by the user object you can optionally pass to the configuration requests.
The user object must be created with a **mandatory** identifier parameter which should uniquely identify each user:
```java
User user = User.newBuilder()
        .build("#USER-IDENTIFIER#"); // mandatory

boolean isMyAwesomeFeatureEnabled = client.getValue(Boolean.class, "isMyAwesomeFeatureEnabled", user, false);
if(isMyAwesomeFeatureEnabled) {
    doTheNewThing();
} else{
    doTheOldThing();
}
```

## Sample/Demo app
* [Sample Android app](https://github.com/ConfigCat/android-sdk/tree/master/samples/android)

## Polling Modes
The ConfigCat SDK supports 3 different polling mechanisms to acquire the setting values from ConfigCat. After latest setting values are downloaded, they are stored in the internal cache then all requests are served from there. Read more about Polling Modes and how to use them at [ConfigCat Java Docs](https://configcat.com/docs/sdk-reference/java/) or [ConfigCat Android Docs](https://configcat.com/docs/sdk-reference/android/).

## Sensitive information handling

The frontend/mobile SDKs are running in your users' browsers/devices. The SDK is downloading a [config.json](https://configcat.com/docs/requests/) file from ConfigCat's CDN servers. The URL path for this config.json file contains your SDK key, so the SDK key and the content of your config.json file (feature flag keys, feature flag values, targeting rules, % rules) can be visible to your users. 
This SDK key is read-only, it only allows downloading your config.json file, but nobody can make any changes with it in your ConfigCat account.  
Suppose you don't want your SDK key or the content of your config.json file visible to your users. In that case, we recommend you use the SDK only in your backend applications and call a backend endpoint in your frontend/mobile application to evaluate the feature flags for a specific application customer.  
Also, we recommend using [sensitive targeting comparators](https://configcat.com/docs/advanced/targeting/#sensitive-text-comparators) in the targeting rules of those feature flags that are used in the frontend/mobile SDKs.

## Need help?
https://configcat.com/support

## Contributing
Contributions are welcome. For more info please read the [Contribution Guideline](CONTRIBUTING.md).

## About ConfigCat
- [Official ConfigCat SDK's for other platforms](https://github.com/configcat)
- [Documentation](https://configcat.com/docs)
- [Blog](https://configcat.com/blog)
