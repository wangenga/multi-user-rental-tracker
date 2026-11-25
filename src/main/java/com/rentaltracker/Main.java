package com.rentaltracker;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import com.rentaltracker.infrastructure.Database;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.logging.NoOpLogger;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.SqliteTransactor;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.repository.exception.RepositoryException;
import com.rentaltracker.security.BcryptPasswordHasher;
import com.rentaltracker.service.AuthService;
import com.rentaltracker.service.ItemService;
import com.rentaltracker.service.RentalService;
import com.rentaltracker.service.UserService;
import com.rentaltracker.transport.MainMenu;
import com.rentaltracker.transport.Prompter;
import com.rentaltracker.transport.screens.ConfirmReturnScreen;
import com.rentaltracker.transport.screens.InventoryScreen;
import com.rentaltracker.transport.screens.ListItemScreen;
import com.rentaltracker.transport.screens.RecordRentalScreen;
import com.rentaltracker.transport.screens.StartupScreen;

public class Main {

    static String resolvePath(String[] args) {
        return args.length > 0 ? args[0] : "data/multiUser.db";
    }

    public static void main(String[] args) {
        run(resolvePath(args), System.in, System.out, Clock.systemDefaultZone());
    }

    public static void run(String path, InputStream in, PrintStream out, Clock clock) {
        try {
            createParentDirectory(path);
            try (Database db = new Database(path)) {
                buildMenu(db, new Prompter(in, out), clock).run();
            }
        } catch (IOException | RepositoryException e) {
            out.println("Cannot run the app: " + e.getMessage());
        }
    }

    private static MainMenu buildMenu(Database db, Prompter prompter, Clock clock) {
        UserRepository userRepository = new UserRepository(db);
        ItemRepository itemRepository = new ItemRepository(db.connection());
        RentalRepository rentalRepository = new RentalRepository(db);
        Transactor tx = new SqliteTransactor(db);

        // TODO: swap NoOpLogger for the file logger once it exists
        AppLogger logger = new NoOpLogger();

        UserService users = new UserService(userRepository);
        AuthService auth = new AuthService(userRepository, new BcryptPasswordHasher(), logger);
        ItemService items = new ItemService(itemRepository, rentalRepository, users, tx);
        RentalService rentals = new RentalService(itemRepository, rentalRepository, users, tx, clock);

        return new MainMenu(prompter,
                new StartupScreen(prompter, auth),
                new ListItemScreen(prompter, items),
                new InventoryScreen(prompter, items),
                new RecordRentalScreen(prompter, rentals),
                new ConfirmReturnScreen(prompter, rentals));
    }

    private static void createParentDirectory(String path) throws IOException {
        Path parent = Path.of(path).toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}