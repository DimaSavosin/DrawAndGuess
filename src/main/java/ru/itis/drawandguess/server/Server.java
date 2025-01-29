package ru.itis.drawandguess.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private int port;
    private List<ClientHandler> clients;
    private ScheduledExecutorService timerExecutor;

    // Через композицию будем иметь ссылки на управляющие объекты:
    private LobbyManager lobbyManager;
    private GameManager gameManager;

    // Конструктор
    public Server(int port) {
        this.port = port;
        this.clients = Collections.synchronizedList(new ArrayList<>());
        this.timerExecutor = Executors.newSingleThreadScheduledExecutor();

        // Создаем наши объекты по SRP
        WordRepository wordRepository = new WordRepository("src/main/resources/words.txt");
        this.lobbyManager = new LobbyManager();
        this.gameManager = new GameManager(wordRepository, timerExecutor, this);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                // Создаем ClientHandler и передаем ему ссылку на себя,
                // чтобы он мог вызывать методы broadcast(), lobbyManager и т.д.
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ===== Методы, вызываемые из ClientHandler =====

    /**
     * Попытка создать лобби.
     */
    public synchronized boolean tryCreateLobby(String password, int maxPlayers) {
        if (lobbyManager.isLobbyCreated()) {
            return false; // Уже создано
        }
        // Создаем
        lobbyManager.createLobby(password, maxPlayers);
        return true;
    }

    /**
     * Попытка присоединиться к лобби по паролю.
     */
    public synchronized boolean tryJoinLobby(String password) {
        if (!lobbyManager.isLobbyCreated()) {
            return false; // лобби еще нет
        }
        if (!lobbyManager.checkLobbyPassword(password)) {
            return false; // неверный пароль
        }
        // иначе OK
        return true;
    }

    public int getMaxPlayers() {
        return lobbyManager.getMaxPlayers();
    }

    /**
     * Добавляем клиента и проверяем, не пора ли стартовать игру.
     */
    public synchronized void addClient(ClientHandler clientHandler) {
        clients.add(clientHandler);
        gameManager.registerPlayer(clientHandler);

        checkAndStartGame();
    }

    /**
     * Удаляем клиента (когда он отключается).
     * Если список опустел – сброс лобби и игры.
     */
    public synchronized void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        gameManager.removePlayer(clientHandler);

        broadcast(clientHandler.getNickname() + " has left the game.");
        sendPlayerList();

        checkAndStartGame();  // возможно, кто-то ушел, а кто-то остался.

        // Если всех клиентов нет – сброс лобби
        if (clients.isEmpty()) {
            System.out.println("All players have left. Resetting lobby and game data...");
            broadcast("All players left. The lobby is reset.");
            lobbyManager.resetLobby();
            gameManager.resetGame();
        }
    }

    /**
     * Проверяем, достаточно ли игроков, чтобы начать игру.
     */
    private synchronized void checkAndStartGame() {
        if (lobbyManager.isLobbyCreated()
                && !gameManager.isGameInProgress()
                && clients.size() == lobbyManager.getMaxPlayers()
                && !gameManager.isGameEnded())
        {
            gameManager.startGame();
        }
    }

    // ===== Методы для отправки сообщений всем клиентам =====

    public void broadcast(String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(message);
            }
        }
    }

    /**
     * Отправить команду рисования всем, кроме отправителя
     */
    public void broadcastDrawCommand(String command, ClientHandler sender) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != sender) {
                    client.sendMessage(command);
                }
            }
        }
    }

    /**
     * Вывести список игроков
     */
    public void sendPlayerList() {
        StringBuilder playerList = new StringBuilder("Connected players: ");
        synchronized (clients) {
            for (ClientHandler client : clients) {
                playerList.append(client.getNickname()).append(", ");
            }
        }
        if (playerList.length() > 2) {
            playerList.setLength(playerList.length() - 2);
        }
        broadcast(playerList.toString());
    }

    // ===== Методы для передачи в GameManager (обработка угадывания и т.п.) =====

    public void onCorrectGuess(ClientHandler guesser, String currentWord) {
        broadcast("Player " + guesser.getNickname() + " guessed the word! The word was: " + currentWord);
    }

    public void onScoresUpdated(String scoresString) {
        broadcast("Score update: " + scoresString);
    }

    public void onTimeIsUp(String currentWord) {
        broadcast("Time is up! The word was: " + currentWord);
    }

    public void onClearCanvas() {
        broadcast("CLEAR_CANVAS");
    }

    public void onGameEnded(String finalScores) {
        broadcast("GAME_ENDED");
        broadcast("Game over! Thanks for playing.");
        broadcast("Final scores: " + finalScores);
        System.out.println("Game over! All rounds completed.");
    }
    public GameManager getGameManager() {
        return gameManager;
    }

    public List<ClientHandler> getClients() {
        return clients;
        // или, ещё лучше:
        // return Collections.unmodifiableList(clients);
    }


}
