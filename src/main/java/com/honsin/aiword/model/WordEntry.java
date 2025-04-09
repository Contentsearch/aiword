package com.honsin.aiword.model;

import java.util.Objects;

public class WordEntry {
    private final String word;
    private final String translation;

    public WordEntry(String word, String translation) {
        this.word = Objects.requireNonNull(word, "Word cannot be null").trim();
        this.translation = Objects.requireNonNull(translation, "Translation cannot be null").trim();
    }

    public String getWord() {
        return word;
    }

    public String getTranslation() {
        return translation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WordEntry wordEntry = (WordEntry) o;
        return Objects.equals(word, wordEntry.word); // Usually, uniqueness is based on the word itself
    }

    @Override
    public int hashCode() {
        return Objects.hash(word);
    }

    @Override
    public String toString() {
        return "WordEntry{" +
                "word='" + word + '\'' +
                ", translation='" + translation + '\'' +
                '}';
    }
}