package com.cineflow.util;

import javafx.application.Platform;
import javafx.scene.image.Image;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class PosterHelperTest {

    @BeforeAll
    public static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Platform already initialized
        }
    }

    @Test
    public void testPostersDirectoryExists() {
        File dir = PosterHelper.getPostersDirectory();
        assertNotNull(dir, "Posters directory should not be null");
        assertTrue(dir.exists() && dir.isDirectory(), "Posters directory must exist on disk");
    }

    @Test
    public void testSlugGeneration() {
        assertEquals("dune_part_two", PosterHelper.toSlug("Dune: Part Two"));
        assertEquals("inception", PosterHelper.toSlug("Inception"));
        assertEquals("the_dark_knight", PosterHelper.toSlug("The Dark Knight"));
        assertEquals("kantara", PosterHelper.toSlug("Kantara"));
        assertEquals("oppenheimer", PosterHelper.toSlug("Oppenheimer"));
        assertEquals("rrr", PosterHelper.toSlug("RRR"));
        assertEquals("spirited_away", PosterHelper.toSlug("Spirited Away"));
        assertEquals("avatar_fire_and_ash", PosterHelper.toSlug("Avatar: Fire and Ash"));
    }

    @Test
    public void testPosterLoading() {
        Image dunePoster = PosterHelper.getPoster("Dune: Part Two");
        assertNotNull(dunePoster, "Dune poster should be loaded from posters folder");

        Image inceptionPoster = PosterHelper.getPoster("Inception");
        assertNotNull(inceptionPoster, "Inception poster should be loaded");

        Image defaultPoster = PosterHelper.getPoster("Non Existent Movie XYZ 999");
        assertNotNull(defaultPoster, "Unknown movie should fall back to default poster");
    }
}

