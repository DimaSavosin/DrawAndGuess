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

import java.io.*;
import java.net.Socket;

public class ClientFX extends Application {

    private TextField createLobbyPasswordField;
    private TextField joinLobbyPasswordField;
    private ComboBox<Integer> createLobbyPlayerCountBox;
    private Button createLobbyButton;
    private Button joinLobbyButton;

    private TextField nicknameField;
    private Button nicknameOkButton;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

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

    // Храним ссылку на анимацию пульсации, чтобы её можно было остановить
    private ScaleTransition pulseAnimation;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Draw & Guess Client");

        Scene lobbyScene = createLobbyScene();
        lobbyScene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        primaryStage.setScene(lobbyScene);
        primaryStage.show();
    }

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
        createLobbyPlayerCountBox.getStyleClass().add("combo-box");

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
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
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

        out.println("CREATE LOBBY " + password + " " + maxPlayers);
        try {
            String response = in.readLine();
            if (response == null) {
                showAlert("Ошибка", "Сервер не отвечает при создании лобби.");
                socket.close();
                return;
            }

            if (response.startsWith("OK")) {
                showNicknameDialog();
            } else {
                showAlert("Ошибка при создании лобби", response);
                socket.close();
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

        out.println("JOIN LOBBY " + password);
        try {
            String response = in.readLine();
            if (response == null) {
                showAlert("Ошибка", "Сервер не отвечает при присоединении к лобби.");
                socket.close();
                return;
            }
            if (response.startsWith("OK")) {
                showNicknameDialog();
            } else {
                showAlert("Ошибка при присоединении", response);
                socket.close();
            }
        } catch (IOException ex) {
            showAlert("Ошибка", "Не удалось прочитать ответ сервера.");
            ex.printStackTrace();
        }
    }

    private boolean connectToServer() {
        try {
            socket = new Socket("127.0.0.1", 12345);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            return true;
        } catch (IOException ex) {
            showAlert("Ошибка", "Невозможно подключиться к серверу.");
            return false;
        }
    }

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
            out.println(nickname);

            new Thread(() -> {
                try {
                    while (true) {
                        String serverMessage = in.readLine();
                        if (serverMessage == null) {
                            // сервер закрыл соединение
                            Platform.runLater(() -> {
                                showAlert("Ошибка", "Соединение с сервером потеряно.");
                                primaryStage.setScene(createLobbyScene());
                            });
                            break;
                        }
                        if (serverMessage.startsWith("Nickname already in use")) {
                            Platform.runLater(() -> {
                                showAlert("Ошибка", serverMessage);
                            });
                            break;
                        }
                        Platform.runLater(() -> createGameScene());
                        break;
                    }
                } catch (IOException ex) {
                    Platform.runLater(() -> {
                        showAlert("Ошибка", "Ошибка при чтении данных от сервера.");
                        primaryStage.setScene(createLobbyScene());
                    });
                }
            }).start();
        });
    }

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

        new Thread(this::listenServerMessages).start();
    }

    private void listenServerMessages() {
        try {
            String serverMessage;
            while ((serverMessage = in.readLine()) != null) {
                handleServerMessage(serverMessage);
            }
        } catch (IOException e) {
            Platform.runLater(() -> chatArea.appendText("Disconnected from server.\n"));
        }
    }

    private void handleServerMessage(String serverMessage) {
        if (serverMessage.equals("GAME_ENDED")) {
            gameEnded = true;
            Platform.runLater(() -> {
                isDrawer = false;
                clearButton.setDisable(true);
                wordLabel.setText("Game has ended!");

                showAlert("Information",
                        "Game ended! You will be returned to the main menu in 5 seconds...");

                Timeline timeline = new Timeline(
                        new KeyFrame(Duration.seconds(5), ev -> {
                            try {
                                if (socket != null && !socket.isClosed()) {
                                    socket.close();
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                            primaryStage.setScene(createLobbyScene());
                        })
                );
                timeline.setCycleCount(1);
                timeline.play();
            });
        }
        else if (serverMessage.startsWith("DRAW")) {
            Platform.runLater(() -> handleDrawCommand(serverMessage));
        }
        else if (serverMessage.startsWith("YOU_ARE_DRAWER")) {
            String[] parts = serverMessage.split(" ", 2);
            if (parts.length > 1) {
                Platform.runLater(() -> {
                    isDrawer = true;
                    if (!gameEnded) {
                        clearButton.setDisable(false);
                    }
                    wordLabel.setText("Word to draw: " + parts[1]);
                });
            }
        }
        else if (serverMessage.startsWith("YOU_ARE_GUESSER")) {
            Platform.runLater(() -> {
                isDrawer = false;
                clearButton.setDisable(true);
                wordLabel.setText("Guess the word!");
            });
        }
        else if (serverMessage.equals("CLEAR_CANVAS")) {
            clearCanvas();
        }
        else if (serverMessage.startsWith("You have 60 seconds to guess the word!")) {
            Platform.runLater(() -> startCountdown(60));
        }
        else if (serverMessage.startsWith("Time is up!")) {
            stopCurrentTimer();
            Platform.runLater(() -> timerLabel.setText("Time is up!"));
            Platform.runLater(() -> chatArea.appendText(serverMessage + "\n"));
        }
        // ------------------------------
        // Реализуем всплывающий эффект:
        // Когда кто-то угадал слово: "guessed the word!"
        else if (serverMessage.contains("guessed the word!")) {
            stopCurrentTimer();
            Platform.runLater(() -> {
                chatArea.appendText(serverMessage + "\n");
                bounceChatArea();  // <-- вызываем метод анимации
            });
        }
        // ------------------------------
        else {
            Platform.runLater(() -> chatArea.appendText(serverMessage + "\n"));
        }
    }

    private void setupDrawing(GraphicsContext gc) {
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (isDrawer && !gameEnded) {
                gc.beginPath();
                gc.moveTo(e.getX(), e.getY());
                gc.stroke();
                sendDrawCommand("PRESS " + e.getX() + " " + e.getY());
            }
        });
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (isDrawer && !gameEnded) {
                gc.lineTo(e.getX(), e.getY());
                gc.stroke();
                sendDrawCommand("DRAG " + e.getX() + " " + e.getY());
            }
        });

        clearButton.setOnAction(e -> {
            if (!gameEnded && out != null) {
                out.println("CLEAR_REQUEST");
            }
        });
    }

    private void sendDrawCommand(String command) {
        if (out != null) {
            out.println(command);
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println(message);
            messageField.clear();
        }
    }

    private void clearCanvas() {
        Platform.runLater(() -> {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        });
    }

    /**
     * Запускаем бесконечную пульсацию с увеличением,
     * пока не остановим явно.
     */
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

    private void handleDrawCommand(String command) {
        String[] parts = command.split(" ");
        if (parts.length < 3) {
            return; // Неверный формат команды
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();

        switch (parts[1]) {
            case "PRESS":
                gc.beginPath();
                gc.moveTo(Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                gc.stroke();
                break;
            case "DRAG":
                gc.lineTo(Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                gc.stroke();
                break;
        }
    }

    /**
     * "Всплывающий" эффект (прыжок) для области чата,
     * когда кто-то угадал слово.
     */
    private void bounceChatArea() {
        // Простой ScaleTransition: увеличиваем в 1.2 раза, возвращаем назад
        ScaleTransition scale = new ScaleTransition(Duration.millis(400), chatArea);
        scale.setFromX(1.0);
        scale.setToX(1.2);
        scale.setFromY(1.0);
        scale.setToY(1.2);
        scale.setCycleCount(2);
        scale.setAutoReverse(true);
        scale.play();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
