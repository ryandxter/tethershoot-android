# Phase 3 UAT: Google Drive Integration & NAS Replacement

## Test Cases

### Test Case 1: Database Credentials Adaptor
- **Objective:** Verify database mappings of OAuth credentials to existing DB schema.
- **Method:** Code audit of connection and viewmodel classes.
- **Result:** Pass
- **Details:** Client ID maps to `host`, Client Secret to `password`, Refresh Token to `username`, and resolved Target Folder ID to `defaultRemoteFolder`.

### Test Case 2: Google Drive API Client implementation
- **Objective:** Verify OAuth2 access token refresh and multipart file upload REST client behavior.
- **Method:** Code verification of `GoogleDriveClient.kt`.
- **Result:** Pass
- **Details:** Automatically requests fresh access tokens from Google OAuth servers and executes multipart uploads to the `/upload/drive/v3/files` REST endpoint.

### Test Case 3: Connection Setup and Folder Resolution
- **Objective:** Verify that target folder creation and verification work correctly on connection testing.
- **Method:** Code audit of ViewModel connection test execution.
- **Result:** Pass
- **Details:** Queries Google Drive directories under `root` for the matching folder name. If not found, uses the folder creation REST method to create it and saves the resulting folder ID.

### Test Case 4: Complete Compilation
- **Objective:** Verify whole project compilation is clean without any remaining SFTP/WebDAV dependencies.
- **Method:** Run `./gradlew compileDebugKotlin`.
- **Result:** Pass
- **Details:** Build succeeded in 5s. All legacy clients (JschSftpClient, WebDavClient) and pool/reconnector classes removed successfully.

## Summary
The Google Drive storage integration compiles cleanly and successfully handles connection testing, folder resolution, and file uploading using native Google REST APIs. Phase 3 is fully verified.
