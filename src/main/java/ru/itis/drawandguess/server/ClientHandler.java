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

    private Server server; // ссылка на наш сервер

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
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

            // Разбираем, CREATE LOBBY / JOIN LOBBY
            if (firstLine.startsWith("CREATE LOBBY")) {
                // Пример: CREATE LOBBY password 3
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
                // Пытаемся создать
                boolean created = server.tryCreateLobby(password, maxPlayers);
                if (!created) {
                    out.println("ERROR LOBBY_EXISTS");
                    socket.close();
                    return;
                }
                out.println("OK Lobby created");

            } else if (firstLine.startsWith("JOIN LOBBY")) {
                String[] parts = firstLine.split(" ");
                if (parts.length != 3) {
                    out.println("ERROR WRONG_COMMAND");
                    socket.close();
                    return;
                }
                String password = parts[2];
                boolean joined = server.tryJoinLobby(password);
                if (!joined) {
                    out.println("ERROR WRONG_PASSWORD_OR_NO_LOBBY");
                    socket.close();
                    return;
                }

                // Проверяем кол-во мест
                if (server.getMaxPlayers() > 0) {
                    // Можно проверить, не заполнено ли лобби
                    // Но это уже на уровне server.addClient
                }

                out.println("OK Joined lobby");
            } else {
                out.println("ERROR UNKNOWN_COMMAND");
                socket.close();
                return;
            }

            // Теперь ждем ник
            while (true) {
                nickname = in.readLine();
                if (nickname == null) {
                    break;
                }
                // Здесь можно проверить уникальность ника среди server.clients
                if (isNicknameUnique(nickname)) {
                    ready = true;
                    // Добавляем клиента в список
                    server.addClient(this);

                    System.out.println(nickname + " joined the game.");
                    server.broadcast(nickname + " has joined the game.");
                    server.sendPlayerList();
                    break;
                } else {
                    out.println("Nickname already in use. Please enter a different one.");
                }
            }

            // Обработка дальнейших сообщений
            String message;
            while ((message = in.readLine()) != null) {
                // Проверяем, кто рисует, и т.д. – узнаём у GameManager
                // но для простоты оставим логику, как было:

                // Если это команды рисования
                if (message.startsWith("PRESS") || message.startsWith("DRAG")) {
                    // Проверяем, является ли этот клиент рисующим
                    if (server.getGameManager().getCurrentDrawer() == this) {
                        server.broadcastDrawCommand("DRAW " + message, this);
                    } else {
                        sendMessage("You cannot draw. You are not the drawer.");
                    }
                } else if (message.equals("CLEAR_REQUEST")) {
                    if (server.getGameManager().getCurrentDrawer() == this) {
                        server.broadcast("CLEAR_CANVAS");
                    } else {
                        sendMessage("You cannot clear the canvas. You are not the drawer.");
                    }
                } else {
                    // Значит, это попытка угадать слово
                    if (server.getGameManager().getCurrentDrawer() != this) {
                        server.getGameManager().handleGuess(message, this);
                    } else {
                        sendMessage("You are the drawer. You cannot guess.");
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Connection with " + nickname + " lost.");
        } finally {
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        server.removeClient(this);
    }

    private boolean isNicknameUnique(String nickname) {
        // Можно тоже у Server попросить список игроков,
        // и там проверить.
        // Для упрощения здесь через broadcast-список
        return server.getClients().stream()
                .noneMatch(ch -> ch != this && nickname.equalsIgnoreCase(ch.getNickname()));
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
