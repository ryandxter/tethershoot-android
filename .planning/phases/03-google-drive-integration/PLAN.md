# Phase 3 Plan: Google Drive Integration & NAS Replacement

## Tasks

- [ ] Task 1: Reconfigure database structures for Google Drive credentials
  - Update room models to support OAuth credentials (client id, client secret, refresh token, access token, target folder id, target folder name).
  
- [ ] Task 2: Implement Google Drive REST Client (`GoogleDriveClient.kt`)
  - Implement OkHttp network routines to authenticate with Google OAuth2 server.
  - Implement access token refresh automatically before initiating uploads.
  - Implement multipart file upload to Google Drive REST endpoint (`https://www.googleapis.com/upload/drive/v3/files`).

- [ ] Task 3: Replace SFTP/WebDAV implementations
  - Clean up dependencies on JSch and WebDAV XML parsing.
  - Refactor `DelegatingStorageClient` to delegate upload requests directly to the new `GoogleDriveClient`.

- [ ] Task 4: Redesign authentication UI (`LoginNasScreen` -> `LoginGoogleDriveScreen`)
  - Modify UI to request Client ID, Client Secret, and Refresh Token / Auth Code.
  - Add connection testing that pings Google Drive API, validates credentials, and fetches/creates the target parent folder.

- [ ] Task 5: Compilation and end-to-end integration check
  - Run `./gradlew compileDebugKotlin` to verify the codebase successfully builds under the new storage implementation.
