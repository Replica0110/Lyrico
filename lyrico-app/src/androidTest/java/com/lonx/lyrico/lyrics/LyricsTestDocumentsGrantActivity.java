package com.lonx.lyrico.lyrics;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.provider.DocumentsContract;

/** Grants access from the provider's owning APK, using real Android URI permissions. */
public class LyricsTestDocumentsGrantActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ResultReceiver result = getIntent().getParcelableExtra("result");
        try {
            String target = getIntent().getStringExtra("targetPackage");
            String owner = getPackageName();
            if (!owner.endsWith(".test") || !owner.substring(0, owner.length() - 5).equals(target)) {
                throw new IllegalArgumentException("Unexpected target package");
            }
            grantUriPermission(target,
                DocumentsContract.buildTreeDocumentUri("com.lonx.lyrico.test.lyrics.documents", "root"),
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            if (result != null) result.send(0, null);
        } catch (Exception error) {
            Bundle detail = new Bundle();
            detail.putString("error", error.toString());
            if (result != null) result.send(1, detail);
        } finally { finish(); }
    }
}
