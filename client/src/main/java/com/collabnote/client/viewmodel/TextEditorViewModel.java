package com.collabnote.client.viewmodel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.text.BadLocationException;

import org.apache.commons.lang3.RandomUtils;

import com.collabnote.client.data.CollaborationRepository;
import com.collabnote.client.data.DocumentModel;
import com.collabnote.client.data.StateVisual;
import com.collabnote.client.data.StateVisualListener;
import com.collabnote.client.data.entity.DocumentEntity;
import com.collabnote.client.socket.ClientSocketListener;
import com.collabnote.client.ui.document.CRDTDocumentBind;
import com.collabnote.crdt.gc.DeleteGroupSerializable;
import com.collabnote.crdt.gc.GCCRDT;
import com.collabnote.crdt.time.TCRDT;
import com.collabnote.crdt.CRDT;
import com.collabnote.crdt.CRDTItem;
import com.collabnote.crdt.CRDTItemSerializable;
import com.collabnote.crdt.CRDTLocalListener;
import com.collabnote.socket.DataPayload;
import com.collabnote.socket.Type;

public class TextEditorViewModel implements CRDTLocalListener, ClientSocketListener {
    // user id
    public int agent = RandomUtils.nextInt();
    private CRDTDocumentBind document;
    private HashMap<Integer, Object> userCarets;

    private CollaborationRepository collaborationRepository;
    private DocumentModel documentModel;

    // data listener
    private TextEditorViewModelCaretListener caretListener;
    private TextEditorViewModelCollaborationListener collaborationListener;
    private TextEditorViewModelImageListener imageListener;

    private StateVisual stateVisual;

    public TextEditorViewModel(CRDTDocumentBind document) {
        this.collaborationRepository = new CollaborationRepository();
        this.documentModel = new DocumentModel();
        this.document = document;
        this.initDocument();
    }

    public CRDT getCurrentReplica() {
        return document.getEntity().getCrdtReplica();
    }

    // set listeners
    public void setCollaborationListener(TextEditorViewModelCollaborationListener collaborationListener) {
        this.collaborationListener = collaborationListener;
    }

    public void setCaretListener(TextEditorViewModelCaretListener caretListener) {
        this.caretListener = caretListener;
    }

    public void setImageListener(TextEditorViewModelImageListener imageListener) {
        this.imageListener = imageListener;
    }

    public void setStateVisualizer() throws IOException {
        if (this.stateVisual != null) {
            unsetStateVisualizer();
            return;
        }

        if (this.document.getEntity() == null || this.imageListener == null)
            return;

        this.stateVisual = new StateVisual(this.document.getEntity(), new StateVisualListener() {

            @Override
            public void updateImage(byte[] image) {
                imageListener.updateImage(image);
            }

        });
    }

