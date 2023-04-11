package com.collabnote.documentcrdt;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.text.SimpleAttributeSet;

import org.apache.commons.math3.util.Pair;

import com.collabnote.newcrdt.CRDT;
import com.collabnote.newcrdt.CRDTItem;
import com.collabnote.newcrdt.CRDTListener;
import com.collabnote.newcrdt.Transaction;

public class CRDTDocument extends PlainDocument implements DocumentListener, CRDTListener {
    private CRDT crdt;

    public CRDTDocument(CRDT crdt) {
        super();
        this.crdt = crdt;
    }

    public CRDTDocument(Content c, CRDT crdt) {
        super(c);
        this.crdt = crdt;
        addDocumentListener(this);
    }

    @Override
    public void onCRDTInsert(Transaction transaction) {
        try {
            this.writeLock();

            Pair<Integer, CRDTItem> result = transaction.execute();
            if (result == null) {
                return;
            }

            String text = result.getSecond().content;
            this.getContent().insertString(result.getFirst(), text);
            DefaultDocumentEvent e = new CRDTDocumentEvent(result.getFirst(), text.length(),
                    DocumentEvent.EventType.INSERT);
            this.insertUpdate(e, new SimpleAttributeSet());
            e.end();

            this.fireInsertUpdate(e);
        } catch (BadLocationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            this.writeUnlock();
        }
    }

    @Override
    public void onCRDTDelete(Transaction transaction) {
        try {
            this.writeLock();

            Pair<Integer, CRDTItem> result = transaction.execute();
            if (result == null) {
                return;
            }

            int length = result.getSecond().content.length();
            DefaultDocumentEvent e = new CRDTDocumentEvent(result.getFirst(), length, DocumentEvent.EventType.REMOVE);
            this.removeUpdate(e);
            this.getContent().remove(result.getFirst(), length);
            this.postRemoveUpdate(e);
            e.end();

            this.fireRemoveUpdate(e);
        } catch (BadLocationException e1) {
            e1.printStackTrace();
        } finally {
            this.writeUnlock();
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        if (e instanceof CRDTDocumentEvent) {
            return;
        }

        String changes = "";
        int offset = e.getOffset();

        try {
            changes = this.getText(offset, e.getLength());
        } catch (BadLocationException e2) {
            changes = "";
        }

        crdt.localInsert(offset, changes);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        if (e instanceof CRDTDocumentEvent) {
            return;
        }

        int offset = e.getOffset();

        crdt.localDelete(offset, e.getLength());
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
    }

    class CRDTDocumentEvent extends DefaultDocumentEvent {
        public CRDTDocumentEvent(int offs, int len, EventType type) {
            super(offs, len, type);
        }
    }
}
