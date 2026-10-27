/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: 2020-2022 SHIFT GmbH
 * SPDX-License-Identifier: Apache-2.0
 */
package org.uwuaosp.updater;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.uwuaosp.updater.controller.UpdaterController;
import org.uwuaosp.updater.data.Update;
import org.uwuaosp.updater.data.UpdateStatus;
import org.uwuaosp.updater.misc.Utils;
import org.uwuaosp.updater.util.OtaMetadataParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;

public class UpdateImporter {
    private static final int REQUEST_PICK = 9061;
    private static final String TAG = "UpdateImporter";
    private static final String MIME_ZIP = "application/zip";

    private final Activity activity;
    private final Callbacks callbacks;

    private Thread workingThread;

    public UpdateImporter(Activity activity, Callbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
    }

    public synchronized void stopImport() {
        if (workingThread != null && workingThread.isAlive()) {
            workingThread.interrupt();
        }
    }

    public void openImportPicker() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(MIME_ZIP);
        activity.startActivityForResult(intent, REQUEST_PICK);
    }

    public boolean onResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || requestCode != REQUEST_PICK) {
            return false;
        }

        if (data == null || data.getData() == null) return false;
        return onPicked(data.getData());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private synchronized boolean onPicked(Uri uri) {
        if (workingThread != null && workingThread.isAlive()) return true;
        callbacks.onImportStarted();

        workingThread = new Thread(() -> {
            File importedFile = null;
            try {
                importedFile = importFile(uri);
                verifyPackage(importedFile);
                final Update update = buildLocalUpdate(importedFile);
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                addUpdate(update);
                activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed()) callbacks.onImportCompleted(update);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to import update package", e);
                // Do not store invalid update
                if (importedFile != null) {
                    importedFile.delete();
                }

                activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed()) callbacks.onImportCompleted(null);
                });
            }
        });
        workingThread.start();
        return true;
    }

    @SuppressLint("SetWorldReadable")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private File importFile(Uri uri) throws IOException {
        final File downloadDir = Utils.getDownloadPath(activity);
        // 每次导入拥有独立文件，失败不能删除上一次已验证的本地包
        final File outFile = File.createTempFile("local-", ".zip", downloadDir);
        try (ParcelFileDescriptor descriptor = activity.getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor == null) throw new IOException("Failed to obtain fileDescriptor");
            try (FileInputStream input = new FileInputStream(descriptor.getFileDescriptor());
                    FileOutputStream output = new FileOutputStream(outFile)) {
                int read;
                final byte[] buffer = new byte[64 * 1024];
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
            }
        } catch (IOException | RuntimeException e) {
            outFile.delete();
            throw e;
        }

        outFile.setReadable(true, false);

        return outFile;
    }

    private Update buildLocalUpdate(File file) throws IOException {
        final OtaMetadataParser metadata = new OtaMetadataParser(file);
        final String name = activity.getString(R.string.local_update_name);
        return new Update.Builder()
            .setName(name)
            .setFile(file)
            .setFileSize(file.length())
            .setTimestamp(metadata.getTimestamp())
            .setOsPatchLevel(metadata.getSecurityPatchLevel())
            .setOsSdkLevel(metadata.getSdkLevel())
            .setStatus(UpdateStatus.VERIFIED)
            .setVersion(metadata.getVersion().isEmpty() ? name : metadata.getVersion())
            .build();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void verifyPackage(File file) throws Exception {
        try {
            android.os.RecoverySystem.verifyPackage(file, null, null);
        } catch (Exception e) {
            if (file.exists()) {
                file.delete();
                throw new Exception("Verification failed, file has been deleted");
            } else {
                throw e;
            }
        }
    }

    private void addUpdate(Update update) {
        UpdaterController controller = UpdaterController.getInstance(activity);
        controller.addLocalUpdate(update);
    }

    public interface Callbacks {
        void onImportStarted();

        void onImportCompleted(Update update);
    }
}
