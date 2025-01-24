package ru.itis.drawandguess.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

class Lobby {
    private String code;
    private String password;
    private int maxPlayers;
    private List<ClientHandler> players;
    private Map<ClientHandler, Integer> scores;
    private ClientHandler leader;
    private int currentRound = 0;
    private String currentWord;
    private ClientHandler currentDrawer;

    public Lobby(String code, String password, int maxPlayers, ClientHandler leader) {
        this.code = code;
        this.password = password;
        this.maxPlayers = maxPlayers;
        this.players = new ArrayList<>();
        this.scores = new HashMap<>();
        this.players.add(leader);
        this.scores.put(leader, 0);
        this.leader = leader;
    }

    public boolean addPlayer(ClientHandler client) {
        if (players.size() < maxPlayers) {
            players.add(client);
            scores.put(client, 0);
            return true;
        }
        return false;
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public void startGame(List<String> words) {
        currentRound = 1;
        broadcast("Game is starting!");
        nextRound(words);
    }

    public void nextRound(List<String> words) {
        if (currentRound > maxPlayers) {
            endGame();
            return;
        }

        Random random = new Random();
        currentDrawer = players.get((currentRound - 1) % players.size());
        currentWord = words.get(random.nextInt(words.size()));

        for (ClientHandler player : players) {
            if (player == currentDrawer) {
                player.sendMessage("YOU_ARE_DRAWER " + currentWord);
            } else {
                player.sendMessage("YOU_ARE_GUESSER");
            }
        }

        broadcast("Round " + currentRound + " has started. The drawer is " + currentDrawer.getNickname());
        currentRound++;
    }

    public void endGame() {
        broadcast("Game over! Here are the scores:");
        for (Map.Entry<ClientHandler, Integer> entry : scores.entrySet()) {
            broadcast(entry.getKey().getNickname() + ": " + entry.getValue() + " points");
        }
        players.clear();
    }

    public void broadcast(String message) {
        for (ClientHandler player : players) {
            player.sendMessage(message);
        }
    }

    public void broadcastToOthers(ClientHandler sender, String message) {
        for (ClientHandler player : players) {
            if (player != sender) {
                player.sendMessage(message);
            }
        }
    }

    public void handleGuess(ClientHandler guesser, String guess) {
        if (guesser != currentDrawer && guess.equalsIgnoreCase(currentWord)) {
            int newScore = scores.get(guesser) + 1;
            scores.put(guesser, newScore);
            broadcast("Player " + guesser.getNickname() + " guessed the word! The word was: " + currentWord);
            nextRound(Server.getWords()); // Берем слова из Server
        } else {
            broadcast(guesser.getNickname() + " guessed: " + guess);
        }
    }



    public List<ClientHandler> getPlayers() {
        return players;
    }

    public ClientHandler getCurrentDrawer() {
        return currentDrawer;
    }

    public String getCurrentWord() {
        return currentWord;
    }
}
