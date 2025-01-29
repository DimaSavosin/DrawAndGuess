package ru.itis.drawandguess.gameInterface;

import java.io.*;
import java.net.Socket;

public class NetworkClient {
    private final String host;
    private final int port;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public NetworkClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Подключаемся к серверу.
     * Возвращает true, если успешно.
     */
    public boolean connect() {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("Connected to the server: " + host + ":" + port);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Отправить строку на сервер
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * Считать строку от сервера (блокирующий вызов).
     * Вернёт null, если соединение прервано.
     */
    public String readLine() throws IOException {
        if (in != null) {
            return in.readLine();
        }
        return null;
    }

    /**
     * Закрыть сокет и все потоки.
     */
    public void close() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
