package py.sistienda.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        var root = new StackPane(new Label("SisTienda - UI OK ✅"));
        var scene = new Scene(root, 480, 240);
        stage.setTitle("SisTienda");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
