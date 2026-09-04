package py.sistienda.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import py.sistienda.data.database.DatabaseInitializer;
import py.sistienda.data.database.SqliteConnectionFactory;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        var connectionFactory = new SqliteConnectionFactory();
        var databaseInitializer = new DatabaseInitializer(connectionFactory);
        databaseInitializer.initialize();

        var root = new StackPane(new Label("SisTienda - UI OK ✅ (DB OK)"));
        var scene = new Scene(root, 480, 240);
        stage.setTitle("SisTienda");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
