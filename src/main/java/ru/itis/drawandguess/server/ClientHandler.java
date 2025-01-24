package ru.itis.drawandguess.server;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String nickname;
    private boolean ready = false;
    private List<ClientHandler> clients;

    public ClientHandler(Socket socket, List<ClientHandler> clients) {
        this.socket = socket;
        this.clients = clients;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            while (true) {
                out.println("Enter your nickname: ");
                nickname = in.readLine();
                if (isNicknameUnique(nickname)) {
                    ready = true;
                    break;
                } else {
                    out.println("Nickname already in use. Please enter a different one.");
                }
            }

            System.out.println(nickname + " joined the game.");
            Server.broadcast(nickname + " has joined the game.");
            Server.sendPlayerList();

            Server.checkAndStartGame();

            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("PRESS") || message.startsWith("DRAG")) {
                    if (this == Server.getCurrentDrawer()) {
                        Server.broadcastDrawCommand("DRAW " + message, this);
                    } else {
                        sendMessage("You cannot draw. You are not the drawer.");
                    }
                } else if (Server.getCurrentDrawer() != this) {
                    Server.handleGuess(message, this);
                } else {
                    sendMessage("You are the drawer. You cannot guess.");
                }
            }
        } catch (IOException e) {
            System.out.println("Connection with " + nickname + " lost.");
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            clients.remove(this);
            Server.broadcast(nickname + " has left the game.");
            Server.sendPlayerList();
            Server.checkAndStartGame();
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String getNickname() {
        return nickname;
    }

    public boolean isReady() {
        return ready;
    }

    private boolean isNicknameUnique(String nickname) {
        for (ClientHandler client : clients) {
            if (client != this && client.getNickname() != null && client.getNickname().equalsIgnoreCase(nickname)) {
                return false;
            }
        }
        return true;
    }
}
