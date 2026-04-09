package org.opensha.commons.data.siteData;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

import java.awt.GraphicsEnvironment;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Simply downloads site data from USGS GitLab for specified <code>CONUS_Versions</code>.
 * This does not offer any guarantee against data corruption.
 * If a download fails, it will not be retried, but an error will be thrown and
 * if a desktop environment is available, a GUI pop-up window will attempt to be shown.
 * <p>
 * If there is data at the specified download path, it is assumed to be up-to-date.
 * This is a reasonable assumption as a tagged version is not expected to change.
 * If there is a future need for data integrity validation and dynamic updates,
 * the GetFile framework should be considered on a fork of the repository with
 * the appropriate GetFile metadata and MD5 checksums.
 * </p>
 */
public class CONUS_Downloader {
    private static final String BASE_URL = "https://code.usgs.gov/ghsc/nshmp/nshms/nshm-conus/-/archive/";
    private static final Logger log = LoggerFactory.getLogger(CONUS_Downloader.class);
    private static final boolean D = false;

    private final CONUS_Versions version;
    private final File outputDir;

    /**
     * CONUS_Downloader constructor collects data on which version to
     * download and where to download to.
     * @param version Which site data to download from the USGS GitLab
     * @param outputDir Where the downloaded data will be stored
     */
    CONUS_Downloader(CONUS_Versions version, File outputDir) {
        this.version = version;
        this.outputDir = outputDir;
    }

    /**
     * CONUS_Downloader constructor with a default location for downloads.
     * (Recommended Constructor)
     * @param version Which site data to download from the USGS GitLab
     */
    CONUS_Downloader(CONUS_Versions version) {
        this(version, getStoreDir());
    }

    private String getArchiveName() {
        return "nshm-conus-" + version.getTag() + ".zip";
    }

    private String getDownloadURL() {
        return BASE_URL + version.getTag() + "/" + getArchiveName() + "?ref_type=tags&path=site-data";
    }

    /**
     * Name of directory to store extracted site data.
     * Must be unique for each type of data to retrieve.
     */
    private String getSiteDataEntry() {
        // Simply uses the name of the CONUS_Versions Enum.
       return version.name();
    }

    /**
     * Get the default store directory for CONUS file downloads
     * @return	Default store directory to use in default constructor.
     */
    public static File getStoreDir() {
        Path storeDir = Paths.get(
                System.getProperty("user.home"), ".site_data", "conus");
        try {
            Files.createDirectories(storeDir);
        } catch (IOException e) {
            log.error("e: ", e);
            System.err.println("NSHM23_Downloader failed to create storeDir at " + storeDir);
        }
        return storeDir.toFile();
    }

    /**
     * Extracts the downloaded zip archive
     * Extracted archive name is same as `getSiteDataEntry`
     * @return path to extracted archive
     * @throws IOException
     */
    private File extractArchive() throws IOException {
        // Check if file exists or throw error
        File archive = new File(outputDir.getAbsolutePath(), getArchiveName());
        if (!outputDir.exists() || !archive.exists()) {
            String errMsg = "Failed to extract archive " + archive + ". Does not exist.";
            log.error(errMsg);
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, errMsg, "Extraction Failed", JOptionPane.ERROR_MESSAGE);
            }
            return null;
        }
        File target = new File(outputDir.getAbsolutePath(), getSiteDataEntry());

        // Create target directory if it doesn't exist
        if (!target.exists()) {
            target.mkdirs();
        }

        // Extract the zip file
        try (ZipFile zipFile = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            // The prefix we want to extract from (everything inside the second site-data/)
            String extractPrefix = "nshm-conus-" + version.getTag() + "-site-data/site-data/";

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryPath = entry.getName();

                // Only process entries that are inside the site-data directory
                if (entryPath.startsWith(extractPrefix)) {
                    // Remove the prefix to get the relative path within site-data
                    String relativePath = entryPath.substring(extractPrefix.length());

                    File entryFile = new File(target, relativePath);

                    // Create parent directories if they don't exist
                    if (entry.isDirectory()) {
                        entryFile.mkdirs();
                    } else {
                        // Create parent directories for the file
                        entryFile.getParentFile().mkdirs();

                        // Extract the file
                        try (InputStream is = zipFile.getInputStream(entry);
                             FileOutputStream fos = new FileOutputStream(entryFile)) {
                            IOUtils.copy(is, fos);
                        }
                    }
                }
                // Skip all other entries (like the top-level directory and anything outside site-data)
            }
        }

        // Delete the archive after extraction
        archive.delete();

        return target;
    }

    /**
     * Attempts to download and extract the site data for specified CONUS Versions.
     * @return path to the extracted archive where data can be found
     */
    public File downloadSiteData() {
        // Simply returns the path if data was already downloaded.
        // Check for the extracted archive to determine if the data exists.
        File target = new File(outputDir.getAbsolutePath(), getSiteDataEntry());
        if (D) System.out.println("Check if site data already exists at " + target.getAbsolutePath());
        if (target.exists()) {
           return target;
        }
        // Download the archive to the outputDir, then extract it and erase the archive.
        URL url;
        try {
            url = new URL(getDownloadURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Downloading CONUS Site Data for " + version.getDisplayName() + " from " + url);
        try {
            File downloadPath = new File(outputDir.getAbsolutePath(), getArchiveName());
            FileUtils.copyURLToFile(url, downloadPath);
            if (D) System.out.println("Done downloading to " + downloadPath);
            return extractArchive();
        } catch (IOException e) {
            log.error("e: ", e);
            String errMsg = "Failed to download CONUS Site Data for " + version.getDisplayName() + " at " + getDownloadURL() + ".\n"
                            + "USGS GitLab must be down for maintenance. Please try again later.";
            log.error(errMsg);
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, errMsg, "Download Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
        return null; // Failed to download data
    }

    /**
     * CLT to test CONUS site data retrieval from USGS GitLab
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        // Match name of the enum (not the display name)
        if (args.length != 1) {
            System.out.println("Usage: CONUS_Downloader <CONUS_Versions>");
            System.out.println("e.g., NSHM18, NSHM23");
            return;
        }
        CONUS_Versions version;
        try {
            version = Enum.valueOf(CONUS_Versions.class, args[0]);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid input: " + args[0]);
            System.out.println("Valid inputs are: ");
            for (CONUS_Versions v : CONUS_Versions.values()) {
                System.out.println("  " + v + " for " + v.getDisplayName());
            }
            return;
        }
        System.out.println("Downloaded: " + new CONUS_Downloader(version).downloadSiteData());
    }
}
