package com.rvmeur.transferin.domain.model

/**
 * Mode for selecting source media from camera.
 *
 * - BROWSE_FOLDER: User manually selects a specific folder
 * - AUTO_DETECT: App automatically monitors entire storage for new files
 */
enum class SourceSelectionMode {
    /**
     * User manually selects one folder to monitor.
     * Only detects new files in that folder.
     */
    BROWSE_FOLDER,

    /**
     * App automatically scans entire storage and monitors all folders.
     * Detects new files and new folders automatically.
     */
    AUTO_DETECT
}
