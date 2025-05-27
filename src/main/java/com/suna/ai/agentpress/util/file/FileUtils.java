package com.Nubian.ai.agentpress.util.file;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility functions for handling file operations.
 * This class provides methods for determining which files should be excluded
 * from operations and cleaning file paths.
 */
public final class FileUtils {

    /**
     * Files to exclude from operations.
     */
    private static final Set<String> EXCLUDED_FILES = new HashSet<>(Arrays.asList(
        ".DS_Store",
        ".gitignore",
        "package-lock.json",
        "postcss.config.js",
        "postcss.config.mjs",
        "jsconfig.json",
        "components.json",
        "tsconfig.tsbuildinfo",
        "tsconfig.json"
    ));

    /**
     * Directories to exclude from operations.
     */
    private static final Set<String> EXCLUDED_DIRS = new HashSet<>(Arrays.asList(
        "node_modules",
        ".next",
        "dist",
        "build",
        ".git"
    ));

    /**
     * File extensions to exclude from operations.
     */
    private static final Set<String> EXCLUDED_EXT = new HashSet<>(Arrays.asList(
        ".ico",
        ".svg",
        ".png",
        ".jpg",
        ".jpeg",
        ".gif",
        ".bmp",
        ".tiff",
        ".webp",
        ".db",
        ".sql"
    ));

    /**
     * Check if a file should be excluded based on path, name, or extension.
     *
     * @param relPath Relative path of the file to check
     * @return true if the file should be excluded, false otherwise
     */
    public static boolean shouldExcludeFile(String relPath) {
        if (relPath == null || relPath.isEmpty()) {
            return false;
        }

        // Check filename
        Path path = Paths.get(relPath);
        String filename = path.getFileName().toString();
        if (EXCLUDED_FILES.contains(filename)) {
            return true;
        }

        // Check directory
        String dirPath = path.getParent() != null ? path.getParent().toString() : "";
        for (String excludedDir : EXCLUDED_DIRS) {
            if (dirPath.contains(excludedDir)) {
                return true;
            }
        }

        // Check extension
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            String extension = filename.substring(lastDotIndex).toLowerCase();
            if (EXCLUDED_EXT.contains(extension)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Clean and normalize a path to be relative to the workspace.
     *
     * @param path The path to clean
     * @param workspacePath The base workspace path to remove (default: "/workspace")
     * @return The cleaned path, relative to the workspace
     */
    public static String cleanPath(String path, String workspacePath) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        // Use default workspace path if not provided
        if (workspacePath == null || workspacePath.isEmpty()) {
            workspacePath = "/workspace";
        }

        // Remove any leading slash
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        
        // Remove workspace prefix if present
        String workspacePrefix = workspacePath.startsWith("/") ? 
            workspacePath.substring(1) : workspacePath;
            
        if (cleanPath.startsWith(workspacePrefix)) {
            cleanPath = cleanPath.substring(workspacePrefix.length());
        }
        
        // Remove workspace/ prefix if present
        if (cleanPath.startsWith("workspace/")) {
            cleanPath = cleanPath.substring("workspace/".length());
        }
        
        // Remove any remaining leading slash
        return cleanPath.startsWith("/") ? cleanPath.substring(1) : cleanPath;
    }

    /**
     * Clean and normalize a path to be relative to the workspace.
     * Uses default workspace path "/workspace".
     *
     * @param path The path to clean
     * @return The cleaned path, relative to the workspace
     */
    public static String cleanPath(String path) {
        return cleanPath(path, "/workspace");
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private FileUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
