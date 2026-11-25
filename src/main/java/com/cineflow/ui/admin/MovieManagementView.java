package com.cineflow.ui.admin;

import com.cineflow.model.Movie;
import com.cineflow.service.MovieService;
import com.cineflow.service.ValidationException;
import com.cineflow.util.AlertHelper;
import com.cineflow.util.PosterHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Admin view for Movie Catalog CRUD operations.
 */
public class MovieManagementView {

    private final MovieService movieService;
    private TableView<Movie> tableView;
    private ObservableList<Movie> movieData;
    private TextField searchField;
    private ComboBox<String> genreFilter;

    public MovieManagementView() {
        this.movieService = new MovieService();
    }

    public Parent getView() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(20));

        // Header and Toolbar
        HBox topBar = createTopBar();
        root.getChildren().add(topBar);

        // TableView
        tableView = createTableView();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        root.getChildren().add(tableView);

        // Load initial data
        refreshTable();

        return root;
    }

    private HBox createTopBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Movie Catalog Management");
        title.getStyleClass().add("title-medium");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("Search by title...");
        searchField.setPrefWidth(200);

        // Property listener: Search-as-you-type filter
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterMovies());

        genreFilter = new ComboBox<>(FXCollections.observableArrayList(
                "ALL", "Action", "Sci-Fi", "Drama", "Comedy", "Thriller", "Animation"
        ));
        genreFilter.setValue("ALL");

        // Selection listener: Genre ComboBox filter
        genreFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> filterMovies());

        Button addBtn = new Button("+ Add Movie");
        addBtn.getStyleClass().add("button-primary");

        // ActionEvent: Opens dialog to add a new movie
        addBtn.setOnAction(e -> openMovieDialog(null));

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("button-secondary");

        // ActionEvent: Edits the selected movie
        editBtn.setOnAction(e -> {
            Movie selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                openMovieDialog(selected);
            } else {
                AlertHelper.showWarning("Selection Required", "No Movie Selected", "Please select a movie to edit.");
            }
        });

        Button postersBtn = new Button("📂 Posters Folder");
        postersBtn.getStyleClass().add("button-secondary");
        postersBtn.setOnAction(e -> openPostersFolder());

        Button toggleBtn = new Button("Toggle Active");
        toggleBtn.getStyleClass().add("button-secondary");

        // ActionEvent: Toggles movie activation status
        toggleBtn.setOnAction(e -> toggleSelectedMovieActive());

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("button-danger");

        // ActionEvent: Deletes selected movie with confirmation
        deleteBtn.setOnAction(e -> deleteSelectedMovie());

        bar.getChildren().addAll(title, spacer, searchField, genreFilter, addBtn, editBtn, postersBtn, toggleBtn, deleteBtn);
        return bar;
    }

    private void openPostersFolder() {
        try {
            File dir = PosterHelper.getPostersDirectory();
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir);
            } else {
                new ProcessBuilder("explorer.exe", dir.getAbsolutePath()).start();
            }
        } catch (Exception ex) {
            AlertHelper.showError("Folder Error", "Could not open posters folder", ex.getMessage());
        }
    }

    private TableView<Movie> createTableView() {
        TableView<Movie> table = new TableView<>();
        movieData = FXCollections.observableArrayList();
        table.setItems(movieData);

        TableColumn<Movie, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(50);

        TableColumn<Movie, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(180);

        TableColumn<Movie, String> genreCol = new TableColumn<>("Genre");
        genreCol.setCellValueFactory(new PropertyValueFactory<>("genre"));
        genreCol.setPrefWidth(100);

        TableColumn<Movie, String> langCol = new TableColumn<>("Language");
        langCol.setCellValueFactory(new PropertyValueFactory<>("language"));
        langCol.setPrefWidth(90);

        TableColumn<Movie, Integer> durCol = new TableColumn<>("Duration");
        durCol.setCellValueFactory(new PropertyValueFactory<>("durationMinutes"));
        durCol.setPrefWidth(80);

        TableColumn<Movie, Double> ratingCol = new TableColumn<>("Rating");
        ratingCol.setCellValueFactory(new PropertyValueFactory<>("rating"));
        ratingCol.setPrefWidth(70);

        TableColumn<Movie, String> dateCol = new TableColumn<>("Release Date");
        dateCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getReleaseDate() != null ? cellData.getValue().getReleaseDate().toString() : ""));
        dateCol.setPrefWidth(100);

        TableColumn<Movie, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().isActive() ? "ACTIVE" : "INACTIVE"));
        statusCol.setPrefWidth(90);

        table.getColumns().addAll(idCol, titleCol, genreCol, langCol, durCol, ratingCol, dateCol, statusCol);

        // MouseEvent: Double-click a movie TableView row to open edit dialog
        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                Movie selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openMovieDialog(selected);
                }
            }
        });

        // KeyEvent: Delete removes the selected admin table row after confirmation
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                deleteSelectedMovie();
            }
        });

        return table;
    }

    private void filterMovies() {
        String query = searchField.getText();
        String genre = genreFilter.getValue();
        try {
            List<Movie> results = movieService.searchMovies(query, genre, "ALL");
            movieData.setAll(results);
        } catch (SQLException e) {
            AlertHelper.showError("Database Error", "Search Failed", e.getMessage());
        }
    }

    public void refreshTable() {
        try {
            List<Movie> list = movieService.getAllMovies();
            movieData.setAll(list);
        } catch (SQLException e) {
            AlertHelper.showError("Database Error", "Failed to Load Movies", e.getMessage());
        }
    }

    private void toggleSelectedMovieActive() {
        Movie selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showWarning("Selection Required", "No Movie Selected", "Please select a movie to toggle.");
            return;
        }

        try {
            movieService.toggleActive(selected.getId(), !selected.isActive());
            refreshTable();
        } catch (SQLException e) {
            AlertHelper.showError("Database Error", "Failed to update movie status", e.getMessage());
        }
    }

    private void deleteSelectedMovie() {
        Movie selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showWarning("Selection Required", "No Movie Selected", "Please select a movie to delete.");
            return;
        }

        boolean confirm = AlertHelper.showConfirmation(
                "Confirm Deletion",
                "Delete Movie?",
                "Are you sure you want to delete '" + selected.getTitle() + "'?\nIf bookings exist, it will be safely deactivated."
        );

        if (confirm) {
            try {
                movieService.deleteMovie(selected.getId());
                refreshTable();
            } catch (SQLException e) {
                AlertHelper.showError("Database Error", "Could not delete movie", e.getMessage());
            }
        }
    }

    private void openMovieDialog(Movie existingMovie) {
        Dialog<Movie> dialog = new Dialog<>();
        dialog.setTitle(existingMovie == null ? "Add New Movie" : "Edit Movie: " + existingMovie.getTitle());
        dialog.setHeaderText(existingMovie == null ? "Enter Movie Details" : "Update Movie Details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        TextField titleField = new TextField(existingMovie != null ? existingMovie.getTitle() : "");
        TextField genreField = new TextField(existingMovie != null ? existingMovie.getGenre() : "");
        TextField langField = new TextField(existingMovie != null ? existingMovie.getLanguage() : "");
        TextField durField = new TextField(existingMovie != null ? String.valueOf(existingMovie.getDurationMinutes()) : "120");
        TextField ratingField = new TextField(existingMovie != null ? String.valueOf(existingMovie.getRating()) : "8.0");
        TextArea descArea = new TextArea(existingMovie != null ? existingMovie.getDescription() : "");
        descArea.setPrefRowCount(3);
        DatePicker releasePicker = new DatePicker(existingMovie != null ? existingMovie.getReleaseDate() : LocalDate.now());
        CheckBox activeCheck = new CheckBox("Active in Catalog");
        activeCheck.setSelected(existingMovie == null || existingMovie.isActive());

        // Poster Preview & Selection
        ImageView posterPreview = new ImageView();
        posterPreview.setFitWidth(70);
        posterPreview.setFitHeight(100);
        posterPreview.setPreserveRatio(false);
        posterPreview.setSmooth(true);
        Rectangle previewClip = new Rectangle(70, 100);
        previewClip.setArcWidth(12);
        previewClip.setArcHeight(12);
        posterPreview.setClip(previewClip);

        if (existingMovie != null) {
            posterPreview.setImage(PosterHelper.getPoster(existingMovie.getTitle()));
        } else {
            posterPreview.setImage(PosterHelper.getPoster("default_poster"));
        }

        final File[] selectedPosterFile = new File[1];
        Label posterStatusLabel = new Label(existingMovie != null ? "Current poster in posters/" : "No custom poster chosen (will match title in posters/)");
        posterStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
        posterStatusLabel.setMaxWidth(260);
        posterStatusLabel.setWrapText(true);

        Button choosePosterBtn = new Button("📁 Choose Poster...");
        choosePosterBtn.getStyleClass().add("button-secondary");
        choosePosterBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");

        Button openFolderBtn = new Button("📂 Open Posters Folder");
        openFolderBtn.getStyleClass().add("button-secondary");
        openFolderBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
        openFolderBtn.setOnAction(ev -> openPostersFolder());

        choosePosterBtn.setOnAction(ev -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Movie Poster Image");
            chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files (*.jpg, *.png, *.jpeg, *.webp)", "*.jpg", "*.png", "*.jpeg", "*.webp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File postersDir = PosterHelper.getPostersDirectory();
            if (postersDir.exists()) {
                chooser.setInitialDirectory(postersDir);
            }
            File file = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file != null) {
                selectedPosterFile[0] = file;
                posterStatusLabel.setText("Selected: " + file.getName());
                try {
                    posterPreview.setImage(new Image(file.toURI().toString()));
                } catch (Exception err) {
                    System.err.println("Failed to preview poster: " + err.getMessage());
                }
            }
        });

        HBox posterButtons = new HBox(8, choosePosterBtn, openFolderBtn);
        posterButtons.setAlignment(Pos.CENTER_LEFT);
        VBox posterDetails = new VBox(4, posterButtons, posterStatusLabel);
        HBox posterRow = new HBox(12, posterPreview, posterDetails);
        posterRow.setAlignment(Pos.CENTER_LEFT);

        grid.add(new Label("Title:"), 0, 0); grid.add(titleField, 1, 0);
        grid.add(new Label("Genre:"), 0, 1); grid.add(genreField, 1, 1);
        grid.add(new Label("Language:"), 0, 2); grid.add(langField, 1, 2);
        grid.add(new Label("Duration (mins):"), 0, 3); grid.add(durField, 1, 3);
        grid.add(new Label("Rating (0.0-10.0):"), 0, 4); grid.add(ratingField, 1, 4);
        grid.add(new Label("Release Date:"), 0, 5); grid.add(releasePicker, 1, 5);
        grid.add(new Label("Description:"), 0, 6); grid.add(descArea, 1, 6);
        grid.add(new Label("Poster Image:"), 0, 7); grid.add(posterRow, 1, 7);
        grid.add(activeCheck, 1, 8);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    int duration = Integer.parseInt(durField.getText().trim());
                    double rating = Double.parseDouble(ratingField.getText().trim());

                    Movie m = existingMovie != null ? existingMovie : new Movie();
                    m.setTitle(titleField.getText());
                    m.setGenre(genreField.getText());
                    m.setLanguage(langField.getText());
                    m.setDurationMinutes(duration);
                    m.setRating(rating);
                    m.setDescription(descArea.getText());
                    m.setReleaseDate(releasePicker.getValue());
                    m.setActive(activeCheck.isSelected());
                    return m;
                } catch (NumberFormatException e) {
                    AlertHelper.showError("Invalid Number", "Input Error", "Duration must be an integer and Rating must be a number.");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(movie -> {
            try {
                movieService.saveMovie(movie);
                if (selectedPosterFile[0] != null) {
                    PosterHelper.savePoster(movie.getTitle(), selectedPosterFile[0]);
                }
                PosterHelper.clearCache();
                refreshTable();
            } catch (ValidationException | SQLException e) {
                AlertHelper.showError("Save Failed", "Could not save movie", e.getMessage());
            }
        });
    }
}
