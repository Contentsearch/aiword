package com.honsin.aiword.service;


import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.honsin.aiword.model.WordEntry;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service(Service.Level.APP)
public final class WordbookService {

    private List<WordEntry> currentlyLoadedWords = new ArrayList<>();

    private static final Logger LOG = Logger.getInstance(WordbookService.class);
    private static final Gson gson = new Gson(); // Create Gson instance

    private static class JsonWordStructure {
        String word;
        List<TranslationItem> translations;

    }

    private static class TranslationItem {
        String translation;
        String type;

    }


    // Get service instance
    public static WordbookService getInstance() {
        return ApplicationManager.getApplication().getService(WordbookService.class);
    }

    public WordbookService() {
        // Initial load can be done here or explicitly called
    }

    public synchronized List<WordEntry> loadWordsFromFile(Path specificFilePath) throws IOException {
        if (!Files.exists(specificFilePath) || !Files.isReadable(specificFilePath)) {
            throw new IOException("单词本文件不存在或无法读取: " + specificFilePath);
        }

        this.currentlyLoadedWords = loadJsonFile(specificFilePath);

        System.out.println("Successfully loaded " + this.currentlyLoadedWords.size() + " words from " + specificFilePath.getFileName());

        return new ArrayList<>(this.currentlyLoadedWords); // Return a copy
    }

    /**
     * Loads a single word entry from a JSON file.
     *
     * @param filePath Path to the .json file.
     */
    private List<WordEntry> loadJsonFile(Path filePath) {
        List<WordEntry> allWords = new ArrayList<>(8000);
        LOG.debug("Attempting to load JSON file (expecting array): " + filePath);
        try {
            // Read the entire file content as a String
            String jsonContent = Files.readString(filePath, StandardCharsets.UTF_8);

            // Use TypeToken to tell Gson to parse into a List<JsonWordStructure>
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<JsonWordStructure>>() {
            }.getType();
            List<JsonWordStructure> parsedWords = gson.fromJson(jsonContent, listType);

            if (parsedWords == null || parsedWords.isEmpty()) {
                LOG.warn("Skipping file " + filePath.getFileName() + ": JSON array is null or empty.");
                return allWords;
            }

            int loadedInFile = 0;
            // Iterate through each word object parsed from the array
            for (JsonWordStructure parsedWord : parsedWords) {
                // Validate parsed data for each word object
                if (parsedWord == null || parsedWord.word == null || parsedWord.word.trim().isEmpty()) {
                    LOG.warn("Skipping entry in file " + filePath.getFileName() + ": Missing or empty 'word' field in JSON object within the array.");
                    continue; // Skip this entry, process the next one
                }

                String word = parsedWord.word.trim();
                String combinedTranslation = combineTranslations(parsedWord.translations);

                if (combinedTranslation.isEmpty()) {
                    LOG.warn("Skipping word '" + word + "' from file " + filePath.getFileName() + ": No valid translations found in JSON object.");
                    continue; // Skip this entry
                }

                // Create and add the WordEntry
                allWords.add(new WordEntry(word, combinedTranslation));
                loadedInFile++;
            }
            LOG.debug("Successfully loaded " + loadedInFile + " words from " + filePath.getFileName());

        } catch (IOException e) {
            LOG.error("Error reading JSON file: " + filePath, e);
        } catch (JsonSyntaxException e) {
            // This error might now indicate the file doesn't contain a valid JSON array
            // or objects within the array are malformed.
            LOG.error("Error parsing JSON file (expected an array of objects): " + filePath + ". Details: " + e.getMessage(), e);
        } catch (Exception e) { // Catch unexpected errors during processing
            LOG.error("Unexpected error processing file: " + filePath, e);
        }
        return allWords;
    }

    /**
     * Combines translations from the parsed JSON structure into the desired format.
     * Example: "n. 能力，能耐；才能; v. 使能够"
     *
     * @param translationItems List of TranslationItem objects.
     * @return A combined string representation of translations, or empty string if none are valid.
     */
    private String combineTranslations(List<TranslationItem> translationItems) {
        if (translationItems == null || translationItems.isEmpty()) {
            return "";
        }

        // Use Stream API for a concise way to format and join
        return translationItems.stream()
                .filter(item -> item != null && item.translation != null && !item.translation.trim().isEmpty()) // Filter out invalid items
                .map(item -> {
                    String type = (item.type != null && !item.type.trim().isEmpty()) ? item.type.trim() + ". " : ""; // Add type prefix if available
                    return type + item.translation.trim();
                })
                .collect(Collectors.joining("; ")); // Join with semicolon and space

    }


    /**
     * Gets a specified number of random words from the loaded list.
     * (This method remains the same)
     *
     * @param count Number of words to get.
     * @return A list of random WordEntry objects, or an empty list if no words are loaded.
     */
    public List<WordEntry> getRandomWords(int count, List<WordEntry> allLoadedWords) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        List<WordEntry> shuffled = new ArrayList<>(allLoadedWords);
        Collections.shuffle(shuffled);

        int actualCount = Math.min(count, shuffled.size());
        return shuffled.subList(0, actualCount);
    }


}