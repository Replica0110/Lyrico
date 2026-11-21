package com.lonx.lyrico.lyrics;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Runs in the test APK's own process, without relying on the target APK's Kotlin runtime. */
public class LyricsTestDocumentsProvider extends DocumentsProvider {
    private File root;

    @Override public boolean onCreate() {
        try {
            root = new File(getContext().getCacheDir(), "lyrics-documents").getCanonicalFile();
            if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create fixture directory");
            return true;
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    @Override public Cursor queryRoots(String[] projection) {
        String[] columns = projection != null ? projection : new String[] {
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS };
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            switch (columns[i]) {
                case Root.COLUMN_ROOT_ID: case Root.COLUMN_DOCUMENT_ID: values[i] = "root"; break;
                case Root.COLUMN_TITLE: values[i] = "Lyrics test files"; break;
                case Root.COLUMN_FLAGS: values[i] = Root.FLAG_SUPPORTS_CREATE | Root.FLAG_LOCAL_ONLY; break;
            }
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = documentCursor(projection);
        addDocument(cursor, documentId, resolve(documentId));
        return cursor;
    }

    @Override public boolean isChildDocument(String parentId, String documentId) {
        try {
            return resolve(documentId).getPath().startsWith(resolve(parentId).getPath() + File.separator);
        } catch (FileNotFoundException error) { return false; }
    }

    @Override public Cursor queryChildDocuments(String parentId, String[] projection, String sortOrder) throws FileNotFoundException {
        MatrixCursor cursor = documentCursor(projection);
        File[] files = resolve(parentId).listFiles();
        if (files != null) for (File file : files) addDocument(cursor, parentId + "/" + file.getName(), file);
        return cursor;
    }

    @Override public String createDocument(String parentId, String mimeType, String displayName) throws FileNotFoundException {
        if (!displayName.equals(new File(displayName).getName()) || displayName.equals(".") || displayName.equals("..")) {
            throw new IllegalArgumentException("Invalid fixture name");
        }
        String id = parentId + "/" + displayName;
        File file = resolve(id);
        try {
            boolean created = Document.MIME_TYPE_DIR.equals(mimeType) ? file.mkdir() : file.createNewFile();
            if (!created) throw new IOException("Fixture already exists");
        } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        return id;
    }

    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(documentId), ParcelFileDescriptor.parseMode(mode));
    }

    @Override public void deleteDocument(String documentId) throws FileNotFoundException {
        if (documentId.equals("root")) throw new IllegalArgumentException("Cannot delete fixture root");
        delete(resolve(documentId));
    }

    private void delete(File file) throws FileNotFoundException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        if (!file.delete()) throw new FileNotFoundException("Cannot remove fixture: " + file);
    }

    private File resolve(String id) throws FileNotFoundException {
        if (!id.equals("root") && !id.startsWith("root/")) throw new IllegalArgumentException("Invalid document ID");
        try {
            File file = id.equals("root") ? root : new File(root, id.substring(5)).getCanonicalFile();
            if (!file.equals(root) && !file.getPath().startsWith(root.getPath() + File.separator)) {
                throw new IllegalArgumentException("Document outside fixture root");
            }
            return file;
        } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
    }

    private MatrixCursor documentCursor(String[] projection) {
        return new MatrixCursor(projection != null ? projection : new String[] {
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED });
    }

    private void addDocument(MatrixCursor cursor, String id, File file) {
        String[] columns = cursor.getColumnNames();
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            switch (columns[i]) {
                case Document.COLUMN_DOCUMENT_ID: values[i] = id; break;
                case Document.COLUMN_DISPLAY_NAME: values[i] = file.getName(); break;
                case Document.COLUMN_MIME_TYPE: values[i] = file.isDirectory() ? Document.MIME_TYPE_DIR : "application/octet-stream"; break;
                case Document.COLUMN_FLAGS: values[i] = Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE |
                    (file.isDirectory() ? Document.FLAG_DIR_SUPPORTS_CREATE : 0); break;
                case Document.COLUMN_SIZE: values[i] = file.length(); break;
                case Document.COLUMN_LAST_MODIFIED: values[i] = file.lastModified(); break;
            }
        }
        cursor.addRow(values);
    }
}
