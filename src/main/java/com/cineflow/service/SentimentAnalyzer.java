package com.cineflow.service;

import com.cineflow.model.ReviewSentiment;

import java.util.*;

/**
 * CineFlow v3 — Pure, DB-free Explainable Lexical Sentiment Analysis Engine.
 * Tailored for cinematic reviews, star ratings, and audience feedback.
 */
public class SentimentAnalyzer {

    private static final Map<String, Double> POSITIVE_LEXICON = new HashMap<>();
    private static final Map<String, Double> NEGATIVE_LEXICON = new HashMap<>();
    private static final Set<String> NEGATORS = new HashSet<>(Arrays.asList(
            "not", "never", "no", "hardly", "barely", "scarcely", "without"
    ));
    private static final Set<String> INTENSIFIERS = new HashSet<>(Arrays.asList(
            "very", "extremely", "absolutely", "so", "truly", "really", "deeply", "highly"
    ));

    static {
        // High-impact cinema positive words
        POSITIVE_LEXICON.put("masterpiece", 2.5);
        POSITIVE_LEXICON.put("mindblowing", 2.2);
        POSITIVE_LEXICON.put("breathtaking", 2.0);
        POSITIVE_LEXICON.put("phenomenal", 2.0);
        POSITIVE_LEXICON.put("spectacular", 1.8);
        POSITIVE_LEXICON.put("brilliant", 1.8);
        POSITIVE_LEXICON.put("thrilling", 1.6);
        POSITIVE_LEXICON.put("superb", 1.6);
        POSITIVE_LEXICON.put("gripping", 1.5);
        POSITIVE_LEXICON.put("captivating", 1.5);
        POSITIVE_LEXICON.put("stellar", 1.5);
        POSITIVE_LEXICON.put("perfection", 1.8);
        POSITIVE_LEXICON.put("excellent", 1.4);
        POSITIVE_LEXICON.put("amazing", 1.3);
        POSITIVE_LEXICON.put("awesome", 1.2);
        POSITIVE_LEXICON.put("loved", 1.2);
        POSITIVE_LEXICON.put("great", 1.0);
        POSITIVE_LEXICON.put("good", 0.7);
        POSITIVE_LEXICON.put("enjoyable", 0.8);
        POSITIVE_LEXICON.put("entertaining", 0.9);
        POSITIVE_LEXICON.put("impressive", 1.1);

        // High-impact cinema negative words
        NEGATIVE_LEXICON.put("unwatchable", 2.5);
        NEGATIVE_LEXICON.put("disaster", 2.2);
        NEGATIVE_LEXICON.put("garbage", 2.2);
        NEGATIVE_LEXICON.put("trash", 2.0);
        NEGATIVE_LEXICON.put("waste", 2.0);
        NEGATIVE_LEXICON.put("terrible", 1.8);
        NEGATIVE_LEXICON.put("horrible", 1.8);
        NEGATIVE_LEXICON.put("worst", 2.0);
        NEGATIVE_LEXICON.put("awful", 1.7);
        NEGATIVE_LEXICON.put("boring", 1.5);
        NEGATIVE_LEXICON.put("sleepy", 1.2);
        NEGATIVE_LEXICON.put("dull", 1.2);
        NEGATIVE_LEXICON.put("disappointing", 1.4);
        NEGATIVE_LEXICON.put("cliche", 1.0);
        NEGATIVE_LEXICON.put("poor", 1.2);
        NEGATIVE_LEXICON.put("bad", 1.0);
        NEGATIVE_LEXICON.put("slow", 0.7);
        NEGATIVE_LEXICON.put("overhyped", 1.3);
        NEGATIVE_LEXICON.put("regret", 1.3);
    }

    public static class SentimentAnalysisResult {
        private final ReviewSentiment sentiment;
        private final double compositeScore;
        private final List<String> extractedKeywords;
        private final String explanation;

        public SentimentAnalysisResult(ReviewSentiment sentiment, double compositeScore,
                                       List<String> extractedKeywords, String explanation) {
            this.sentiment = sentiment;
            this.compositeScore = compositeScore;
            this.extractedKeywords = extractedKeywords;
            this.explanation = explanation;
        }

        public ReviewSentiment getSentiment() { return sentiment; }
        public double getCompositeScore() { return compositeScore; }
        public List<String> getExtractedKeywords() { return extractedKeywords; }
        public String getExplanation() { return explanation; }
    }

    /**
     * Analyzes review text combined with star rating (1-5).
     */
    public SentimentAnalysisResult analyze(String reviewText, int rating) {
        double textScore = 0.0;
        List<String> matchedTerms = new ArrayList<>();

        if (reviewText != null && !reviewText.trim().isEmpty()) {
            String sanitized = reviewText.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
            String[] tokens = sanitized.split("\\s+");

            boolean negated = false;
            double intensifierMultiplier = 1.0;

            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i].trim();
                if (token.isEmpty()) continue;

                if (NEGATORS.contains(token)) {
                    negated = true;
                    continue;
                }
                if (INTENSIFIERS.contains(token)) {
                    intensifierMultiplier = 1.4;
                    continue;
                }

                if (POSITIVE_LEXICON.containsKey(token)) {
                    double weight = POSITIVE_LEXICON.get(token) * intensifierMultiplier;
                    if (negated) {
                        textScore -= weight * 0.8;
                        matchedTerms.add("not " + token);
                    } else {
                        textScore += weight;
                        matchedTerms.add(token);
                    }
                    negated = false;
                    intensifierMultiplier = 1.0;
                } else if (NEGATIVE_LEXICON.containsKey(token)) {
                    double weight = NEGATIVE_LEXICON.get(token) * intensifierMultiplier;
                    if (negated) {
                        textScore += weight * 0.5; // "not bad" is mildly positive
                        matchedTerms.add("not " + token);
                    } else {
                        textScore -= weight;
                        matchedTerms.add(token);
                    }
                    negated = false;
                    intensifierMultiplier = 1.0;
                }
            }
        }

        // Normalize text score into [-1.0, 1.0]
        double normalizedTextScore = Math.tanh(textScore / 2.5);

        // Rating contribution: 1->-0.8, 2->-0.4, 3->0.0, 4->+0.4, 5->+0.8
        double ratingScore = (rating - 3) * 0.4;

        // Composite score: 60% text content, 40% star rating
        double composite = (normalizedTextScore * 0.6) + (ratingScore * 0.4);
        composite = Math.max(-1.0, Math.min(1.0, composite));

        ReviewSentiment sentiment;
        if (composite >= 0.15) {
            sentiment = ReviewSentiment.POSITIVE;
        } else if (composite <= -0.15) {
            sentiment = ReviewSentiment.NEGATIVE;
        } else {
            sentiment = ReviewSentiment.NEUTRAL;
        }

        String explanation = String.format("Score: %.2f (Text: %.2f, Rating %d★: %.2f)",
                composite, normalizedTextScore, rating, ratingScore);
        if (!matchedTerms.isEmpty()) {
            explanation += " | Key terms: " + String.join(", ", matchedTerms);
        }

        return new SentimentAnalysisResult(sentiment, composite, matchedTerms, explanation);
    }
}
