package com.collabnote.newcrdt;

import java.util.NoSuchElementException;

public class Position {
    CRDTItem left;
    CRDTItem right;
    int index;

    public Position(CRDTItem left, CRDTItem right, int index) {
        this.left = left;
        this.right = right;
        this.index = index;
    }

    public void forward() {
        if (this.right == null) {
            throw new NoSuchElementException("null");
        }
        if (!this.right.isDeleted) {
            this.index += 1;
        }
        this.left = this.right;
        this.right = this.right.right;
    }
}
