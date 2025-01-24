package ru.itis.drawandguess.gameInterface;

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

import java.io.*;
import java.net.Socket;

public class ClientFX extends Application {
    // -------------------- Поля для лобби --------------------
    private TextField createLobbyPasswordField;
    private TextField joinLobbyPasswordField;
    private ComboBox<Integer> createLobbyPlayerCountBox;
    private Button createLobbyButton;
    private Button joinLobbyButton;

    // Окно для ввода ника (после успешного создания/присоединения)
    private TextField nicknameField;
    private Button nicknameOkButton;

    // Сокет и потоки
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // -------------------- Поля для игровой логики (СЦЕНА игры) --------------------
    private TextArea chatArea;
    private TextField messageField;
    private Button sendButton;
    private Button clearButton;
    private Canvas canvas;
    private Label wordLabel;

    private boolean isDrawer = false; // Флаг, указывающий, может ли клиент рисовать

    private Stage primaryStage; // чтобы переключать сцены

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Draw & Guess Client");

        // При старте сразу показываем сцену лобби
        Scene lobbyScene = createLobbyScene();
        primaryStage.setScene(lobbyScene);
        primaryStage.show();
    }

    /**
     * Создаёт сцену лобби, в которой пользователь может:
     * 1) Создать лобби, указав пароль и желаемое количество игроков (2-4).
     * 2) Присоединиться к уже созданному лобби, введя пароль.
     */
    private Scene createLobbyScene() {
        Label createLobbyLabel = new Label("Создать лобби:");
        createLobbyPasswordField = new TextField();
        createLobbyPasswordField.setPromptText("Пароль для лобби");
        createLobbyPlayerCountBox = new ComboBox<>();
        createLobbyPlayerCountBox.getItems().addAll(2, 3, 4);
        createLobbyPlayerCountBox.setValue(2); // по умолчанию 2
        createLobbyButton = new Button("Создать");

        Label joinLobbyLabel = new Label("Присоединиться к лобби:");
        joinLobbyPasswordField = new TextField();
        joinLobbyPasswordField.setPromptText("Пароль для лобби");
        joinLobbyButton = new Button("Присоединиться");

        // Обработчики кнопок
        createLobbyButton.setOnAction(e -> onCreateLobby());
        joinLobbyButton.setOnAction(e -> onJoinLobby());

        // Размещаем в сетке
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        grid.add(createLobbyLabel, 0, 0);
        grid.add(createLobbyPasswordField, 1, 0);
        grid.add(createLobbyPlayerCountBox, 2, 0);
        grid.add(createLobbyButton, 3, 0);

        grid.add(joinLobbyLabel, 0, 1);
        grid.add(joinLobbyPasswordField, 1, 1);
        grid.add(joinLobbyButton, 3, 1);

        return new Scene(grid, 500, 150);
    }

    /**
     * Пытаемся создать лобби:
     * 1) Подключаемся к серверу.
     * 2) Отправляем команду "CREATE LOBBY <password> <maxPlayers>"
     * 3) Читаем ответ.
     */
    private void onCreateLobby() {
        String password = createLobbyPasswordField.getText().trim();
        int maxPlayers = createLobbyPlayerCountBox.getValue();

        if (password.isEmpty()) {
            showAlert("Ошибка", "Введите пароль для лобби.");
            return;
        }

        if (!connectToServer()) {
            return; // Не удалось подключиться
        }

        // Отправляем команду на сервер
        out.println("CREATE LOBBY " + password + " " + maxPlayers);
        try {
            String response = in.readLine();
            if (response == null) {
                showAlert("Ошибка", "Сервер не отвечает при создании лобби.");
                socket.close();
                return;
            }

            if (response.startsWith("OK")) {
                // Лобби успешно создано
                showNicknameDialog();
            } else if (response.startsWith("ERROR")) {
                showAlert("Ошибка при создании лобби", response);
                socket.close();
            }
        } catch (IOException ex) {
            showAlert("Ошибка", "Не удалось прочитать ответ сервера.");
            ex.printStackTrace();
        }
    }

    /**
     * Пытаемся присоединиться к лобби:
     * 1) Подключаемся к серверу.
     * 2) Отправляем команду "JOIN LOBBY <password>"
     * 3) Читаем ответ.
     */
    private void onJoinLobby() {
        String password = joinLobbyPasswordField.getText().trim();
        if (password.isEmpty()) {
            showAlert("Ошибка", "Введите пароль для лобби.");
            return;
        }

        if (!connectToServer()) {
            return; // Не удалось подключиться
        }

        // Отправляем команду на сервер
        out.println("JOIN LOBBY " + password);
        try {
            String response = in.readLine();
            if (response == null) {
                showAlert("Ошибка", "Сервер не отвечает при присоединении к лобби.");
                socket.close();
                return;
            }

            if (response.startsWith("OK")) {
                // Успешное присоединение
                showNicknameDialog();
            } else if (response.startsWith("ERROR")) {
                showAlert("Ошибка при присоединении к лобби", response);
                socket.close();
            }
        } catch (IOException ex) {
            showAlert("Ошибка", "Не удалось прочитать ответ сервера.");
            ex.printStackTrace();
        }
    }

    /**
     * Подключение к серверу по адресу и порту (захардкожен localhost:12345, как и в исходном коде).
     */
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

    /**
     * Показываем диалог (или отдельную сцену) для ввода никнейма,
     * после чего переходим к сцене игры (createGameScene).
     */
    private void showNicknameDialog() {
        // Простая форма для ввода ника
        Label label = new Label("Введите ваш ник:");
        nicknameField = new TextField();
        nicknameField.setPromptText("Nickname");
        nicknameOkButton = new Button("OK");

        VBox vbox = new VBox(10, label, nicknameField, nicknameOkButton);
        vbox.setPadding(new Insets(20));

        Scene nicknameScene = new Scene(vbox, 300, 120);
        primaryStage.setScene(nicknameScene);

        nicknameOkButton.setOnAction(e -> {
            String nickname = nicknameField.getText().trim();
            if (nickname.isEmpty()) {
                showAlert("Ошибка", "Ник не может быть пустым.");
                return;
            }
            // Отправляем ник на сервер
            out.println(nickname);

            // Теперь читаем, нет ли ошибки (например, ник занят)
            new Thread(() -> {
                try {
                    while (true) {
                        String serverMessage = in.readLine();
                        if (serverMessage == null) {
                            // Сервер закрыл соединение
                            Platform.runLater(() -> {
                                showAlert("Ошибка", "Соединение с сервером потеряно.");
                                // Возвращаемся в лобби
                                primaryStage.setScene(createLobbyScene());
                            });
                            break;
                        }
                        // Если сервер отвечает, что ник уже занят:
                        if (serverMessage.startsWith("Nickname already in use")) {
                            Platform.runLater(() -> showAlert("Ошибка", serverMessage));
                            // Предположим, даём возможность заново ввести ник
                            // (или можно завершить тут)
                            break;
                        }
                        // Иначе — мы принимаем это сообщение как часть игровой логики.
                        // Значит, ник принят, и мы можем уже перейти к игровой сцене.
                        // Но лучше проверить, не начинаются ли с YOU_ARE_DRAWER / YOU_ARE_GUESSER и т.д.
                        // Для упрощения — просто переходим в игровой UI, а дальше сообщения обрабатываются там.
                        Platform.runLater(() -> {
                            // Показываем сцену с игрой
                            createGameScene();
                        });
                        // И выходим из этого цикла, чтобы не плодить потоки.
                        break;
                    }
                } catch (IOException ex) {
                    Platform.runLater(() -> {
                        showAlert("Ошибка", "Проблема с чтением данных от сервера.");
                        primaryStage.setScene(createLobbyScene());
                    });
                }
            }).start();
        });
    }

    /**
     * Создаём сцену (и layout) со всей логикой игры (полотно, чат и т.д.).
     * Ваша оригинальная логика не удалена, просто перенесена в отдельный метод.
     */
    private void createGameScene() {
        chatArea = new TextArea();
        chatArea.setEditable(false);

        messageField = new TextField();
        sendButton = new Button("Send");
        sendButton.setDisable(false); // Разблокируем после успешного входа
        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        clearButton = new Button("Clear");
        clearButton.setDisable(true); // По умолчанию запрещено, активируем только для "drawer"
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

        // Верхняя панель с ником убираем, т.к. уже ввели ник раньше.
        // Но оставим кнопку clear
        HBox topControls = new HBox(10, clearButton);
        topControls.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(topControls);
        root.setCenter(mainSplitPane);

        Scene gameScene = new Scene(root, 900, 600);
        primaryStage.setScene(gameScene);
        primaryStage.show();

        // Запускаем отдельный поток для чтения сообщений сервера и обработки
        new Thread(this::listenServerMessages).start();
    }

    /**
     * Настройка событий рисования на холсте.
     */
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

    /**
     * Слушаем сообщения от сервера и обрабатываем их.
     */
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
        if (serverMessage.startsWith("DRAW")) {
            Platform.runLater(() -> handleDrawCommand(serverMessage));
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
        } else {
            // Остальные сообщения (чат, системные, и т.п.)
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

    private void clearCanvas() {
        Platform.runLater(() -> {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        });
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
