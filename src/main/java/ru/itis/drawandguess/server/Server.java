package ru.itis.drawandguess.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 12345;
    private static List<ClientHandler> clients = new ArrayList<>();
    private static List<String> words;
    private static ClientHandler currentDrawer = null;
    private static String currentWord = null;
    private static int currentRound = 0;
    private static int totalRounds;
    private static Map<ClientHandler, Integer> scores = new HashMap<>();
    private static ScheduledExecutorService timerExecutor = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> currentTimer;

    // ------------------ Новые поля для лобби ------------------
    private static boolean lobbyCreated = false;
    private static String lobbyPassword = null;
    private static int maxPlayers = 0;

    public static void main(String[] args) {
        words = loadWordsFromFile();
        if (words.isEmpty()) {
            System.err.println("No words available for the game. Please check words.txt.");
            System.exit(1);
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                // Каждый раз создаём новый ClientHandler
                ClientHandler clientHandler = new ClientHandler(clientSocket, clients);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String> loadWordsFromFile() {
        List<String> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("src/main/resources/words.txt"))) {
            String word;
            while ((word = reader.readLine()) != null) {
                words.add(word.trim());
            }
        } catch (IOException e) {
            System.err.println("Error reading words from file: " + e.getMessage());
        }
        return words;
    }

    public static void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    public static void broadcastDrawCommand(String command, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(command);
            }
        }
    }

    // -----------------------------------------------------------
    //           ЛОГИКА ЛОББИ + ЛОГИКА ИГРЫ
    // -----------------------------------------------------------

    /**
     * Проверяем, можно ли начать игру (если набралось нужное число игроков и лобби создано).
     * В исходном коде было: если клиентов >=2 и все готовы. Здесь упрощённо:
     * Начинаем игру, если число клиентов == maxPlayers (которое указал создающий).
     */
    public static synchronized void checkAndStartGame() {
        // Если игра ещё не идёт (currentDrawer == null) и лобби создано
        // и уже набралось именно maxPlayers подключённых клиентов:
        if (lobbyCreated && currentDrawer == null && clients.size() == maxPlayers) {
            totalRounds = clients.size(); // столько раундов, сколько игроков
            currentRound = 1;
            startGame();
        }
    }

    private static void startGame() {
        if (currentRound > totalRounds) {
            endGame();
            return;
        }

        Random random = new Random();
        currentDrawer = clients.get((currentRound - 1) % clients.size());
        currentWord = words.get(random.nextInt(words.size()));

        for (ClientHandler client : clients) {
            if (client == currentDrawer) {
                client.sendMessage("YOU_ARE_DRAWER " + currentWord);
            } else {
                client.sendMessage("YOU_ARE_GUESSER");
            }
        }

        System.out.println("Round " + currentRound + ": Drawer: " + currentDrawer.getNickname() + ", Word: " + currentWord);
        startTimer();
    }

    public static void handleGuess(String guess, ClientHandler guesser) {
        if (guess.equalsIgnoreCase(currentWord)) {
            if (currentTimer != null) {
                currentTimer.cancel(false);
            }
            scores.put(guesser, scores.get(guesser) + 1);
            broadcast("Player " + guesser.getNickname() + " guessed the word! The word was: " + currentWord);
            broadcast("Score update: " + getScoreBoard());
            nextRound();
        } else {
            guesser.sendMessage("Incorrect guess. Try again.");
        }
    }

    private static void startTimer() {
        if (currentTimer != null) {
            currentTimer.cancel(false);
        }

        currentTimer = timerExecutor.schedule(() -> {
            broadcast("Time is up! The word was: " + currentWord);
            nextRound();
        }, 60, TimeUnit.SECONDS);

        broadcast("You have 60 seconds to guess the word!");
    }

    public static void nextRound() {
        broadcast("CLEAR_CANVAS");
        currentRound++;
        startGame();
    }

    public static void endGame() {
        broadcast("Game over! Thanks for playing.");
        broadcast("Final scores: " + getScoreBoard());
        System.out.println("Game over! All rounds completed.");
        if (currentTimer != null) {
            currentTimer.cancel(false);
        }
        currentDrawer = null;
        currentRound = 0;
    }

    public static void sendPlayerList() {
        StringBuilder playerList = new StringBuilder("Connected players: ");
        for (ClientHandler client : clients) {
            playerList.append(client.getNickname()).append(", ");
        }
        if (playerList.length() > 2) {
            playerList.setLength(playerList.length() - 2);
        }
        broadcast(playerList.toString());
    }

    public static ClientHandler getCurrentDrawer() {
        return currentDrawer;
    }

    private static String getScoreBoard() {
        StringBuilder scoreBoard = new StringBuilder();
        for (Map.Entry<ClientHandler, Integer> entry : scores.entrySet()) {
            scoreBoard.append(entry.getKey().getNickname()).append(": ")
                    .append(entry.getValue()).append(" points, ");
        }
        if (scoreBoard.length() > 2) {
            scoreBoard.setLength(scoreBoard.length() - 2);
        }
        return scoreBoard.toString();
    }

    // Геттеры и сеттеры для лобби
    public static boolean isLobbyCreated() {
        return lobbyCreated;
    }

    public static void createLobby(String password, int maxPlayersCount) {
        lobbyCreated = true;
        lobbyPassword = password;
        maxPlayers = maxPlayersCount;
    }

    public static boolean checkLobbyPassword(String password) {
        return lobbyPassword != null && lobbyPassword.equals(password);
    }

    public static int getMaxPlayers() {
        return maxPlayers;
    }

    // Когда клиент успешно зашёл, добавляем очки (по умолчанию 0)
    public static void addClient(ClientHandler clientHandler) {
        clients.add(clientHandler);
        scores.put(clientHandler, 0);
    }
}
