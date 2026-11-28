# Signing

`fussoo-dev.jks` is a **development** keystore committed on purpose.

Why: Android refuses to update an installed app if the signing certificate changed.
GitHub Actions creates a fresh debug keystore for every run, which would break every
update. Committing one dev key means `adb install -r` and normal APK updates work
across builds.

* Store and key password: `fussoo-dev`
* Alias: `fussoo`
* Validity: 10000 days, RSA 2048

This is **not** a Play Store key. If you ever publish, generate a private upload key and
keep it out of the repository.

To create it locally (not needed, it is committed):

```
keytool -genkeypair -v -keystore fussoo-dev.jks -alias fussoo \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass fussoo-dev -keypass fussoo-dev \
  -dname "CN=Fussoo VR Dev, OU=Dev, O=Fussoo, L=Dhaka, C=BD"
```
