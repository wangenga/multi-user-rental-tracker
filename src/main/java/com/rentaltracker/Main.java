package com.rentaltracker;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import com.rentaltracker.infrastructure.Database;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.logging.LogEvents;
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
import com.rentaltracker.transport.screens.BrowseListingScreen;
import com.rentaltracker.transport.screens.StartupScreen;

public class Main {

    static String resolvePath(String[] args) {
        return args.length > 0 ? args[0] : "data/multiUser.db";
    }

    public static void main(String[] args) {
        run(resolvePath(args), System.in, System.out, Clock.systemDefaultZone());
    }

    public static void run(String path, InputStream in, PrintStream out, Clock clock) {
        // TODO: swap NoOpLogger for the file logger once it exists
        run(path, in, out, clock, new NoOpLogger());
    }

    /**
     * A database that won't open is unrecoverable: the detail is logged, the user gets one
     * plain line, and the app stops. Everything after that is handled inside the menu.
     */
    public static void run(String path, InputStream in, PrintStream out, Clock clock, AppLogger logger) {
        Database db = open(path, logger);
        if (db == null) {
            out.println(CANNOT_OPEN);
            return;
        }
        try (db) {
            buildMenu(db, new Prompter(in, out), clock, logger).run();
        } catch (RepositoryException e) {
            // only close() can get here, the menu handles everything else; the user is already done
            logger.error(LogEvents.DB_ERROR, "op", "db.close", "error", "database", "detail", e.getMessage());
        }
    }

    static final String CANNOT_OPEN = "Cannot run the app: the database could not be opened. See the log for details.";

    /** @return the open database, or null if it could not be opened (already logged) */
    private static Database open(String path, AppLogger logger) {
        try {
            createParentDirectory(path);
            return new Database(path);
        } catch (IOException | RepositoryException e) {
            Throwable cause = e.getCause();
            logger.error(LogEvents.DB_OPEN_FAILED, "path", path,
                    "exception", e.getClass().getName(),
                    "detail", cause == null ? e.getMessage() : e.getMessage() + " | cause: " + cause.getMessage());
            return null;
        }
    }

    private static MainMenu buildMenu(Database db, Prompter prompter, Clock clock, AppLogger logger) {
        UserRepository userRepository = new UserRepository(db);
        ItemRepository itemRepository = new ItemRepository(db.connection());
        RentalRepository rentalRepository = new RentalRepository(db);
        Transactor tx = new SqliteTransactor(db);

        UserService users = new UserService(userRepository);
        AuthService auth = new AuthService(userRepository, new BcryptPasswordHasher(), logger);
        ItemService items = new ItemService(itemRepository, rentalRepository, users, tx, logger);
        RentalService rentals = new RentalService(itemRepository, rentalRepository, tx, clock, logger);

        return new MainMenu(prompter, logger,
                new StartupScreen(prompter, auth),
                new ListItemScreen(prompter, items),
                new InventoryScreen(prompter, items),
                new BrowseListingScreen(prompter, rentals),
                new ConfirmReturnScreen(prompter, rentals));
    }

    private static void createParentDirectory(String path) throws IOException {
        Path parent = Path.of(path).toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}