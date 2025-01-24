package ru.itis.drawandguess.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 12345;
    private static Map<String, Lobby> lobbies = new HashMap<>();
    private static List<ClientHandler> clients = new ArrayList<>();
    private static List<String> words;

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

                ClientHandler clientHandler = new ClientHandler(clientSocket, clients, lobbies, words);
                clients.add(clientHandler);
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
    public static List<String> getWords() {
        return words;
    }

}
