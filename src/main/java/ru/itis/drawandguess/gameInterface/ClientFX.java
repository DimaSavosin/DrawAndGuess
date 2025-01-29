package ru.itis.drawandguess.gameInterface;

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;

public class ClientFX extends Application {

    // Поля UI
    private TextField createLobbyPasswordField;
    private TextField joinLobbyPasswordField;
    private ComboBox<Integer> createLobbyPlayerCountBox;
    private Button createLobbyButton;
    private Button joinLobbyButton;

    private TextField nicknameField;
    private Button nicknameOkButton;

    private TextArea chatArea;
    private TextField messageField;
    private Button sendButton;
    private Button clearButton;
    private Canvas canvas;
    private Label wordLabel;
    private Label timerLabel;

    private Timeline roundTimer;
    private int timeLeft;
    private boolean isDrawer = false;
    private boolean gameEnded = false;

    private Stage primaryStage;
    private NetworkClient networkClient; // Сетевой клиент (отдельный класс)

    private ScaleTransition pulseAnimation; // Анимация таймера

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Draw & Guess Client");

        Scene lobbyScene = createLobbyScene();
        lobbyScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        primaryStage.setScene(lobbyScene);
        primaryStage.show();
    }

    /**
     * Создаём сцену для создания/подключения к лобби.
     */
    private Scene createLobbyScene() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        Label createLobbyLabel = new Label("Создать лобби:");
        createLobbyPasswordField = new TextField();
        createLobbyPasswordField.setPromptText("Пароль лобби");

        createLobbyPlayerCountBox = new ComboBox<>();
        createLobbyPlayerCountBox.getItems().addAll(2, 3, 4);
        createLobbyPlayerCountBox.setValue(2);

        createLobbyButton = new Button("Создать");
        createLobbyButton.setMinWidth(140);

        Label joinLobbyLabel = new Label("Присоединиться:");
        joinLobbyPasswordField = new TextField();
        joinLobbyPasswordField.setPromptText("Пароль лобби");
        joinLobbyButton = new Button("Присоединиться");
        joinLobbyButton.setMinWidth(140);

        createLobbyButton.setOnAction(e -> onCreateLobby());
        joinLobbyButton.setOnAction(e -> onJoinLobby());

        grid.add(createLobbyLabel, 0, 0);
        grid.add(createLobbyPasswordField, 1, 0);
        grid.add(createLobbyPlayerCountBox, 2, 0);
        grid.add(createLobbyButton, 3, 0);

        grid.add(joinLobbyLabel, 0, 1);
        grid.add(joinLobbyPasswordField, 1, 1);
        grid.add(joinLobbyButton, 3, 1);

        Scene scene = new Scene(grid, 575, 150);
        scene.getStylesheets().add(
                getClass().getResource("/style.css").toExternalForm()
        );
        return scene;
    }

    private void onCreateLobby() {
        String password = createLobbyPasswordField.getText().trim();
        int maxPlayers = createLobbyPlayerCountBox.getValue();
        if (password.isEmpty()) {
            showAlert("Ошибка", "Введите пароль лобби!");
            return;
        }

        if (!connectToServer()) {
            return;
        }

        networkClient.sendMessage("CREATE LOBBY " + password + " " + maxPlayers);

        try {
            String response = networkClient.readLine();
            if (response == null) {
                showAlert("Ошибка", "Сервер не отвечает при создании лобби.");
                networkClient.close();
                return;
            }

            if (response.startsWith("OK")) {
                showNicknameDialog();
            } else {
                showAlert("Ошибка при создании лобби", response);
                networkClient.close();
                networkClient = null;
            }
        } catch (IOException ex) {
            showAlert("Ошибка", "Не удалось прочитать ответ сервера.");
            ex.printStackTrace();
        }
    }

    private void onJoinLobby() {
        String password = joinLobbyPasswordField.getText().trim();
        if (password.isEmpty()) {
            showAlert("Ошибка", "Введите пароль лобби!");
            return;
        }

        if (!connectToServer()) {
            return;
        }

        networkClient.sendMessage("JOIN LOBBY " + password);

        try {
            String response = networkClient.readLine();
            if (response == null) {
                showAlert("Ошибка", "Сервер не отвечает при присоединении к лобби.");
                networkClient.close();
                networkClient = null;
                return;
            }
            if (response.startsWith("OK")) {
                showNicknameDialog();
            } else {
                showAlert("Ошибка при присоединении", response);
                networkClient.close();
                networkClient = null;
            }
        } catch (IOException ex) {
            showAlert("Ошибка", "Не удалось прочитать ответ сервера.");
            ex.printStackTrace();
        }
    }

    /**
     * Подключаемся к серверу (создаём networkClient), если ещё не подключались.
     */
    private boolean connectToServer() {
        if (networkClient == null) {
            networkClient = new NetworkClient("127.0.0.1", 12345);
            if (!networkClient.connect()) {
                showAlert("Ошибка", "Не удалось подключиться к серверу.");
                networkClient = null;
                return false;
            }
        }
        return true;
    }

    /**
     * Окно для ввода ника.
     */
    private void showNicknameDialog() {
        Label label = new Label("Введите ваш ник:");
        nicknameField = new TextField();
        nicknameOkButton = new Button("OK");

        VBox vbox = new VBox(10, label, nicknameField, nicknameOkButton);
        vbox.setPadding(new Insets(20));
        Scene nicknameScene = new Scene(vbox, 300, 120);
        nicknameScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        primaryStage.setScene(nicknameScene);

        nicknameOkButton.setOnAction(e -> {
            String nickname = nicknameField.getText().trim();
            if (nickname.isEmpty()) {
                showAlert("Ошибка", "Ник не может быть пустым!");
                return;
            }
            networkClient.sendMessage(nickname);

            // ВАЖНО: Сразу идём в GameScene,
            // дальнейшие сообщения (включая Nickname already in use)
            // обрабатываем в startServerListener() -> handleServerMessage()
            createGameScene();
        });
    }

    /**
     * Переход в основную игровую сцену.
     */
    private void createGameScene() {
        gameEnded = false;

        chatArea = new TextArea();
        chatArea.setEditable(false);

        messageField = new TextField();
        sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        clearButton = new Button("Clear");
        clearButton.setDisable(true);

        canvas = new Canvas(600, 400);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        setupDrawing(gc);

        wordLabel = new Label("Waiting for the game to start...");
        wordLabel.getStyleClass().add("word-label");

        timerLabel = new Label("Time left: --");
        timerLabel.getStyleClass().add("timer-label");

        VBox chatBox = new VBox(
                10,
                chatArea,
                new HBox(10, messageField, sendButton)
        );
        chatBox.setPrefWidth(300);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        StackPane canvasPane = new StackPane(canvas);

        VBox canvasBox = new VBox(5, wordLabel, timerLabel, canvasPane, clearButton);
        canvasBox.getStyleClass().add("canvas-box");
        VBox.setVgrow(canvasPane, Priority.ALWAYS);

        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.getItems().addAll(chatBox, canvasBox);
        mainSplitPane.setDividerPositions(0.3);

        BorderPane root = new BorderPane();
        root.setCenter(mainSplitPane);

        Scene gameScene = new Scene(root, 900, 600);
        gameScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        primaryStage.setScene(gameScene);
        primaryStage.show();

        // Включаем фон. поток, который читает всё, что пришлёт сервер.
        startServerListener();
    }

    /**
     * Поток для чтения входящих сообщений.
     */
    private void startServerListener() {
        new Thread(() -> {
            try {
                String serverMessage;
                while ((serverMessage = networkClient.readLine()) != null) {
                    final String msg = serverMessage;
                    Platform.runLater(() -> handleServerMessage(msg));
                }
            } catch (IOException e) {
                Platform.runLater(() -> chatArea.appendText("Disconnected from server.\n"));
            }
        }).start();
    }

    /**
     * Обработка сообщений в главном (UI) потоке.
     */
    private void handleServerMessage(String serverMessage) {
        if (serverMessage.equals("GAME_ENDED")) {
            gameEnded = true;
            isDrawer = false;
            clearButton.setDisable(true);
            wordLabel.setText("Game has ended!");

            showAlert("Information",
                    "Game ended! Вы будете возвращены в главное меню через 5 секунд...");

            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.seconds(5), ev -> {
                        networkClient.close();
                        networkClient = null;
                        primaryStage.setScene(createLobbyScene());
                    })
            );
            timeline.setCycleCount(1);
            timeline.play();
        }
        else if (serverMessage.startsWith("Nickname already in use")) {
            // Если кто-то пытается использовать уже занятый ник
            showAlert("Ошибка", serverMessage);
            networkClient.close();
            networkClient = null;
            primaryStage.setScene(createLobbyScene());
        }
        else if (serverMessage.startsWith("DRAW")) {
            handleDrawCommand(serverMessage);
        }
        else if (serverMessage.startsWith("YOU_ARE_DRAWER")) {
            String[] parts = serverMessage.split(" ", 2);
            if (parts.length > 1) {
                isDrawer = true;
                if (!gameEnded) {
                    clearButton.setDisable(false);
                }
                wordLabel.setText("Word to draw: " + parts[1]);
            }
        }
        else if (serverMessage.startsWith("YOU_ARE_GUESSER")) {
            isDrawer = false;
            clearButton.setDisable(true);
            wordLabel.setText("Guess the word!");
        }
        else if (serverMessage.equals("CLEAR_CANVAS")) {
            clearCanvas();
        }
        else if (serverMessage.startsWith("You have 60 seconds")) {
            startCountdown(60);
        }
        else if (serverMessage.startsWith("Time is up!")) {
            stopCurrentTimer();
            timerLabel.setText("Time is up!");
            chatArea.appendText(serverMessage + "\n");
        }
        else if (serverMessage.contains("guessed the word!")) {
            stopCurrentTimer();
            chatArea.appendText(serverMessage + "\n");
            bounceChatArea();
        }
        else {
            chatArea.appendText(serverMessage + "\n");
        }
    }

    private void setupDrawing(GraphicsContext gc) {
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (isDrawer && !gameEnded) {
                gc.beginPath();
                gc.moveTo(e.getX(), e.getY());
                gc.stroke();
                networkClient.sendMessage("PRESS " + e.getX() + " " + e.getY());
            }
        });
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (isDrawer && !gameEnded) {
                gc.lineTo(e.getX(), e.getY());
                gc.stroke();
                networkClient.sendMessage("DRAG " + e.getX() + " " + e.getY());
            }
        });

        clearButton.setOnAction(e -> {
            if (!gameEnded) {
                networkClient.sendMessage("CLEAR_REQUEST");
            }
        });
    }

    private void handleDrawCommand(String command) {
        String[] parts = command.split(" ");
        if (parts.length < 3) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        switch (parts[1]) {
            case "PRESS" -> {
                gc.beginPath();
                gc.moveTo(Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                gc.stroke();
            }
            case "DRAG" -> {
                gc.lineTo(Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                gc.stroke();
            }
        }
    }

    private void startCountdown(int seconds) {
        stopCurrentTimer();
        timeLeft = seconds;
        timerLabel.setText("Time left: " + timeLeft);

        roundTimer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            timeLeft--;
            if (timeLeft <= 0) {
                roundTimer.stop();
                timerLabel.setText("Time is up!");
                stopPulseTimerLabel();
            } else {
                timerLabel.setText("Time left: " + timeLeft);
                if (timeLeft == 10) {
                    startPulseTimerLabel();
                }
            }
        }));
        roundTimer.setCycleCount(seconds);
        roundTimer.play();
    }

    private void stopCurrentTimer() {
        if (roundTimer != null) {
            roundTimer.stop();
            roundTimer = null;
        }
        stopPulseTimerLabel();
    }

    private void startPulseTimerLabel() {
        if (pulseAnimation != null) {
            return;
        }
        pulseAnimation = new ScaleTransition(Duration.millis(500), timerLabel);
        pulseAnimation.setFromX(1.0);
        pulseAnimation.setToX(1.2);
        pulseAnimation.setFromY(1.0);
        pulseAnimation.setToY(1.2);
        pulseAnimation.setAutoReverse(true);
        pulseAnimation.setCycleCount(Animation.INDEFINITE);
        pulseAnimation.play();
    }

    private void stopPulseTimerLabel() {
        if (pulseAnimation != null) {
            pulseAnimation.stop();
            timerLabel.setScaleX(1.0);
            timerLabel.setScaleY(1.0);
            pulseAnimation = null;
        }
    }

    private void bounceChatArea() {
        ScaleTransition scale = new ScaleTransition(Duration.millis(400), chatArea);
        scale.setFromX(1.0);
        scale.setToX(1.2);
        scale.setFromY(1.0);
        scale.setToY(1.2);
        scale.setCycleCount(2);
        scale.setAutoReverse(true);
        scale.play();
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            networkClient.sendMessage(message);
            messageField.clear();
        }
    }

    private void clearCanvas() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    @Override
    public void stop() {
        if (networkClient != null) {
            networkClient.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
