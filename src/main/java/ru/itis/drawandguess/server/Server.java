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

    private static boolean lobbyCreated = false;
    private static String lobbyPassword = null;
    private static int maxPlayers = 0;

    private static boolean gameEnded = false;

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

                ClientHandler clientHandler = new ClientHandler(clientSocket, clients);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String> loadWordsFromFile() {
        List<String> loadedWords = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("src/main/resources/words.txt"))) {
            String word;
            while ((word = reader.readLine()) != null) {
                loadedWords.add(word.trim());
            }
        } catch (IOException e) {
            System.err.println("Error reading words from file: " + e.getMessage());
        }
        return loadedWords;
    }

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

    public static void addClient(ClientHandler clientHandler) {
        clients.add(clientHandler);
        scores.put(clientHandler, 0); // По умолчанию 0 очков
    }

    public static synchronized void checkAndStartGame() {
        if (lobbyCreated && currentDrawer == null && clients.size() == maxPlayers && !gameEnded) {
            totalRounds = clients.size();
            currentRound = 1;
            startGame();
        }
    }

    private static void startGame() {
        // Если уже все раунды прошли — заканчиваем
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

        System.out.println("Round " + currentRound + ": Drawer: " + currentDrawer.getNickname()
                + ", Word: " + currentWord);
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
            // Просто рассылаем догадку всем в чат (никакого "Incorrect" не пишем)
            broadcast(guesser.getNickname() + ": " + guess);
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
        // Посылаем клиентам специальное сообщение о завершении игры
        broadcast("GAME_ENDED");

        broadcast("Game over! Thanks for playing.");
        broadcast("Final scores: " + getScoreBoard());
        System.out.println("Game over! All rounds completed.");

        if (currentTimer != null) {
            currentTimer.cancel(false);
        }
        currentDrawer = null;
        currentRound = 0;
        gameEnded = true;
    }

    public static boolean isGameEnded() {
        return gameEnded;
    }

    public static ClientHandler getCurrentDrawer() {
        return currentDrawer;
    }

    private static String getScoreBoard() {
        StringBuilder scoreBoard = new StringBuilder();
        for (Map.Entry<ClientHandler, Integer> entry : scores.entrySet()) {
            scoreBoard.append(entry.getKey().getNickname())
                    .append(": ")
                    .append(entry.getValue())
                    .append(" points, ");
        }
        if (scoreBoard.length() > 2) {
            scoreBoard.setLength(scoreBoard.length() - 2);
        }
        return scoreBoard.toString();
    }

    // ------------------- Вспомогательные методы -------------------
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
    public static void resetLobby() {
        System.out.println("All players have left. Resetting lobby and game data...");

        broadcast("All players left. The lobby is reset.");

        lobbyCreated = false;
        lobbyPassword = null;
        maxPlayers = 0;

        gameEnded = false;
        currentDrawer = null;
        currentWord = null;
        currentRound = 0;
        totalRounds = 0;

        scores.clear();
    }
}
