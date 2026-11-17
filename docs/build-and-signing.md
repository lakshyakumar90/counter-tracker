# Release Build and Signing

The release variant can produce APK and Android App Bundle artifacts. A release signing key is not stored in this repository. Keep the upload keystore and passwords in a secure password manager/backup under the project owner's control; never commit them or place them in source-controlled Gradle properties.

## Configure signing for one PowerShell session

Set these variables in the shell that will run Gradle:

- `PACE_RELEASE_STORE_FILE`: absolute path to the owner's upload keystore.
- `PACE_RELEASE_STORE_PASSWORD`: keystore password.
- `PACE_RELEASE_KEY_ALIAS`: key alias inside the keystore.
- `PACE_RELEASE_KEY_PASSWORD`: key password.

Set all four or leave all four unset. A partial configuration fails with a clear Gradle error. If all are unset, release output is unsigned. The `.gitignore` excludes common keystore and private-key file types.

Build release artifacts:

```powershell
.\gradlew.bat :app:assembleRelease :app:bundleRelease
```

Outputs:

- APK: `app/build/outputs/apk/release/app-release.apk` when signing is configured; otherwise `app-release-unsigned.apk`.
- AAB: `app/build/outputs/bundle/release/app-release.aab`.

Verify an APK signature with:

```powershell
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --verbose app\build\outputs\apk\release\app-release.apk
```

The Play Console upload key must be backed up securely. Losing it can block updates; do not generate a replacement casually. Keep the final app signing key and upload-key recovery process documented outside this repository.
