package ru.itis.drawandguess.server;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WordRepository {
    private List<String> words;
    private Random random;

    public WordRepository(String filePath) {
        this.words = loadWordsFromFile(filePath);
        this.random = new Random();

        if (words.isEmpty()) {
            System.err.println("No words available for the game. Check " + filePath);
            // Можно выбросить исключение или завершить программу
        }
    }

    private List<String> loadWordsFromFile(String filePath) {
        List<String> loadedWords = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String word;
            while ((word = reader.readLine()) != null) {
                String w = word.trim();
                if (!w.isEmpty()) {
                    loadedWords.add(w);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading words from file: " + e.getMessage());
        }
        return loadedWords;
    }

    public String getRandomWord() {
        if (words.isEmpty()) {
            return "NO_WORDS";
        }
        return words.get(random.nextInt(words.size()));
    }
}
