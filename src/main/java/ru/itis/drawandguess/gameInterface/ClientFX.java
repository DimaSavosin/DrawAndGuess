package ru.itis.drawandguess.gameInterface;

import javafx.application.Application;
import javafx.application.Platform;
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
    private Canvas canvas;
    private Label wordLabel;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean isDrawer = false; // Флаг, указывающий, может ли клиент рисовать

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Draw & Guess Client");

        chatArea = new TextArea();
        chatArea.setEditable(false);

        messageField = new TextField();
        sendButton = new Button("Send");
        sendButton.setDisable(true);

        nicknameField = new TextField();
        nicknameField.setPromptText("Enter your nickname");
        connectButton = new Button("Connect");

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

        VBox topPanel = new VBox(10, new HBox(10, new Label("Nickname:"), nicknameField, connectButton));

        BorderPane root = new BorderPane();
        root.setTop(topPanel);
        root.setCenter(mainSplitPane);

        connectButton.setOnAction(e -> connectToServer());
        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        primaryStage.setScene(new Scene(root, 900, 600));
        primaryStage.show();
    }

    private void setupDrawing(GraphicsContext gc) {
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (isDrawer) {
                gc.beginPath();
                gc.moveTo(e.getX(), e.getY());
                gc.stroke();
                sendDrawCommand("PRESS " + e.getX() + " " + e.getY());
            }
        });

        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (isDrawer) {
                gc.lineTo(e.getX(), e.getY());
                gc.stroke();
                sendDrawCommand("DRAG " + e.getX() + " " + e.getY());
            }
        });
    }

    private void sendDrawCommand(String command) {
        if (out != null) {
            out.println(command);
        }
    }

    private void connectToServer() {
        String nickname = nicknameField.getText().trim();
        if (nickname.isEmpty()) {
            showAlert("Error", "Please enter a nickname.");
            return;
        }

        try {
            socket = new Socket("127.0.0.1", 12345);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println(nickname);

            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        handleServerMessage(serverMessage);
                    }
                } catch (IOException ex) {
                    chatArea.appendText("Disconnected from server.\n");
                }
            }).start();

            nicknameField.setDisable(true);
            connectButton.setDisable(true);
            sendButton.setDisable(false);
        } catch (IOException ex) {
            showAlert("Error", "Unable to connect to server.");
        }
    }

    private void handleServerMessage(String serverMessage) {
        if (serverMessage.startsWith("DRAW")) {
            handleDrawCommand(serverMessage);
        } else if (serverMessage.startsWith("YOU_ARE_DRAWER")) {
            String[] parts = serverMessage.split(" ", 2);
            if (parts.length > 1) {
                Platform.runLater(() -> {
                    isDrawer = true;
                    wordLabel.setText("Word to draw: " + parts[1]);
                });
            }
        } else if (serverMessage.startsWith("YOU_ARE_GUESSER")) {
            Platform.runLater(() -> {
                isDrawer = false;
                wordLabel.setText("Guess the word!");
            });
        } else {
            Platform.runLater(() -> chatArea.appendText(serverMessage + "\n"));
        }
    }


    private void handleDrawCommand(String command) {
        String[] parts = command.split(" ");
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

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println(message);
            messageField.clear();
        }
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
