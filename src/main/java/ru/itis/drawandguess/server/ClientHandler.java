package ru.itis.drawandguess.server;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String nickname;
    private Lobby lobby;
    private List<ClientHandler> clients;
    private Map<String, Lobby> lobbies;
    private List<String> words;

    public ClientHandler(Socket socket, List<ClientHandler> clients, Map<String, Lobby> lobbies, List<String> words) {
        this.socket = socket;
        this.clients = clients;
        this.lobbies = lobbies;
        this.words = words;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            while (true) {
                out.println("Enter your nickname:");
                nickname = in.readLine();
                if (nickname != null && !nickname.trim().isEmpty()) {
                    break;
                }
            }

            out.println("Welcome, " + nickname + "!");
            while (true) {
                String command = in.readLine();
                if (command.startsWith("CREATE_LOBBY")) {
                    String[] parts = command.split(" ", 3);
                    String password = parts[1];
                    int maxPlayers = Integer.parseInt(parts[2]);
                    createLobby(password, maxPlayers);
                } else if (command.startsWith("JOIN_LOBBY")) {
                    String password = command.split(" ", 2)[1];
                    joinLobby(password);
                } else if (command.startsWith("GUESS")) {
                    String guess = command.substring(6);
                    handleGuess(guess);
                } else if (command.startsWith("DRAW")) {
                    handleDrawCommand(command);
                } else {
                    handleChatMessage(command);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createLobby(String password, int maxPlayers) {
        synchronized (lobbies) {
            if (!lobbies.containsKey(password)) {
                String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                lobby = new Lobby(code, password, maxPlayers, this);
                lobbies.put(password, lobby);
                out.println("LOBBY_CREATED " + code);
            } else {
                out.println("ERROR: Lobby already exists with this password.");
            }
        }
    }

    private void joinLobby(String password) {
        synchronized (lobbies) {
            Lobby lobby = lobbies.get(password);
            if (lobby != null && !lobby.isFull()) {
                this.lobby = lobby;
                lobby.addPlayer(this);
                out.println("LOBBY_JOINED");
                lobby.broadcast(nickname + " has joined the lobby.");
                if (lobby.isFull()) {
                    lobby.startGame(words);
                }
            } else {
                out.println("ERROR: Lobby not found or full.");
            }
        }
    }

    public String getNickname() {
        return nickname;
    }

    private void handleGuess(String guess) {
        if (lobby != null) {
            lobby.handleGuess(this, guess); // Передаем угадывание в Lobby
        } else {
            sendMessage("You are not in a lobby.");
        }
    }



    private void handleChatMessage(String message) {
        if (lobby != null) {
            if (lobby.getCurrentDrawer() != this && message.equalsIgnoreCase(lobby.getCurrentWord())) {
                lobby.handleGuess(this, message);
            } else {
                lobby.broadcast(nickname + ": " + message);
            }
        } else {
            sendMessage("You are not in a lobby.");
        }
    }

    private void handleDrawCommand(String command) {
        if (lobby != null && lobby.getCurrentDrawer() == this) {
            lobby.broadcastToOthers(this, command);
        } else {
            sendMessage("You cannot draw right now.");
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }
}
