# Research: WebDAV Protocol Support & Starting Shared Folder

This research document analyzes the implementation of WebDAV (HTTP/HTTPS) protocol support and the addition of a custom starting folder path for NAS connections, allowing users to bypass SFTP permission limitations on Synology NAS.

---

## 1. Starting Shared Folder for SFTP/WebDAV

### Findings
* Currently, SFTP login starts at the home directory or `/` root. On Synology, `/` contains system directories and mount volumes (e.g., `/volume1`), which are read-only for standard users or require administrative paths.
* When navigating, users start at `/` and get stuck or fail write validation because `/` is read-only.
* **Solution**: Add a "Starting Path" (e.g., `/volume1/photo` or `/photo`) input field to the NAS connection setup. When the connection is established, the folder explorer starts directly at this custom path rather than `/`.

---

## 2. WebDAV Client Implementation

### Protocol Analysis
WebDAV is an extension of HTTP. We can implement a clean WebDAV client using `OkHttpClient` with HTTP Basic Authentication:

1. **Test Connection (PROPFIND)**:
   * Perform a `PROPFIND` request with `Depth: 0` to the base URL. A `207 Multi-Status` or `200 OK` indicates successful login.
2. **List Directories (PROPFIND)**:
   * Perform a `PROPFIND` request with `Depth: 1` to the target directory. Parse the XML response to extract resource names that contain `<d:collection/>` (directories).
3. **Create Directory (MKCOL)**:
   * Perform a `MKCOL` request to the target directory path. A `201 Created` indicates success.
4. **Upload File (PUT)**:
   * Perform a `PUT` request with the file binary stream as request body.
   * Support two-phase commit upload by uploading to `.part` first, and then performing a `MOVE` request (which is the WebDAV atomic rename equivalent of `rename()`).

### XML PROPFIND Parse Example
The response of `PROPFIND` is XML containing `<d:response>` elements:
```xml
<d:multistatus xmlns:d="DAV:">
    <d:response>
        <d:href>/photo/</d:href>
        <d:propstat>
            <d:prop>
                <d:resourcetype><d:collection/></d:resourcetype>
            </d:prop>
            <d:status>HTTP/1.1 200 OK</d:status>
        </d:propstat>
    </d:response>
</d:multistatus>
```

---

## 3. UI and Database Integration

### Database Changes
Add fields to `NasConnectionEntity`:
* `protocol`: String (either `"SFTP"` or `"WEBDAV"`)
* `startingFolder`: String? (optional starting path)
* `useHttps`: Boolean (for WebDAV HTTPS secure connection)

### Connection Setup Screen
* Add dropdown/radio buttons for Protocol selection: **SFTP** vs **WebDAV**.
* If WebDAV is selected, show a checkbox for **Use HTTPS** (secure connection) and change default port input to `5006` (HTTPS) or `5005` (HTTP).
* Add an optional **Starting Folder** input field.

---

## Verification Plan

### Automated Tests
* Create unit tests for WebDAV URL building, Basic Authentication header creation, and XML response parsing.

### Manual Verification
* Deploy to the device and test connecting to Synology NAS using WebDAV (HTTP on port 5005 or HTTPS on port 5006).
* Navigate directories and create a folder; verify write permission works.