    public void unsetStateVisualizer() {
        if (this.stateVisual == null)
            return;

        try {
            this.stateVisual.close();
            this.stateVisual = null;
            imageListener.updateImage(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // create new document
    public DocumentEntity initDocument() {
        if (this.collaborationRepository.isConnected())
            this.collaborationRepository.closeConnection();

        this.userCarets = new HashMap<>();

        this.document.bindCrdt(new DocumentEntity(createCRDTReplica()));
        return this.document.getEntity();
    }

    public CRDT createCRDTReplica() {
        if (this.stateVisual != null)
            unsetStateVisualizer();
        return new GCCRDT(this.agent, this.document, this);
    }

    // save load
    public void saveDocument(File targetFile) {
        if (this.document.getEntity() == null) {
            return;
        }

        this.documentModel.saveFile(this.document.getEntity(), targetFile);
    }

    public void loadDocument(File targetFile) {
        DocumentEntity entity = initDocument();
        this.documentModel.loadFile(entity, targetFile);

        if (entity.isCollaborating() && !this.collaborationRepository.isConnected()) {
            toggleConnection();
        }
    }

    // offline online toggle
    public void toggleConnection() {
        if (this.document.getEntity() == null) {
            return;
        }

        if (this.document.getEntity().isCollaborating()) {
            if (this.collaborationRepository.isConnected()) {
                this.collaborationRepository.closeConnection();
            } else {
                this.collaborationRepository.connectCRDT(this.document.getEntity().getOperationBuffer(),
                        this.document.getEntity().getServerHost(),
                        this.document.getEntity().getShareID(), this.agent, this);
            }
        }
    }

    // collaboration
    public void shareDocument(String host) {
        if (this.document.getEntity() == null) {
            return;
        }

        this.document.getEntity().setCollaboration(null, host, new ArrayList<>());

        collaborationRepository.shareCRDT(this.document.getEntity().getCrdtReplica(), host, this.agent, this);
    }

    public void connectDocument(String host, String shareID) {
        DocumentEntity entity = initDocument();

        entity.setCollaboration(shareID, host, new ArrayList<>());

        collaborationRepository.connectCRDT(entity.getOperationBuffer(), host, shareID, this.agent, this);
    }

    public void updateCaret(int index) {
        if (!this.document.getEntity().isCollaborating())
            return;

        this.collaborationRepository.sendCaret(this.document.getEntity().getShareID(), index);
    }

    // local CRDT update listener
    @Override
    public void afterLocalCRDTInsert(CRDTItem item) {
        if (this.stateVisual != null)
            this.stateVisual.triggerRender();

        if (!this.document.getEntity().isCollaborating())
            return;

        CRDTItemSerializable serializedItem = item.serialize();

        this.document.getEntity().addOperationBuffer(serializedItem);
        // might try to send in network thread by the queue
        this.collaborationRepository.sendInsert(this.document.getEntity().getShareID(), serializedItem);
    }

    @Override
    public void afterLocalCRDTDelete(List<CRDTItem> item) {
        if (this.stateVisual != null)
            this.stateVisual.triggerRender();

        if (!this.document.getEntity().isCollaborating())
            return;

        for (CRDTItem i : item) {
            CRDTItemSerializable serializedItem = i.serialize();

            this.document.getEntity().addOperationBuffer(serializedItem);
            this.collaborationRepository.sendDelete(this.document.getEntity().getShareID(), serializedItem);
        }
    }

    // socket listener
    @Override
    public void onStart() {
        // TODO Auto-generated method stub
    }

    @Override
    public void onReceiveData(DataPayload data) {
        switch (data.getType()) {
            case CARET:
                if (this.caretListener == null)
                    return;

                int index = data.getCaretIndex();
                int agent = data.getAgent();

                if (index == -1) {
                    Object tag = this.userCarets.remove(agent);
                    if (tag != null)
                        this.caretListener.removeCaretListener(tag);
                    return;
                }

                Object tag = this.userCarets.get(agent);
                if (tag != null)
                    this.caretListener.removeCaretListener(tag);
                try {
                    this.userCarets.put(agent, this.caretListener.addCaretListener(index));
                } catch (BadLocationException e1) {
                }
                break;
            case DELETE:
                this.document.getEntity().getCrdtReplica().tryRemoteDelete(data.getCrdtItem());
                break;
            case INSERT:
                this.document.getEntity().getCrdtReplica().tryRemoteInsert(data.getCrdtItem());
                break;
            // acknowledge sent
            case DONE:
                this.document.getEntity().ackOperationBuffer(data.getCrdtItem());
                break;
            // after connected
            case CONNECT:
                this.document.getEntity().setShareID(data.getShareID());
                if (this.collaborationListener != null)
                    this.collaborationListener.collaborationStatusListener(true, data.getShareID());
                break;
            // server going to share to client
            case SHARE:
                // empty replica
                this.document.getEntity().setCrdtReplica(createCRDTReplica());
                this.document.bindCrdt(this.document.getEntity());
                break;
            // gc crdt protocol
            case GC:
                if (!(this.document.getEntity().getCrdtReplica() instanceof GCCRDT))
                    break;
                List<DeleteGroupSerializable> success = ((GCCRDT) this.document.getEntity().getCrdtReplica())
                        .GC(data.getDeleteGroupList());
                if (success.size() > 0) {
                    this.collaborationRepository.sendGCAck(this.document.getEntity().getShareID(), success);
                }
                break;
            case RECOVER:
                if (!(this.document.getEntity().getCrdtReplica() instanceof GCCRDT))
                    break;
                ((GCCRDT) this.document.getEntity().getCrdtReplica()).recover(data.getDeleteGroupList(),
                        data.getCrdtItem());
                break;
            default:
                break;
        }
        if (this.stateVisual != null
                && (data.getType() == Type.INSERT
                        || data.getType() == Type.DELETE
                        || data.getType() == Type.GC
                        || data.getType() == Type.RECOVER))
            this.stateVisual.triggerRender();
    }

    @Override
    public void onFinished() {
        if (this.collaborationListener != null)
            this.collaborationListener.collaborationStatusListener(false, null);
        this.userCarets = new HashMap<>();
    }
}
