package com.collabnote.crdt;

public class Marker {
    public CRDTItem item;
    public int index;

    public Marker(CRDTItem item, int index) {
        this.item = item;
        this.index = index;
    }
}
