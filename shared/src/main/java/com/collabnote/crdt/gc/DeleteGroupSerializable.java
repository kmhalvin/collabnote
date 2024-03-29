package com.collabnote.crdt.gc;

import java.io.Serializable;
import java.util.List;

import com.collabnote.crdt.CRDTItemSerializable;

public class DeleteGroupSerializable implements Serializable {
    public CRDTItemSerializable leftDeleteGroup;
    public CRDTItemSerializable rightDeleteGroup;
    public List<CRDTItemSerializable> gcItems;

    public DeleteGroupSerializable(CRDTItemSerializable leftDeleteGroup, CRDTItemSerializable rightDeleteGroup,
            List<CRDTItemSerializable> gcItems) {
        this.leftDeleteGroup = leftDeleteGroup;
        this.rightDeleteGroup = rightDeleteGroup;
        this.gcItems = gcItems;
    }
}
