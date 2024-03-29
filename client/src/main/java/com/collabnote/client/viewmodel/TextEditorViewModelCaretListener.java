package com.collabnote.client.viewmodel;

import javax.swing.text.BadLocationException;

public interface TextEditorViewModelCaretListener {
    Object addCaretListener(int index) throws BadLocationException;

    void removeCaretListener(Object tag);
}
