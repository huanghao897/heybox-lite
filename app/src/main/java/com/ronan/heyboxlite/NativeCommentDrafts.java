package com.ronan.heyboxlite;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.lang.ref.WeakReference;

/** Keeps an acknowledgement tied to the editor revision that submitted it. */
final class NativeCommentDrafts {
    static final class Editor {
        final WeakReference<Object> entry;
        final String key;
        long revision;

        Editor(Object entry, String key) {
            this.entry = new WeakReference<>(entry);
            this.key = key;
        }
    }

    static final class Submission {
        final Editor editor;
        final long revision;
        final String draft;

        Submission(Editor editor, String draft) {
            this.editor = editor;
            this.revision = editor.revision;
            this.draft = draft;
        }
    }

    private final SessionStore session;
    private final Map<String, WeakReference<Editor>> editors = new HashMap<>();
    private WeakReference<Editor> activeEditor = new WeakReference<>(null);

    NativeCommentDrafts(SessionStore session) {
        this.session = session;
    }

    Editor open(Object entry, String key) {
        Editor editor = new Editor(entry, key);
        Iterator<WeakReference<Editor>> values = editors.values().iterator();
        while (values.hasNext()) if (values.next().get() == null) values.remove();
        editors.put(key, new WeakReference<>(editor));
        activeEditor = new WeakReference<>(editor);
        return editor;
    }

    boolean active(Editor editor) {
        return activeEditor.get() == editor;
    }

    void edit(Editor editor, String text) {
        if (!owns(editor)) return;
        editor.revision++;
        session.setCommentDraft(editor.key, text);
    }

    Submission submit(Editor editor) {
        return new Submission(editor, session.commentDraft(editor.key));
    }

    boolean clear(Submission sent) {
        Editor editor = sent.editor;
        if (!owns(editor) || editor.revision != sent.revision
                || !session.commentDraft(editor.key).equals(sent.draft)) return false;
        session.setCommentDraft(editor.key, "");
        editor.revision++;
        return true;
    }

    private boolean owns(Editor editor) {
        WeakReference<Editor> reference = editors.get(editor.key);
        return reference != null && reference.get() == editor;
    }
}
