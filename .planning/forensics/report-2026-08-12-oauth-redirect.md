# Forensic Report: Android Google OAuth Redirection & Client Type Misconfiguration

**Timestamp:** 2026-08-12T15:30:15+07:00
**Project:** TransferInFuji (`com.rvmeur.transferin`)

---

## 1. Executive Summary & Anomaly Investigation

### Primary Symptom Reported
User added `https://rvmeur.com/oauth2callback` into Authorized Redirect URIs in Google Cloud Console / Firebase Console. However, after completing Google Account authorization in Chrome browser, Chrome navigated to the website homepage `https://rvmeur.com/oauth2callback` instead of returning back to the Android application `TransferInFuji`.

---

## 2. Evidence Gathering & Technical Root Cause Analysis

### Evidence A: Client Type Mismatch (WEB Client vs Android Native Client)
- **Current Credentials File (`client_secret_...json`):**
  The OAuth credentials being used belong to a **WEB Application** Client ID (`343575435197-q6tnos0o1cgt8c0ir8alne21gitioj7i.apps.googleusercontent.com`).
- **Google OAuth Policy Restriction:**
  Google Cloud Console strictly enforces that **WEB** Client IDs **CANNOT** use custom scheme URIs (e.g. `com.rvmeur.transferin:/oauth2redirect`). That's why Google returned `Error 400: invalid_request` when attempting custom scheme URIs.

### Evidence B: Android 12+ (API 31+) App Link Verification Behavior
- When using `https://rvmeur.com/oauth2callback` with a **WEB** Client ID, Google redirects Chrome to `https://rvmeur.com/oauth2callback?code=...`.
- Because `https://rvmeur.com/.well-known/assetlinks.json` is not published on `rvmeur.com`, Android 12+ treats `https://` URLs as normal web browsing and opens the website in Chrome rather than delegating the Intent back to `com.rvmeur.transferin.MainActivity`.

---

## 3. Recommended Remediation Strategies

### Solution 1: Create an Android OAuth Client ID in Google Cloud / Firebase Console (RECOMMENDED & CLEANEST)

Native Android apps should use an **Android Client ID** instead of a **Web Client ID**.

1. Go to [Google Cloud Credentials Console](https://console.cloud.google.com/apis/credentials) or Firebase Console.
2. Click **Create Credentials** -> **OAuth client ID**.
3. Select **Application type**: **Android**.
4. Input details:
   - **Package name**: `com.rvmeur.transferin`
   - **SHA-1 certificate fingerprint**: `44:24:66:EB:56:88:E0:AE:34:A6:B0:BE:15:5D:B5:FA:6F:B7:FB:72`
5. Click **Create**.
6. When using an **Android Client ID**, Google OAuth **ALLOWS** custom scheme URIs (`com.rvmeur.transferin:/oauth2redirect`). Chrome will immediately launch `MainActivity` upon account selection without touching any web server!

---

### Solution 2: Web Server HTML Script Redirect on `https://rvmeur.com/oauth2callback`

If you prefer to keep using the existing Web Client ID:

Place a 1-line HTML redirect script at `https://rvmeur.com/oauth2callback`:

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>TransferIn Redirecting...</title>
  <script>
    const urlParams = new URLSearchParams(window.location.search);
    const code = urlParams.get('code');
    if (code) {
      window.location.href = "com.rvmeur.transferin://oauth2redirect?code=" + code;
    }
  </script>
</head>
<body>
  <p>Mengarahkan kembali ke aplikasi TransferIn...</p>
</body>
</html>
```

When Google redirects Chrome to `https://rvmeur.com/oauth2callback?code=4/0A...`, this script immediately triggers `com.rvmeur.transferin://oauth2redirect?code=...`, opening `TransferInFuji` instantly!
