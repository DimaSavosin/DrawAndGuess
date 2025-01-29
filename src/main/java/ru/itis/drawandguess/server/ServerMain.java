package ru.itis.drawandguess.server;


public class ServerMain {
    private static final int PORT = 12345;

    public static void main(String[] args) {
        // Создаем Server и запускаем его
        Server server = new Server(PORT);
        server.start();
    }
}
