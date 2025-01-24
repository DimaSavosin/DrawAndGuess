package ru.itis.drawandguess.gameInterface;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;

public class ClientFX extends Application {
    private TextArea chatArea;
    private TextField messageField;
    private TextField nicknameField;
    private Button sendButton;
    private Button connectButton;
    private Button clearButton;
    private Canvas canvas;
    private Label wordLabel;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean isDrawer = false;
    private boolean isNicknameSent = false;

    @Override
    public void start(Stage primaryStage) {
        showLobbyWindow(primaryStage);
    }

    private void showLobbyWindow(Stage primaryStage) {
        VBox lobbyLayout = new VBox(10);
        lobbyLayout.setAlignment(Pos.CENTER);

        Label title = new Label("Draw & Guess Lobby");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        nicknameField = new TextField();
        nicknameField.setPromptText("Enter your nickname");

        TextField joinLobbyField = new TextField();
        joinLobbyField.setPromptText("Enter lobby password");

        Button joinLobbyButton = new Button("Join Lobby");
        joinLobbyButton.setOnAction(e -> {
            String nickname = nicknameField.getText().trim();
            String password = joinLobbyField.getText().trim();
            if (!nickname.isEmpty() && !password.isEmpty()) {
                ensureConnected();
                sendNicknameIfNeeded(nickname);
                sendToServer("JOIN_LOBBY " + password);
            } else {
                showAlert("Error", "Please enter your nickname and lobby password.");
            }
        });

        TextField createLobbyField = new TextField();
        createLobbyField.setPromptText("Set lobby password");

        Spinner<Integer> maxPlayersSpinner = new Spinner<>(2, 10, 2);

        Button createLobbyButton = new Button("Create Lobby");
        createLobbyButton.setOnAction(e -> {
            String nickname = nicknameField.getText().trim();
            String password = createLobbyField.getText().trim();
            int maxPlayers = maxPlayersSpinner.getValue();
            if (!nickname.isEmpty() && !password.isEmpty()) {
                ensureConnected();
                sendNicknameIfNeeded(nickname);
                sendToServer("CREATE_LOBBY " + password + " " + maxPlayers);
            } else {
                showAlert("Error", "Please enter your nickname and set a lobby password.");
            }
        });

        lobbyLayout.getChildren().addAll(
                title,
                new Label("Your nickname:"),
                nicknameField,
                new Label("Join an existing lobby:"),
                joinLobbyField,
                joinLobbyButton,
                new Label("Or create a new lobby:"),
                createLobbyField,
                new Label("Max players:"),
                maxPlayersSpinner,
                createLobbyButton
        );

        Scene lobbyScene = new Scene(lobbyLayout, 400, 500);
        primaryStage.setScene(lobbyScene);
        primaryStage.show();
    }

    private void startGameUI(Stage primaryStage) {
        System.out.println("Switching to game UI..."); // Debug log
        primaryStage.setTitle("Draw & Guess Game");

        chatArea = new TextArea();
        chatArea.setEditable(false);

        messageField = new TextField();
        sendButton = new Button("Send");
        sendButton.setDisable(false);

        sendButton.setOnAction(e -> {
            String text = messageField.getText().trim();
            if (!text.isEmpty()) {
                sendToServer("CHAT " + text);
                messageField.clear();
            }
        });

        clearButton = new Button("Clear");
        clearButton.setDisable(true);
        clearButton.setOnAction(e -> {
            if (out != null) {
                out.println("CLEAR_REQUEST");
            }
        });

        canvas = new Canvas(600, 400);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        setupDrawing(gc);

        wordLabel = new Label("Waiting for the game to start...");
        wordLabel.setStyle("-fx-background-color: lightgray; -fx-padding: 5px; -fx-font-size: 14px; -fx-alignment: center;");
        wordLabel.setMaxWidth(Double.MAX_VALUE);

        VBox chatBox = new VBox(10, chatArea, new HBox(10, messageField, sendButton));
        chatBox.setPrefWidth(300);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        StackPane canvasPane = new StackPane(canvas);
        VBox canvasBox = new VBox(wordLabel, canvasPane);
        VBox.setVgrow(canvasPane, Priority.ALWAYS);

        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.getItems().addAll(chatBox, canvasBox);
        mainSplitPane.setDividerPositions(0.3);

        HBox topControls = new HBox(10, clearButton);
        VBox topPanel = new VBox(10, topControls);

        BorderPane root = new BorderPane();
        root.setTop(topPanel);
        root.setCenter(mainSplitPane);

        Scene gameScene = new Scene(root, 900, 600);
        primaryStage.setScene(gameScene);
        primaryStage.show();
        System.out.println("Game UI loaded successfully."); // Debug log
    }

