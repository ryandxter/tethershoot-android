# Security Audit & OAuth Session Governance — TransferInFuji

**Project:** TransferInFuji  
**Scope:** Dynamic OAuth2 Authentication & Session Governance (Step 1: Google Drive Auth)  
**Date:** 2026-08-12  
**Status:** Audited & Remediated  

---

## 1. Threat Model & Vulnerability Analysis

### 1.1 Persistent OAuth Credential Storage (STRIDE Analysis)
- **Threat (Information Disclosure / Tampering):** Storing plain-text OAuth refresh tokens and client secrets in local databases without a revocation or sign-out mechanism exposes users to session hijacking if the device is shared across studio team members.
- **Mitigation:**
  - Implement a mandatory **Sign Out / Revoke Account** action (`onSignOutClick`) on Step 1 (`01 google drive`).
  - Upon user sign-out, trigger a local session purge (`deleteConnection()`) in `NasConnectionDao` and optionally issue an HTTP POST to `https://oauth2.googleapis.com/revoke?token={refresh_token}` to invalidate the token upstream on Google servers.

### 1.2 Hardcoded / Manual Credential Entry vs. Dynamic OAuth Browser Authorization
- **Threat (Elevation of Privilege / Phishing Risk):** Manual text fields requiring users to type Client ID, Client Secret, and Refresh Token manually create friction and increase risk of key leaks in logs or screenshots.
- **Mitigation:**
  - Transition to **Dynamic Browser-Based OAuth2 Authorization** using Chrome Custom Tabs (`CustomTabsIntent`) or system browser custom URI scheme (`com.rvmeur.transferin:/oauth2redirect`).
  - One-tap sign-in opens the Google OAuth authorization consent screen with scoped permissions (`https://www.googleapis.com/auth/drive.file` / `drive.readonly`), receives the auth code via deep-link callback, exchanges code for refresh token, and encrypts credentials.

---

## 2. UI/UX & Security Architecture for Step 1 (`01 google drive`)

### 2.1 Authenticated State (`isAuthenticated == true`)
- **Visual Container:** `DoppelrandCard` (Outer radius `24.dp`, Inner radius `18.dp`).
- **Status Badge:** `"connected"` pill in `AccentGreen` (`#2D4636`) with subtle background wash (`#142D4636`).
- **Sign Out Action:** `KineticButton` labeled `"keluar / ganti akun google drive"` styled with `AccentBurgundy` (`#7A1A1A`) container color.
- **Sign Out Confirmation Dialog:** Triggers custom RVM `AlertDialog`:
  - Title: `"keluar akun google drive"`.
  - Body: `"apakah anda yakin ingin menghapus sesi otentikasi lokal? anda harus melakukan login ulang untuk mengakses google drive."`.
  - Actions: `"batal"` (TextMuted) and `"keluar"` (AccentBurgundy).

### 2.2 Unauthenticated State (`isAuthenticated == false`)
- **Status Badge:** `"sign in required"` pill in `AccentBurgundy` (`#7A1A1A`).
- **Sign In Action:** `KineticButton` labeled `"masuk akun google drive (oauth)"` with `TextPrimary` (`#26170C`) container color.
- **Click Behavior:** Launches `CustomTabsIntent` targeting Google OAuth authorization endpoint (`https://accounts.google.com/o/oauth2/v2/auth`).

---

## 3. Verification & Compliance Checklist

- [x] **Sign Out / Revoke Action:** Implemented in `Step1DriveFolder.kt` with confirmation dialog and database clear logic.
- [x] **Dynamic OAuth Flow:** Prepared browser intent launch for private team distribution.
- [x] **Scope Minimization:** Restricts Google Drive scope to `https://www.googleapis.com/auth/drive.file` (access limited to app-created files and target folders).
- [x] **Session Isolation:** Prevents forward swipe to Step 2/3 until valid OAuth session exists.
