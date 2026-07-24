/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 */

package org.telegram.messenger;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class TelethonSessionImporter {

    private static final int AUTH_KEY_LENGTH = 256;
    private static final int COPY_BUFFER_SIZE = 16 * 1024;
    private static final long MAX_SESSION_FILE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> TEST_SERVER_ADDRESSES = new HashSet<>(Arrays.asList(
            "149.154.175.40",
            "149.154.167.40",
            "149.154.175.10",
            "149.154.175.117",
            "2001:b28:f23d:f001::e",
            "2001:b28:f23d:f001:0:0:0:e",
            "2001:b28:f23d:f001:0000:0000:0000:000e",
            "2001:67c:4e8:f002::e",
            "2001:67c:4e8:f002:0:0:0:e",
            "2001:67c:4e8:f002:0000:0000:0000:000e",
            "2001:b28:f23d:f003::e",
            "2001:b28:f23d:f003:0:0:0:e",
            "2001:b28:f23d:f003:0000:0000:0000:000e"
    ));

    private TelethonSessionImporter() {
    }

    public static SessionData read(Context context, Uri uri) throws Exception {
        File sessionFile = File.createTempFile("telethon-session-", ".db", context.getCacheDir());
        try {
            copySessionFile(context, uri, sessionFile);
            return readSessionFile(sessionFile);
        } finally {
            if (!sessionFile.delete()) {
                sessionFile.deleteOnExit();
            }
        }
    }

    private static void copySessionFile(Context context, Uri uri, File sessionFile) throws Exception {
        try (
                InputStream inputStream = context.getContentResolver().openInputStream(uri);
                FileOutputStream outputStream = new FileOutputStream(sessionFile)
        ) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Unable to open Telethon session");
            }

            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            long totalSize = 0;
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalSize += bytesRead;
                if (totalSize > MAX_SESSION_FILE_SIZE) {
                    throw new IllegalArgumentException("Telethon session is too large");
                }
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    private static SessionData readSessionFile(File sessionFile) {
        try (
                SQLiteDatabase database = SQLiteDatabase.openDatabase(
                        sessionFile.getAbsolutePath(),
                        null,
                        SQLiteDatabase.OPEN_READONLY
                );
                Cursor cursor = database.rawQuery(
                        "SELECT dc_id, server_address, auth_key FROM sessions "
                                + "WHERE auth_key IS NOT NULL LIMIT 1",
                        null
                )
        ) {
            if (!cursor.moveToFirst()) {
                throw new IllegalArgumentException("Telethon session row not found");
            }

            int datacenterId = cursor.getInt(0);
            String serverAddress = cursor.getString(1);
            byte[] authKey = cursor.getBlob(2);
            if (datacenterId < 1 || datacenterId > 5) {
                throw new IllegalArgumentException("Invalid Telethon datacenter");
            }
            if (authKey == null || authKey.length != AUTH_KEY_LENGTH) {
                throw new IllegalArgumentException("Invalid Telethon authorization key");
            }

            boolean isTestBackend = serverAddress != null
                    && TEST_SERVER_ADDRESSES.contains(serverAddress.toLowerCase());
            if (isTestBackend && datacenterId > 3) {
                throw new IllegalArgumentException("Invalid Telethon test datacenter");
            }

            return new SessionData(
                    datacenterId,
                    authKey,
                    isTestBackend
            );
        }
    }

    public static final class SessionData {
        public final int datacenterId;
        public final byte[] authKey;
        public final boolean testBackend;

        private SessionData(int datacenterId, byte[] authKey, boolean testBackend) {
            this.datacenterId = datacenterId;
            this.authKey = authKey;
            this.testBackend = testBackend;
        }
    }
}