    private void setupDrawing(GraphicsContext gc) {
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (isDrawer) {
                gc.beginPath();
                gc.moveTo(e.getX(), e.getY());
                gc.stroke();
                sendDrawCommand("DRAW_PRESS " + e.getX() + " " + e.getY());
            }
        });

        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (isDrawer) {
                gc.lineTo(e.getX(), e.getY());
                gc.stroke();
                sendDrawCommand("DRAW_DRAG " + e.getX() + " " + e.getY());
            }
        });
    }

    private void sendDrawCommand(String command) {
        if (out != null) {
            out.println(command);
        }
    }

    private void sendToServer(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void sendNicknameIfNeeded(String nickname) {
        if (!isNicknameSent) {
            sendToServer(nickname);
            isNicknameSent = true;
        }
    }

    private void ensureConnected() {
        if (socket == null || socket.isClosed()) {
            connectToServer();
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket("127.0.0.1", 12345);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        final String message = serverMessage;
                        Platform.runLater(() -> handleServerMessage(message));
                    }
                } catch (IOException ex) {
                    if (chatArea != null) {
                        Platform.runLater(() -> chatArea.appendText("Disconnected from server.\n"));
                    }
                }
            }).start();
        } catch (IOException ex) {
            showAlert("Error", "Unable to connect to server.");
        }
    }

    private void handleServerMessage(String serverMessage) {
        if (serverMessage.startsWith("DRAW_PRESS") || serverMessage.startsWith("DRAW_DRAG")) {
            handleDrawCommand(serverMessage);
        } else if (serverMessage.startsWith("YOU_ARE_DRAWER")) {
            String[] parts = serverMessage.split(" ", 2);
            if (parts.length > 1) {
                Platform.runLater(() -> {
                    isDrawer = true;
                    clearButton.setDisable(false);
                    wordLabel.setText("Word to draw: " + parts[1]);
                });
            }
        } else if (serverMessage.startsWith("YOU_ARE_GUESSER")) {
            Platform.runLater(() -> {
                isDrawer = false;
                clearButton.setDisable(true);
                wordLabel.setText("Guess the word!");
            });
        } else if (serverMessage.equals("CLEAR_CANVAS")) {
            clearCanvas();
        } else if (serverMessage.startsWith("CHAT ")) {
            String chatMessage = serverMessage.substring(5);
            Platform.runLater(() -> chatArea.appendText(chatMessage + "\n"));
        } else if (serverMessage.startsWith("LOBBY_CREATED")) {
            Platform.runLater(() -> {
                Stage primaryStage = (Stage) nicknameField.getScene().getWindow();
                startGameUI(primaryStage);
            });
        } else if (serverMessage.equals("LOBBY_JOINED")) {
            Platform.runLater(() -> {
                Stage primaryStage = (Stage) nicknameField.getScene().getWindow();
                startGameUI(primaryStage);
            });
        } else {
            Platform.runLater(() -> chatArea.appendText(serverMessage + "\n"));
        }
    }

    private void handleDrawCommand(String command) {
        String[] parts = command.split(" ");
        GraphicsContext gc = canvas.getGraphicsContext2D();
        if (parts[0].equals("DRAW_PRESS")) {
            gc.beginPath();
            gc.moveTo(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            gc.stroke();
        } else if (parts[0].equals("DRAW_DRAG")) {
            gc.lineTo(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            gc.stroke();
        }
    }

    private void clearCanvas() {
        Platform.runLater(() -> {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
