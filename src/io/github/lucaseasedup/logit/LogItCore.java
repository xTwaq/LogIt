/*
 * LogItCore.java
 *
 * Copyright (C) 2012-2013 LucasEasedUp
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.github.lucaseasedup.logit;

import com.google.common.collect.ImmutableList;
import static io.github.lucaseasedup.logit.LogItPlugin.getMessage;
import static io.github.lucaseasedup.logit.LogItPlugin.parseMessage;
import io.github.lucaseasedup.logit.account.AccountManager;
import io.github.lucaseasedup.logit.account.AccountWatcher;
import io.github.lucaseasedup.logit.command.ChangeEmailCommand;
import io.github.lucaseasedup.logit.command.ChangePassCommand;
import io.github.lucaseasedup.logit.command.LogItCommand;
import io.github.lucaseasedup.logit.command.LoginCommand;
import io.github.lucaseasedup.logit.command.LogoutCommand;
import io.github.lucaseasedup.logit.command.RecoverPassCommand;
import io.github.lucaseasedup.logit.command.RegisterCommand;
import io.github.lucaseasedup.logit.command.UnregisterCommand;
import io.github.lucaseasedup.logit.config.LogItConfiguration;
import io.github.lucaseasedup.logit.db.AbstractRelationalDatabase;
import io.github.lucaseasedup.logit.db.CsvDatabase;
import io.github.lucaseasedup.logit.db.H2Database;
import io.github.lucaseasedup.logit.db.MySqlDatabase;
import io.github.lucaseasedup.logit.db.Pinger;
import io.github.lucaseasedup.logit.db.SqliteDatabase;
import io.github.lucaseasedup.logit.hash.BCrypt;
import io.github.lucaseasedup.logit.hash.HashGenerator;
import static io.github.lucaseasedup.logit.hash.HashGenerator.getBCrypt;
import static io.github.lucaseasedup.logit.hash.HashGenerator.getMd2;
import static io.github.lucaseasedup.logit.hash.HashGenerator.getMd5;
import static io.github.lucaseasedup.logit.hash.HashGenerator.getSha1;
import static io.github.lucaseasedup.logit.hash.HashGenerator.getSha256;
import static io.github.lucaseasedup.logit.hash.HashGenerator.getSha384;
import static io.github.lucaseasedup.logit.hash.HashGenerator.getSha512;
import static io.github.lucaseasedup.logit.hash.HashGenerator.getWhirlpool;
import io.github.lucaseasedup.logit.listener.AccountEventListener;
import io.github.lucaseasedup.logit.listener.BlockEventListener;
import io.github.lucaseasedup.logit.listener.EntityEventListener;
import io.github.lucaseasedup.logit.listener.InventoryEventListener;
import io.github.lucaseasedup.logit.listener.PlayerEventListener;
import io.github.lucaseasedup.logit.listener.ServerEventListener;
import io.github.lucaseasedup.logit.listener.SessionEventListener;
import io.github.lucaseasedup.logit.listener.TickEventListener;
import io.github.lucaseasedup.logit.session.SessionManager;
import io.github.lucaseasedup.logit.util.FileUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import static org.bukkit.ChatColor.stripColor;
import org.bukkit.entity.Player;

/**
 * LogItCore is the central part of LogIt.
 * </p>
 * It's also the most important part of API.
 * 
 * @author LucasEasedUp
 */
public class LogItCore
{
    private LogItCore(LogItPlugin plugin)
    {
        this.plugin = plugin;
        this.firstRun = !new File(plugin.getDataFolder(), "config.yml").exists();
    }
    
