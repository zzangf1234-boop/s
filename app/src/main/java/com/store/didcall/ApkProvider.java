package com.store.didcall;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 외부 라이브러리 없이 APK 파일을 설치 앱에 넘겨 주기 위한 아주 작은 FileProvider.
 * AndroidManifest 에 authority = 패키지명.apk 로 등록.
 *_slaveUpdate 가 cache/update.apk 를 ACTION_VIEW 로 열 때 쓰임.
 */
public class ApkProvider extends ContentProvider {

    @Override public boolean onCreate() { return true; }

    private File fileFor(Uri uri) {
        return new File(getContext().getCacheDir(), "update.apk");
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri);
        if (!f.isFile()) throw new FileNotFoundException("no apk");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String sel, String[] args, String sort) {
        if (projection != null && projection.length > 0 && !OpenableColumns.DISPLAY_NAME.equals(projection[0])
                && !OpenableColumns.SIZE.equals(projection[0])) {
            // 필요한 열만 계산해 돌려 줌
        }
        String[] cols = projection != null && projection.length > 0 ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols);
        File f = fileFor(uri);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = "DID-Call-update.apk";
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.isFile() ? f.length() : 0L;
            else row[i] = null;
        }
        c.addRow(row);
        return c;
    }

    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String sel, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String sel, String[] args) { return 0; }
}
