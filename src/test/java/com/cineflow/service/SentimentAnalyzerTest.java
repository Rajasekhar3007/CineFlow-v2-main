package com.cineflow.service;

import com.cineflow.model.ReviewSentiment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Feature 5 — SentimentAnalyzer Unit Tests")
class SentimentAnalyzerTest {

    private SentimentAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new SentimentAnalyzer();
    }

    @Test
    @DisplayName("Should classify glowing cinema review as POSITIVE")
    void testGlowingReviewPositive() {
        String review = "Absolutely mindblowing masterpiece! Christopher Nolan and Cillian Murphy delivered breathtaking acting and stellar sound design.";
        SentimentAnalyzer.SentimentAnalysisResult result = analyzer.analyze(review, 5);

        assertEquals(ReviewSentiment.POSITIVE, result.getSentiment());
        assertTrue(result.getCompositeScore() > 0.5, "Score should be strongly positive");
        assertTrue(result.getExtractedKeywords().contains("masterpiece"));
        assertTrue(result.getExtractedKeywords().contains("mindblowing"));
        assertTrue(result.getExtractedKeywords().contains("breathtaking"));
    }

    @Test
    @DisplayName("Should classify critical negative review as NEGATIVE")
    void testCriticalReviewNegative() {
        String review = "Utterly boring and dull waste of money. The plot was awful and completely unwatchable.";
        SentimentAnalyzer.SentimentAnalysisResult result = analyzer.analyze(review, 1);

        assertEquals(ReviewSentiment.NEGATIVE, result.getSentiment());
        assertTrue(result.getCompositeScore() < -0.4, "Score should be strongly negative");
        assertTrue(result.getExtractedKeywords().contains("boring"));
        assertTrue(result.getExtractedKeywords().contains("waste"));
        assertTrue(result.getExtractedKeywords().contains("unwatchable"));
    }

    @Test
    @DisplayName("Should handle negation properly ('not good')")
    void testNegationHandling() {
        String review = "The visuals were not good and the story was not entertaining.";
        SentimentAnalyzer.SentimentAnalysisResult result = analyzer.analyze(review, 2);

        assertEquals(ReviewSentiment.NEGATIVE, result.getSentiment());
        assertTrue(result.getExtractedKeywords().contains("not good"));
    }

    @Test
    @DisplayName("Should classify moderate review with neutral 3 stars as NEUTRAL")
    void testNeutralReview() {
        String review = "An okay movie with standard pacing and normal action sequences.";
        SentimentAnalyzer.SentimentAnalysisResult result = analyzer.analyze(review, 3);

        assertEquals(ReviewSentiment.NEUTRAL, result.getSentiment());
    }

    @Test
    @DisplayName("Should handle empty review gracefully based on star rating")
    void testEmptyReviewRatingPrior() {
        SentimentAnalyzer.SentimentAnalysisResult fiveStar = analyzer.analyze("", 5);
        assertEquals(ReviewSentiment.POSITIVE, fiveStar.getSentiment());

        SentimentAnalyzer.SentimentAnalysisResult oneStar = analyzer.analyze("", 1);
        assertEquals(ReviewSentiment.NEGATIVE, oneStar.getSentiment());
    }
}