    public void start()
    {
        if (started)
            return;
        
        new File(plugin.getDataFolder(), "lib").mkdir();
        
        config = new LogItConfiguration(plugin);
        config.load();
        
        if (firstRun)
        {
            new File(plugin.getDataFolder(), "backup").mkdir();
            new File(plugin.getDataFolder(), "mail").mkdir();
            new File(plugin.getDataFolder(), "lang").mkdir();
            
            try
            {
                FileUtils.extractResource("/password-recovery.html", new File(plugin.getDataFolder(), "mail/password-recovery.html"));
            }
            catch (IOException ex)
            {
                Logger.getLogger(LogItCore.class.getName()).log(Level.WARNING, null, ex);
            }
        }
        
        if (!loaded)
            load();
        
        try
        {
            LogItPlugin.loadLibrary(LIB_H2);
            LogItPlugin.loadLibrary(LIB_MAIL);
        }
        catch (IOException | ReflectiveOperationException ex)
        {
        }
        
        if (getHashingAlgorithm().equals(HashingAlgorithm.UNKNOWN))
        {
            log(SEVERE, getMessage("UNKNOWN_HASHING_ALGORITHM").replace("%ha%", getHashingAlgorithm().name()));
            plugin.disable();
            
            return;
        }
        
        try
        {
            switch (getStorageAccountsDbType())
            {
                case SQLITE:
                {
                    database = new SqliteDatabase("jdbc:sqlite:" +
                        plugin.getDataFolder() + "/" + config.getString("storage.accounts.sqlite.filename"));
                    database.connect();
                    
                    break;
                }
                case MYSQL:
                {
                    database = new MySqlDatabase(config.getString("storage.accounts.mysql.host"));
                    ((MySqlDatabase) database).connect(
                        config.getString("storage.accounts.mysql.user"),
                        config.getString("storage.accounts.mysql.password"),
                        config.getString("storage.accounts.mysql.database")
                    );
                    
                    break;
                }
                case H2:
                {
                    database = new H2Database("jdbc:h2:" +
                        plugin.getDataFolder() + "/" + config.getString("storage.accounts.h2.filename"));
                    database.connect();
                    
                    break;
                }
                case CSV:
                {
                    database = new CsvDatabase(plugin.getDataFolder());
                    database.connect();
                    
                    break;
                }
                default:
                {
                    log(SEVERE, getMessage("UNKNOWN_STORAGE_TYPE").replace("%st%", getStorageAccountsDbType().name()));
                    plugin.disable();
                    
                    return;
                }
            }
        }
        catch (IOException | SQLException | ReflectiveOperationException ex)
        {
            Logger.getLogger(LogItCore.class.getName()).log(Level.SEVERE, null, ex);
            plugin.disable();
            
            return;
        }
        
        pinger = new Pinger(database);
        
        try
        {
            String[] storageColumnsArray = getStorageColumns();
            
            database.createTableIfNotExists(config.getString("storage.accounts.table"), storageColumnsArray);
            
            ArrayList<String> existingColumns = database.getColumnNames(config.getString("storage.accounts.table"));
            
            database.setAutobatchEnabled(true);
            
            for (int i = 0; i < storageColumnsArray.length; i += 2)
            {
                if (!existingColumns.contains(storageColumnsArray[i]))
                {
                    database.addColumn(config.getString("storage.accounts.table"), storageColumnsArray[i], storageColumnsArray[i + 1]);
                }
            }
            
            database.executeBatch();
            database.setAutobatchEnabled(false);
        }
        catch (SQLException ex)
        {
            Logger.getLogger(LogItCore.class.getName()).log(Level.SEVERE, null, ex);
            plugin.disable();
            
            return;
        }
        
        accountManager = new AccountManager(this, database);
        
        try
        {
            accountManager.loadAccounts();
        }
        catch (SQLException ex)
        {
            Logger.getLogger(LogItCore.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        accountWatcher = new AccountWatcher(this, accountManager);
        backupManager  = new BackupManager(this, database);
        sessionManager = new SessionManager(this, accountManager);
        
        if (config.getBoolean("password-recovery.enabled"))
        {
            mailSender = new MailSender();
            mailSender.configure(config.getString("mail.smtp-host"), config.getInt("mail.smtp-port"),
                config.getString("mail.smtp-user"), config.getString("mail.smtp-password"));
            
            plugin.getCommand("recoverpass").setExecutor(new RecoverPassCommand(this));
        }
        
        SqliteDatabase inventoryDatabase = new SqliteDatabase("jdbc:sqlite:" +
            plugin.getDataFolder() + "/" + config.getString("storage.inventories.filename"));
        
        try
        {
            inventoryDatabase.connect();
            inventoryDatabase.createTableIfNotExists("inventories", new String[]{
                "username",     "VARCHAR(16)",
                "world",        "VARCHAR(512)",
                "inv_contents", "TEXT",
                "inv_armor",    "TEXT"
            });
            
            ResultSet rs = inventoryDatabase.select("inventories", new String[]{"username", "world", "inv_contents", "inv_armor"});
            
            while (rs.next())
            {
                try
                {
                    InventoryDepository.saveInventory(rs.getString("world"), rs.getString("username"),
                        InventoryDepository.unserialize(rs.getString("inv_contents")),
                        InventoryDepository.unserialize(rs.getString("inv_armor")));
                }
                catch (FileNotFoundException ex)
                {
                }
            }
            
            inventoryDatabase.truncateTable("inventories");
        }
        catch (IOException | SQLException ex)
        {
            Logger.getLogger(LogItCore.class.getName()).log(Level.SEVERE, null, ex);
            plugin.disable();
            
            return;
        }
        
        inventoryDepository = new InventoryDepository(inventoryDatabase);
        waitingRoom = new WaitingRoom(this, database);
        
        pingerTaskId          = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, pinger, 0L, 2400L);
        sessionManagerTaskId  = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, sessionManager, 0L, 20L);
        tickEventCallerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, tickEventCaller, 0L, 1L);
        accountWatcherTaskId  = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, accountWatcher, 0L, 12000L);
        backupManagerTaskId   = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, backupManager, 0L, 40L);
        
        if (plugin.getServer().getPluginManager().isPluginEnabled("Vault"))
        {
            permissions = plugin.getServer().getServicesManager().getRegistration(Permission.class).getProvider();
        }
        
        log(FINE, getMessage("PLUGIN_START_SUCCESS")
                .replace("%st%", getStorageAccountsDbType().name())
                .replace("%ha%", getHashingAlgorithm().name()));
        
        if (firstRun)
            log(INFO, getMessage("PLUGIN_FIRST_RUN"));
        
        started = true;
    }
    
    public void stop()
    {
        if (!started)
            return;
        
        try
        {
            database.close();
        }
        catch (SQLException ex)
        {
            Logger.getLogger(LogItCore.class.getName()).log(Level.WARNING, null, ex);
        }
        
        Bukkit.getScheduler().cancelTask(pingerTaskId);
        Bukkit.getScheduler().cancelTask(sessionManagerTaskId);
        Bukkit.getScheduler().cancelTask(tickEventCallerTaskId);
        Bukkit.getScheduler().cancelTask(accountWatcherTaskId);
        Bukkit.getScheduler().cancelTask(backupManagerTaskId);
        
        log(FINE, getMessage("PLUGIN_STOP_SUCCESS"));
        
        started = false;
    }
    
    public void restart()
    {
        File sessions = new File(plugin.getDataFolder() + "/" + config.getString("storage.sessions.filename"));
        
        try
        {
            sessionManager.exportSessions(sessions);
        }
        catch (SQLException ex)
        {
            Logger.getLogger(LogItCore.class.getName()).log(Level.WARNING, null, ex);
        }
        
        stop();
        
        try
        {
            plugin.loadMessages();
        }
        catch (IOException ex)
        {
            Logger.getLogger(LogItCore.class.getName()).log(Level.WARNING, null, ex);
        }
        
        start();
        
        try
        {
            sessionManager.importSessions(sessions);
        }
        catch (SQLException ex)
        {
            Logger.getLogger(LogItCore.class.getName()).log(Level.WARNING, null, ex);
        }
        
        sessions.delete();
        
        log(INFO, getMessage("RELOADED"));
    }
    
    /**
     * Changes the global password.
     * 
     * @param password New global password;
     */
    public void changeGlobalPassword(String password)
    {
        String salt = HashGenerator.generateSalt(getHashingAlgorithm());
        
        config.set("password.global-password.salt", salt);
        config.set("password.global-password.hash", hash(password, salt));
        
        log(INFO, getMessage("GLOBALPASS_SET_SUCCESS"));
    }
    
    /**
     * Checks if the given password matches its hashed equivalent.
     * 
     * @param password Plain-text password.
     * @param hashedPassword Hashed password.
     * @return True if passwords match.
     */
    public boolean checkPassword(String password, String hashedPassword)
    {
        if (getHashingAlgorithm() == HashingAlgorithm.BCRYPT)
        {
            return BCrypt.checkpw(password, hashedPassword);
        }
        else
        {
            return hashedPassword.equals(hash(password));
        }
    }
    
    /**
     * Checks if the given password matches its hashed equivalent.
     * Salt is used only if it is enabled in the config file.
     * 
     * @param password Plain-text password.
     * @param hashedPassword Hashed password.
     * @param salt Salt.
     * @return True if passwords match.
     */
    public boolean checkPassword(String password, String hashedPassword, String salt)
    {
        if (getHashingAlgorithm() == HashingAlgorithm.BCRYPT)
        {
            return BCrypt.checkpw(password, hashedPassword);
        }
        else
        {
            if (config.getBoolean("password.use-salt"))
                return hashedPassword.equals(hash(password, salt));
            else
                return hashedPassword.equals(hash(password));
        }
    }
    
    /**
     * Checks if the given password matches the global password.
     * 
     * @param password Plain-text password.
     * @return True if passwords match.
     */
    public boolean checkGlobalPassword(String password)
    {
        return checkPassword(password, config.getString("password.global-password.hash"),
            config.getString("password.global-password.salt"));
    }
    
    public void removeGlobalPassword()
    {
        config.set("password.global-password.hash", "");
        config.set("password.global-password.salt", "");
        
        log(INFO, getMessage("GLOBALPASS_REMOVE_SUCCESS"));
    }
    
    public void sendPasswordRecoveryMail(String username) throws IOException, SQLException
    {
        try
        {
            if (mailSender == null)
                throw new RuntimeException("MailSender not initialized.");
            
            String to = accountManager.getEmail(username);
            String from = config.getString("mail.email-address");
            String subject = parseMessage(config.getString("password-recovery.subject"), new String[]{
                "%player%", username,
            });
            
            String playerPassword = generatePassword(config.getInt("password-recovery.password-length"),
                config.getString("password-recovery.password-combination"));
            accountManager.changeAccountPassword(username, playerPassword);
            
            StringBuilder bodyBuilder = new StringBuilder();
            
            try (FileReader fr = new FileReader(new File(plugin.getDataFolder(), config.getString("password-recovery.body-template"))))
            {
                int b;

                while ((b = fr.read()) != -1)
                {
                    bodyBuilder.append((char) b);
                }
            }
            
            String body = parseMessage(bodyBuilder.toString(), new String[]{
                "%player%", username,
                "%password%", playerPassword
            });
            
            mailSender.sendMail(new String[]{to}, from, subject, body, config.getBoolean("password-recovery.html-enabled"));
            
            log(FINE, getMessage("RECOVER_PASSWORD_SUCCESS_LOG", new String[]{
                "%player%", username,
                "%email%", to,
            }));
        }
        catch (IOException | SQLException ex)
        {
            log(WARNING, getMessage("RECOVER_PASSWORD_FAIL_LOG", new String[]{
                "%player%", username,
            }));
            
            throw ex;
        }
    }
    
    public String generatePassword(int length, String combination)
    {
        char[] charTable = combination.toCharArray();
        
        StringBuilder sb = new StringBuilder(length);
        Random random = new Random();
        
        for (int i = 0, n = charTable.length; i < length; i++)
        {
            sb.append(charTable[random.nextInt(n)]);
        }
        
        return sb.toString();
    }
    
    /**
     * Checks if the player is forced to login (by either "force-login.global" set to true, or
     * the player being in a world with forced login).
     * <p/>
     * If the player has the "logit.force-login.exempt" permission, it returns false.
     * 
     * @param player Player.
     * @return True if the specified player is forced to log in.
     */
    public boolean isPlayerForcedToLogin(Player player)
    {
        return (config.getBoolean("force-login.global") || config.getStringList("force-login.in-worlds").contains(player.getWorld().getName()))
                && !player.hasPermission("logit.force-login.exempt");
    }
    
    /**
     * Updates player group depending on whether they're logged in or logged out.
     * 
     * @param player Player whose group is to be updated.
     */
    public void updatePlayerGroup(Player player)
    {
        if (!isLinkedToVault())
            return;
        
        if (accountManager.isRegistered(player.getName()))
        {
            permissions.playerRemoveGroup(player, config.getString("groups.unregistered"));
            permissions.playerAddGroup(player, config.getString("groups.registered"));
        }
        else
        {
            permissions.playerRemoveGroup(player, config.getString("groups.registered"));
            permissions.playerAddGroup(player, config.getString("groups.unregistered"));
        }
        
        if (sessionManager.isSessionAlive(player))
        {
            permissions.playerRemoveGroup(player, config.getString("groups.logged-out"));
            permissions.playerAddGroup(player, config.getString("groups.logged-in"));
        }
        else
        {
            permissions.playerRemoveGroup(player, config.getString("groups.logged-in"));
            permissions.playerAddGroup(player, config.getString("groups.logged-out"));
        }
    }
    
    /**
     * Checks if LogIt is linked to Vault (e.i.&nbsp;LogItCore has been loaded and Vault is enabled).
     * 
     * @return True if LogIt is linked to Vault.
     */
    public boolean isLinkedToVault()
    {
        return permissions != null;
    }
    
    /**
     * Hashes the given string through algorithm specified in the config.
     * 
     * @param string String to be hashed.
     * @return Resulting hash.
     */
    public String hash(String string)
    {
        switch (getHashingAlgorithm())
        {
            case PLAIN:
            {
                return string;
            }
            case MD2:
            {
                return getMd2(string);
            }
            case MD5:
            {
                return getMd5(string);
            }
            case SHA1:
            {
                return getSha1(string);
            }
            case SHA256:
            {
                return getSha256(string);
            }
            case SHA384:
            {
                return getSha384(string);
            }
            case SHA512:
            {
                return getSha512(string);
            }
            case WHIRLPOOL:
            {
                return getWhirlpool(string);
            }
            case BCRYPT:
            {
                return getBCrypt(string, "");
            }
            default:
            {
                return null;
            }
        }
    }
    
    /**
     * Hashes the given string and salt through algorithm specified in the config.
     * <p/>
     * If algorithm is PLAIN, salt won't be appended to the string.
     * 
     * @param string String to be hashed.
     * @param salt Salt.
     * @return Resulting hash.
     */
    public String hash(String string, String salt)
    {
        String hash;
        
        if (getHashingAlgorithm() == HashingAlgorithm.BCRYPT)
            hash = getBCrypt(string, salt);
        else if (getHashingAlgorithm() == HashingAlgorithm.PLAIN)
            hash = hash(string);
        else
            hash = hash(string + salt);
        
        return hash;
    }
    
    public StorageType getStorageAccountsDbType()
    {
        String s = plugin.getConfig().getString("storage.accounts.db-type");
        
        if (s.equalsIgnoreCase("sqlite"))
        {
            return StorageType.SQLITE;
        }
        else if (s.equalsIgnoreCase("mysql"))
        {
            return StorageType.MYSQL;
        }
        else if (s.equalsIgnoreCase("h2"))
        {
            return StorageType.H2;
        }
        else if (s.equalsIgnoreCase("csv"))
        {
            return StorageType.CSV;
        }
        else
        {
            return StorageType.UNKNOWN;
        }
    }
    
    public HashingAlgorithm getHashingAlgorithm()
    {
        String s = plugin.getConfig().getString("password.hashing-algorithm");
        
        if (s.equalsIgnoreCase("plain"))
        {
            return HashingAlgorithm.PLAIN;
        }
        else if (s.equalsIgnoreCase("md2"))
        {
            return HashingAlgorithm.MD2;
        }
        else if (s.equalsIgnoreCase("md5"))
        {
            return HashingAlgorithm.MD5;
        }
        else if (s.equalsIgnoreCase("sha-1"))
        {
            return HashingAlgorithm.SHA1;
        }
        else if (s.equalsIgnoreCase("sha-256"))
        {
            return HashingAlgorithm.SHA256;
        }
        else if (s.equalsIgnoreCase("sha-384"))
        {
            return HashingAlgorithm.SHA384;
        }
        else if (s.equalsIgnoreCase("sha-512"))
        {
            return HashingAlgorithm.SHA512;
        }
        else if (s.equalsIgnoreCase("whirlpool"))
        {
            return HashingAlgorithm.WHIRLPOOL;
        }
        else if (s.equalsIgnoreCase("bcrypt"))
        {
            return HashingAlgorithm.BCRYPT;
        }
        else
        {
            return HashingAlgorithm.UNKNOWN;
        }
    }
    
    public IntegrationType getIntegration()
    {
        String s = plugin.getConfig().getString("integration");
        
        if (s.equalsIgnoreCase("none"))
        {
            return IntegrationType.NONE;
        }
        else if (s.equalsIgnoreCase("phpbb"))
        {
            return IntegrationType.PHPBB;
        }
        else
        {
            return IntegrationType.UNKNOWN;
        }
    }
    
    /**
     * Logs a message in the name of LogIt.
     * 
     * @param level Message level.
     * @param message Message.
     */
    public void log(Level level, String message)
    {
        if (config.getBoolean("log-to-file.enabled"))
        {
            Date             date = new Date();
            SimpleDateFormat sdf  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            try (FileWriter fileWriter = new FileWriter(new File(plugin.getDataFolder(), config.getString("log-to-file.filename")), true))
            {
                fileWriter.write(sdf.format(date) + " [" + level.getName() + "] " + stripColor(message) + "\n");
            }
            catch (IOException ex)
            {
                Logger.getLogger(LogItCommand.class.getName()).log(Level.WARNING, null, ex);
            }
        }
        
        plugin.getLogger().log(level, stripColor(message));
    }
    
    /**
     * Returns an array containing storage columns,
     * where getStorageColumns()[i] is the column name,
     * and getStorageColumns()[i + 1] is the column type.
     * 
     * @return Storage columns.
     */
    public String[] getStorageColumns()
    {
        return storageColumns.toArray(new String[storageColumns.size()]);
    }
    
    public Permission getPermissions()
    {
        return permissions;
    }
    
    public WaitingRoom getWaitingRoom()
    {
        return waitingRoom;
    }
    
    public AbstractRelationalDatabase getDatabase()
    {
        return database;
    }
    
    public InventoryDepository getInventoryDepository()
    {
        return inventoryDepository;
    }
    
    public MailSender getMailSender()
    {
        return mailSender;
    }
    
    public BackupManager getBackupManager()
    {
        return backupManager;
    }
    
    public AccountManager getAccountManager()
    {
        return accountManager;
    }
    
    public SessionManager getSessionManager()
    {
        return sessionManager;
    }
    
    public boolean isFirstRun()
    {
        return firstRun;
    }
    
    public LogItPlugin getPlugin()
    {
        return plugin;
    }
    
    public LogItConfiguration getConfig()
    {
        return config;
    }
    
    private void load()
    {
        storageColumns = ImmutableList.of(
            config.getString("storage.accounts.columns.username"),        "VARCHAR(16)",
            config.getString("storage.accounts.columns.salt"),            "VARCHAR(20)",
            config.getString("storage.accounts.columns.password"),        "VARCHAR(256)",
            config.getString("storage.accounts.columns.ip"),              "VARCHAR(64)",
            config.getString("storage.accounts.columns.email"),           "VARCHAR(255)",
            config.getString("storage.accounts.columns.last_active"),     "INTEGER",
            config.getString("storage.accounts.columns.location_world"),  "VARCHAR(512)",
            config.getString("storage.accounts.columns.location_x"),      "REAL",
            config.getString("storage.accounts.columns.location_y"),      "REAL",
            config.getString("storage.accounts.columns.location_z"),      "REAL",
            config.getString("storage.accounts.columns.location_yaw"),    "REAL",
            config.getString("storage.accounts.columns.location_pitch"),  "REAL",
            config.getString("storage.accounts.columns.in_wr"),           "INTEGER"
        );
        
        tickEventCaller = new TickEventCaller();
        
        plugin.getServer().getPluginManager().registerEvents(new TickEventListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ServerEventListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new BlockEventListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new EntityEventListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PlayerEventListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new InventoryEventListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new AccountEventListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new SessionEventListener(this), plugin);
        
        plugin.getCommand("logit").setExecutor(new LogItCommand(this));
        plugin.getCommand("login").setExecutor(new LoginCommand(this));
        plugin.getCommand("logout").setExecutor(new LogoutCommand(this));
        plugin.getCommand("register").setExecutor(new RegisterCommand(this));
        plugin.getCommand("unregister").setExecutor(new UnregisterCommand(this));
        plugin.getCommand("changepass").setExecutor(new ChangePassCommand(this));
        plugin.getCommand("changeemail").setExecutor(new ChangeEmailCommand(this));
        
        loaded = true;
    }
    
    /**
     * The preferred way to obtain the instance of LogIt core.
     * 
     * @return Instance of LogIt core.
     */
    public static LogItCore getInstance()
    {
        return INSTANCE;
    }
    
    public static enum StorageType
    {
        UNKNOWN, SQLITE, MYSQL, H2, CSV
    }
    
    public static enum HashingAlgorithm
    {
        UNKNOWN, PLAIN, MD2, MD5, SHA1, SHA256, SHA384, SHA512, WHIRLPOOL, BCRYPT
    }
    
    public static enum IntegrationType
    {
        UNKNOWN, NONE, PHPBB
    }
    
    public static final String LIB_H2 = "h2-1.3.171.jar";
    public static final String LIB_MAIL = "mail-1.4.5.jar";
    
    private static final LogItCore INSTANCE = new LogItCore(LogItPlugin.getInstance());
    
    private final LogItPlugin plugin;
    
    private final boolean firstRun;
    private boolean loaded = false;
    private boolean started = false;
    
    private LogItConfiguration  config;
    private AbstractRelationalDatabase database;
    private Pinger              pinger;
    private Permission          permissions;
    private SessionManager      sessionManager;
    private AccountManager      accountManager;
    private AccountWatcher      accountWatcher;
    private BackupManager       backupManager;
    private WaitingRoom         waitingRoom;
    private InventoryDepository inventoryDepository;
    private MailSender          mailSender;
    private TickEventCaller     tickEventCaller;
    
    private int pingerTaskId;
    private int sessionManagerTaskId;
    private int tickEventCallerTaskId;
    private int accountWatcherTaskId;
    private int backupManagerTaskId;
    
    private List<String> storageColumns;
}