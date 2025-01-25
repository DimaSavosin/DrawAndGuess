package ru.itis.drawandguess.server;

import java.io.*;
import java.net.Socket;
import java.util.List;

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

            String firstLine = in.readLine();
            if (firstLine == null) {
                socket.close();
                return;
            }

            if (firstLine.startsWith("CREATE LOBBY")) {
                if (Server.isLobbyCreated()) {
                    out.println("ERROR LOBBY_EXISTS");
                    socket.close();
                    return;
                } else {
                    String[] parts = firstLine.split(" ");
                    if (parts.length != 4) {
                        out.println("ERROR WRONG_COMMAND");
                        socket.close();
                        return;
                    }
                    String password = parts[2];
                    int maxPlayers;
                    try {
                        maxPlayers = Integer.parseInt(parts[3]);
                    } catch (NumberFormatException e) {
                        out.println("ERROR INVALID_PLAYERS_NUMBER");
                        socket.close();
                        return;
                    }
                    if (maxPlayers < 2 || maxPlayers > 4) {
                        out.println("ERROR INVALID_PLAYERS_NUMBER");
                        socket.close();
                        return;
                    }
                    Server.createLobby(password, maxPlayers);
                    out.println("OK Lobby created");
                }

            } else if (firstLine.startsWith("JOIN LOBBY")) {
                if (!Server.isLobbyCreated()) {
                    out.println("ERROR NO_LOBBY_YET");
                    socket.close();
                    return;
                } else {
                    String[] parts = firstLine.split(" ");
                    if (parts.length != 3) {
                        out.println("ERROR WRONG_COMMAND");
                        socket.close();
                        return;
                    }
                    String password = parts[2];
                    if (!Server.checkLobbyPassword(password)) {
                        out.println("ERROR WRONG_PASSWORD");
                        socket.close();
                        return;
                    }
                    if (clients.size() >= Server.getMaxPlayers()) {
                        out.println("ERROR LOBBY_FULL");
                        socket.close();
                        return;
                    }
                    out.println("OK Joined lobby");
                }

            } else {
                out.println("ERROR UNKNOWN_COMMAND");
                socket.close();
                return;
            }
            while (true) {
                nickname = in.readLine();
                if (nickname == null) {
                    break;
                }
                if (isNicknameUnique(nickname)) {
                    ready = true;
                    Server.addClient(this);

                    System.out.println(nickname + " joined the game.");
                    Server.broadcast(nickname + " has joined the game.");
                    Server.sendPlayerList();
                    Server.checkAndStartGame();
                    break;
                } else {
                    out.println("Nickname already in use. Please enter a different one.");
                }
            }

            String message;
            while ((message = in.readLine()) != null) {
                if (Server.isGameEnded()) {
                    Server.broadcast(nickname + ": " + message);
                    continue;
                }

                if (message.startsWith("PRESS") || message.startsWith("DRAG")) {
                    if (this == Server.getCurrentDrawer()) {
                        Server.broadcastDrawCommand("DRAW " + message, this);
                    } else {
                        sendMessage("You cannot draw. You are not the drawer.");
                    }
                } else if (message.equals("CLEAR_REQUEST")) {
                    if (this == Server.getCurrentDrawer()) {
                        Server.broadcast("CLEAR_CANVAS");
                    } else {
                        sendMessage("You cannot clear the canvas. You are not the drawer.");
                    }
                } else {
                    if (Server.getCurrentDrawer() != this) {
                        Server.handleGuess(message, this);
                    } else {
                        sendMessage("You are the drawer. You cannot guess.");
                    }
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
            if (nickname != null) {
                Server.broadcast(nickname + " has left the game.");
            }
            Server.sendPlayerList();
            Server.checkAndStartGame();
            if (clients.isEmpty()) {
                Server.resetLobby();
            }
        }

    }

    private boolean isNicknameUnique(String nickname) {
        for (ClientHandler client : clients) {
            if (client != this && client.getNickname() != null
                    && client.getNickname().equalsIgnoreCase(nickname)) {
                return false;
            }
        }
        return true;
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
}
