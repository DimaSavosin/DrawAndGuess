package ru.itis.drawandguess.server;

public class LobbyManager {
    private boolean lobbyCreated = false;
    private String lobbyPassword = null;
    private int maxPlayers = 0;

    public boolean isLobbyCreated() {
        return lobbyCreated;
    }

    public void createLobby(String password, int maxPlayersCount) {
        this.lobbyCreated = true;
        this.lobbyPassword = password;
        this.maxPlayers = maxPlayersCount;
    }

    public boolean checkLobbyPassword(String password) {
        return lobbyPassword != null && lobbyPassword.equals(password);
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void resetLobby() {
        this.lobbyCreated = false;
        this.lobbyPassword = null;
        this.maxPlayers = 0;
    }
}
