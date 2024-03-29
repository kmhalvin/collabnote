package com.collabnote.server.socket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import com.collabnote.crdt.CRDTItemSerializable;
import com.collabnote.server.collaborate.Collaborate;
import com.collabnote.server.collaborate.CollaborateDatabase;
import com.collabnote.socket.DataPayload;
import com.collabnote.socket.Type;

public class ClientHandler extends Thread {
    private CollaborateDatabase collaborateDatabase;

    private Collaborate collaborate;
    private int agent;
    private ClientState state = ClientState.UNKNOWN;
    private Socket clientSocket;
    private ObjectInputStream reader;
    private ObjectOutputStream writer;

    public ClientHandler(Socket clientSocket, CollaborateDatabase collaborateDatabase) {
        this.clientSocket = clientSocket;
        this.collaborateDatabase = collaborateDatabase;

        try {
            this.reader = new ObjectInputStream(clientSocket.getInputStream());
            this.writer = new ObjectOutputStream(clientSocket.getOutputStream());
        } catch (IOException e) {
        }
    }

    public void sendData(DataPayload data) {
        if (writer == null)
            return;

        if (data.getAgent() == 0)
            // server agent identity
            data.setAgent(-1);
        else if (data.getAgent() == this.agent)
            return;

        try {
            writer.writeObject(data);
        } catch (IOException e) {
        }
    }

    @Override
    public void run() {
        System.out.println("Connected socket");
        try {
            while (!this.clientSocket.isClosed()) {
                try {
                    DataPayload data = (DataPayload) this.reader.readObject();
                    switch (data.getType()) {
                        case CARET:
                            if (this.state == ClientState.READY)
                                this.collaborate.broadcast(data);
                            break;
                        case DELETE:
                            if (this.state == ClientState.READY) {
                                // this.collaborate.broadcast(data);
                                this.collaborate.delete(data.getCrdtItem());
                                sendData(DataPayload.donePayload(data.getCrdtItem()));
                            }
                            break;
                        case INSERT:
                            if (this.state == ClientState.READY) {
                                // this.collaborate.broadcast(data);
                                this.collaborate.insert(data.getCrdtItem());
                                sendData(DataPayload.donePayload(data.getCrdtItem()));
                            }
                            break;

                        // connection onboarding
                        case SHARE:
                            if (this.state == ClientState.UNKNOWN) {
                                this.state = ClientState.SHARING;
                                this.agent = data.getAgent();

                                String shareID;
                                do {
                                    shareID = this.collaborateDatabase.create();
                                } while (shareID == null);

                                this.collaborate = this.collaborateDatabase.get(shareID);

                                this.state = ClientState.READY;
                                this.collaborate.addClient(this);

                                sendData(new DataPayload(Type.CONNECT, shareID, null, 0, null));
                            } else if (this.state == ClientState.READY) {
                                this.collaborate.setReady(true);
                            }
                            break;

                        case CONNECT:
                            if (this.state == ClientState.UNKNOWN) {
                                this.state = ClientState.CONNECTING;
                                this.agent = data.getAgent();

                                this.collaborate = this.collaborateDatabase.get(data.getShareID());
                                if (this.collaborate != null && this.collaborate.isReady()) {
                                    this.state = ClientState.READY;

                                    // send to client to share their changes
                                    sendData(new DataPayload(Type.SHARE, this.collaborate.shareID, null, 0, null));
                                } else {
                                    this.clientSocket.close();
                                }
                            } else if (this.state == ClientState.READY) {
                                this.collaborate.addClient(this);

                                // share server state
                                for (CRDTItemSerializable crdtItem : this.collaborate.getVersionVector()) {
                                    sendData(DataPayload.insertPayload(this.collaborate.shareID, crdtItem));
                                }

                                sendData(new DataPayload(Type.CONNECT, this.collaborate.shareID, null, 0, null));
                            }
                            break;

                        // gc manager
                        case GC:
                            break;
                        default:
                            break;
                    }
                } catch (ClassNotFoundException e) {
                }
            }
        } catch (IOException e) {
        }

        try {
            this.reader.close();
        } catch (IOException e) {
        }
        try {
            this.writer.close();
        } catch (IOException e) {
        }

        if (this.collaborate != null)
            this.collaborate.removeClient(this);
    }

    public int getAgent() {
        return agent;
    }
}
