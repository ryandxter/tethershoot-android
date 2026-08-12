# Forensic Diagnostic Report: Google Chrome OAuth "Akses Diblokir / Access Blocked"

**Timestamp:** 2026-08-12T17:10:35+07:00
**Project:** TransferInFuji (`com.rvmeur.transferin`)
**Target Device:** Wireless Debugging `192.168.1.47:42457`

---

## 1. Primary Findings & Anomaly Detection

### Symptom
When user taps `"masuk akun google drive (oauth)"` in `TransferInFuji` Android App, Google Chrome opens and displays:
> **"Akses diblokir / Access blocked: Authorization Error"**
> **"Error 400: invalid_request"** / **"redirect_uri_mismatch"**

### Scientific Root Cause Analysis
1. **Google OAuth 2.0 Policy Mismatch:**
   - **Scenario A (Using Android Client ID in Chrome Browser):**
     Google OAuth server `https://accounts.google.com/o/oauth2/v2/auth` **REJECTS** custom scheme URIs (`com.rvmeur.transferin:/oauth2redirect`) when called via standard web browser (Chrome) using an Android Client ID (`343575435197-ug3en8ich4l1grgae2m6kbpcub0glu77.apps.googleusercontent.com`).
     - *Google Error:* `Custom scheme URIs are not allowed for this client type. Error 400: invalid_request`.

   - **Scenario B (Using Web Client ID in Chrome Browser without GCP Setting):**
     When calling `https://accounts.google.com/o/oauth2/v2/auth` with Web Client ID (`343575435197-q6tnos0o1cgt8c0ir8alne21gitioj7i.apps.googleusercontent.com`), Google checks if `https://rvmeur.com/oauth2callback` is listed under **Authorized redirect URIs** in Google Cloud Console for that Web Client ID.
     - *Google Error:* `redirect_uri_mismatch` / `Akses diblokir` if `https://rvmeur.com/oauth2callback` is missing from GCP Web Client ID settings.

---

## 2. Complete Resolution Roadmap

### Solution Option 1: Register `https://rvmeur.com/oauth2callback` in Web Client ID (Web OAuth Flow)
1. Go to [Google Cloud Console Credentials](https://console.cloud.google.com/apis/credentials).
2. Select Web Client ID: `343575435197-q6tnos0o1cgt8c0ir8alne21gitioj7i.apps.googleusercontent.com`.
3. Under **Authorized redirect URIs**, add `https://rvmeur.com/oauth2callback`.
4. Save.

### Solution Option 2: Add Google Play Services Auth / Credential Manager (Native Android Dialog Flow - Best UX)
Use Android's native `GoogleSignIn` SDK / `play-services-auth` so Google Account login shows as a native bottom sheet popup inside the app without opening Chrome browser at all!
