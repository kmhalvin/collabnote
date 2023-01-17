package com.collabnote.crdt;

import java.io.Serializable;

public class CRDTItem implements Serializable {
    String value;
    CRDTID id;
    CRDTID originLeft;
    CRDTID originRight;
    boolean isDeleted;
    int reference = 0;

    CRDTItem(String value, CRDTID id, CRDTID originLeft, CRDTID originRight, boolean isDeleted) {
        this.value = value;
        this.id = id;
        this.originLeft = originLeft;
        this.originRight = originRight;
        this.isDeleted = isDeleted;
    }

    public String getValue() {
        return value;
    }

    public void increaseReference() {
        this.reference++;
    }

    public void decreaseReference() {
        this.reference--;
    }

    // tombstone and no reference
    public boolean isRemovable() {
        return this.isDeleted && this.reference <= 0;
    }

    // tombstone and still has reference
    public boolean isActiveTombstone() {
        return this.isDeleted && this.reference > 0;
    }

    public void permaRemove() {
        this.isDeleted = true;
        this.reference = -1;
    }

    public boolean isPermaRemove() {
        return this.isDeleted && this.reference == -1;
    }

}
