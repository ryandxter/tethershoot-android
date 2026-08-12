# Phase 3 Research: Google Drive Integration & NAS Replacement

## Context & Objective
The goal is to replace all legacy NAS (SFTP/WebDAV) storage code with an online Google Drive storage client using authentic Google Drive API OAuth2 credential authentication.

## Technical Design & API Specification

### 1. Google Drive OAuth2 flow:
- **Authorization Request:**
  - Scope: `https://www.googleapis.com/auth/drive.file` (access to files created or opened by the app, which is extremely secure) or `https://www.googleapis.com/auth/drive` (full access).
  - Web UI allows pasting of Authorization Code or inputting OAuth Client ID, Client Secret, and Refresh Token for professional workflow setup.
- **Token Exchange:**
  - Endpoint: `POST https://oauth2.googleapis.com/token`
  - Parameters: `client_id`, `client_secret`, `code`, `grant_type=authorization_code`, `redirect_uri` (or manual code entry).
- **Access Token Refresh:**
  - Endpoint: `POST https://oauth2.googleapis.com/token`
  - Parameters: `client_id`, `client_secret`, `refresh_token`, `grant_type=refresh_token`.

### 2. Google Drive Multipart Upload REST API:
- **Endpoint:** `POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart`
- **Headers:**
  - `Authorization: Bearer <access_token>`
  - `Content-Type: multipart/related; boundary=foo`
- **Request Body Format:**
  ```http
  --foo
  Content-Type: application/json; charset=UTF-8

  {
    "name": "DSCF0001.JPG",
    "parents": ["target_folder_id"]
  }

  --foo
  Content-Type: image/jpeg

  [BINARY DATA STREAM]
  --foo--
  ```

## Codebase Modification Impact

1. **Entity & DB Layer (`NasConnectionEntity`):**
   - Repurpose fields:
     - `host` -> `clientId`
     - `port` -> `clientSecret`
     - `username` -> `refreshToken`
     - `password` -> `accessToken`
     - `startingFolder` -> `targetFolderId`
     - `protocol` -> "GOOGLE_DRIVE"

2. **Storage Client Interface (`SftpClient`):**
   - We will repurpose `SftpClient` interface to `StorageClient`.
   - Implement `GoogleDriveClient` replacing `JschSftpClient` and `WebDavClient`.
   - Update `DelegatingSftpClient` (which is renamed to `DelegatingStorageClient`) to route solely to `GoogleDriveClient`.

3. **UI Authentication Layer (`LoginNasScreen` & `LoginNasViewModel`):**
   - Replace custom host/port forms with simple credentials fields (Client ID, Client Secret, Refresh Token / Auth Code) and a validation button.
