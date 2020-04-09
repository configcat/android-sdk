# Steps to deploy
## Preparation
1. Run tests
3. Increase the version in the gradle.properties file.
4. Commit & Push
## Publish
Use the **same version** for the git tag as in the properties file.
- Via git tag
    1. Create a new version tag.
       ```bash
       git tag v[MAJOR].[MINOR].[PATCH]
       ```
       > Example: `git tag v2.5.5`
    2. Push the tag.
       ```bash
       git push origin --tags
       ```
- Via Github release 

  Create a new [Github release](https://github.com/configcat/android-sdk/releases) with a new version tag and release notes.

## Sync
1. Make sure the new version is available on [jcenter](https://bintray.com/configcat/releases/configcat-android-client).
2. Log in to bintray.com and sync the new package to Maven Central. (https://bintray.com/configcat/releases/configcat-android-client#central)
3. Make sure the new version is available on [Maven Central](https://search.maven.org/artifact/com.configcat/configcat-android-client).

## Update import examples in local README.md

## Update code examples in ConfigCat Dashboard project's 
`steps to connect your application`
1. Update Maven import examples.
2. Update Gradle import examples.

## Update import examples in Docs

## Update samples
Update and test sample apps with the new SDK version.