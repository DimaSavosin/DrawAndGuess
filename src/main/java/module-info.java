module ru.itis.drawandguess {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;

    opens ru.itis.drawandguess to javafx.fxml;
    exports ru.itis.drawandguess;
    exports ru.itis.drawandguess.gameInterface to javafx.graphics;
}

