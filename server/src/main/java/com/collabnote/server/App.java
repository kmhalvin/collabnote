/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package com.collabnote.server;

import java.net.ServerSocket;
import java.net.Socket;

import com.collabnote.server.collaborate.CollaborateDatabase;
import com.collabnote.server.socket.ClientHandler;
import com.collabnote.socket.Const;

public class App {
    public static void main(String[] args) {
        CollaborateDatabase collaborateDatabase = new CollaborateDatabase();

        try (ServerSocket socket = new ServerSocket(Const.PORT)) {
            while (true) {
                Socket cSocket = socket.accept();
                new ClientHandler(cSocket, collaborateDatabase).start();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
