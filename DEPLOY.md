# Steps to deploy
## Preparation
1. Run tests
3. Increase the version in the gradle.properties file.
## Publish
Use the **same version** for the git tag as in the properties file.
- Via git tag
    1. Create a new version tag.
       ```bash
       git tag [MAJOR].[MINOR].[PATCH]
       ```
       > Example: `git tag 2.5.5`
    2. Push the tag.
       ```bash
       git push origin --tags
       ```
- Via Github release 

  Create a new [Github release](https://github.com/configcat/android-sdk/releases) with a new version tag and release notes.

## Jcenter
1. Make sure the new version is available on [jcenter](https://bintray.com/configcat/releases/configcat-android-client).