package ru.itis.drawandguess.server;

import java.util.*;
import java.util.concurrent.*;

public class GameManager {
    private List<ClientHandler> players = new ArrayList<>();
    private Map<ClientHandler, Integer> scores = new HashMap<>();

    private ClientHandler currentDrawer = null;
    private String currentWord = null;

    private int currentRound = 0;
    private int totalRounds;
    private boolean gameEnded = false;
    private boolean gameInProgress = false;

    private WordRepository wordRepository;
    private ScheduledExecutorService timerExecutor;
    private ScheduledFuture<?> currentTimer;

    // Ссылка на сервер, чтобы иметь возможность вызывать broadcast и т.д.
    private Server server;

    public GameManager(WordRepository wordRepository,
                       ScheduledExecutorService timerExecutor,
                       Server server) {
        this.wordRepository = wordRepository;
        this.timerExecutor = timerExecutor;
        this.server = server;
    }

    /**
     * Регистрируем нового игрока
     */
    public void registerPlayer(ClientHandler client) {
        players.add(client);
        scores.put(client, 0);
    }

    /**
     * Удаляем игрока
     */
    public void removePlayer(ClientHandler client) {
        players.remove(client);
        scores.remove(client);
    }

    public boolean isGameEnded() {
        return gameEnded;
    }

    public boolean isGameInProgress() {
        return gameInProgress;
    }

    /**
     * Сброс игры (когда все вышли, например)
     */
    public void resetGame() {
        gameEnded = false;
        gameInProgress = false;
        currentDrawer = null;
        currentRound = 0;
        totalRounds = 0;
        scores.clear();

        if (currentTimer != null) {
            currentTimer.cancel(false);
        }
    }

    /**
     * Запуск игры. Вызывается, когда набралось нужное число игроков.
     */
    public void startGame() {
        // Игра может начинаться заново, если была сброшена
        if (players.isEmpty()) {
            return;
        }
        gameInProgress = true;
        totalRounds = players.size();
        currentRound = 1;

        startRound();
    }

    private void startRound() {
        if (currentRound > totalRounds) {
            endGame();
            return;
        }

        Random random = new Random();
        currentDrawer = players.get((currentRound - 1) % players.size());
        currentWord = wordRepository.getRandomWord();

        // Уведомляем
        for (ClientHandler player : players) {
            if (player == currentDrawer) {
                player.sendMessage("YOU_ARE_DRAWER " + currentWord);
            } else {
                player.sendMessage("YOU_ARE_GUESSER");
            }
        }

        System.out.println("Round " + currentRound + ": Drawer: " + currentDrawer.getNickname()
                + ", Word: " + currentWord);

        startTimer();
    }

    /**
     * Обработка угадывания слова
     */
    public void handleGuess(String guess, ClientHandler guesser) {
        if (!gameInProgress) return;

        if (guess.equalsIgnoreCase(currentWord)) {
            // Останавливаем таймер
            if (currentTimer != null) {
                currentTimer.cancel(false);
            }
            // Добавляем очки
            scores.put(guesser, scores.get(guesser) + 1);

            // Оповещаем всех
            server.onCorrectGuess(guesser, currentWord);
            server.onScoresUpdated(getScoreBoard());

            nextRound();
        } else {
            // Просто рассылаем сообщение
            server.broadcast(guesser.getNickname() + ": " + guess);
        }
    }

    private void nextRound() {
        server.onClearCanvas();
        currentRound++;
        startRound();
    }

    private void endGame() {
        gameEnded = true;
        gameInProgress = false;

        // Останавливаем таймер, если он еще тикает
        if (currentTimer != null) {
            currentTimer.cancel(false);
        }
        server.onGameEnded(getScoreBoard());
    }

    /**
     * Запуск таймера на 60 сек
     */
    private void startTimer() {
        if (currentTimer != null) {
            currentTimer.cancel(false);
        }
        currentTimer = timerExecutor.schedule(() -> {
            // Время вышло
            server.onTimeIsUp(currentWord);
            nextRound();
        }, 60, TimeUnit.SECONDS);

        server.broadcast("You have 60 seconds to guess the word!");
    }

    /**
     * Возвращает текущего "художника"
     */
    public ClientHandler getCurrentDrawer() {
        return currentDrawer;
    }

    /**
     * Возвращает текущее слово
     */
    public String getCurrentWord() {
        return currentWord;
    }

    private String getScoreBoard() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ClientHandler, Integer> entry : scores.entrySet()) {
            sb.append(entry.getKey().getNickname())
                    .append(": ")
                    .append(entry.getValue())
                    .append(" points, ");
        }
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }
}
