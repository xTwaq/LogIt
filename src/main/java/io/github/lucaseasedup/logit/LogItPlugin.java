package io.github.lucaseasedup.logit;

import io.github.lucaseasedup.logit.command.DisabledCommandExecutor;
import io.github.lucaseasedup.logit.command.LogItCommand;
import io.github.lucaseasedup.logit.command.NopCommandExecutor;
import io.github.lucaseasedup.logit.common.FatalReportedException;
import io.github.lucaseasedup.logit.config.LocationSerializable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.MetricsLite;

public final class LogItPlugin extends JavaPlugin
{
    /**
     * Internal method. Do not call directly.
     */
    @Override
    public void onEnable()
    {
        try
        {
            MetricsLite metrics = new MetricsLite(this);
            metrics.start();
        }
        catch (IOException ex)
        {
        }
        
        try
        {
            // Load default messages.
            loadMessages(getConfig().getString("locale", "en"));
        }
        catch (IOException ex)
        {
            // If messages could not be loaded, just log the failure.
            // They're not necessary for LogIt to work.
            getLogger().log(Level.WARNING, "Could not load messages.", ex);
        }
        
        try
        {
            loadLibraries();
        }
        catch (FatalReportedException ex)
        {
            disable();
        }
        
        getCommand("logit")
                .setExecutor(new LogItCommand());
        getCommand("$logit-nop-command")
                .setExecutor(new NopCommandExecutor());
        
        core = LogItCore.getInstance();
        
        try
        {
            core.start();
        }
        catch (FatalReportedException ex)
        {
            if (getConfig().getBoolean("terminateUnsafeServer", true))
            {
                Bukkit.getServer().shutdown();
            }
            else
            {
                disable();
            }
        }
    }
    
    /**
     * Internal method. Do not call directly.
     */
    @Override
    public void onDisable()
    {
        if (core != null)
        {
            if (core.isStarted())
            {
                core.stop();
            }
            
            core = null;
        }
        
        getCommand("logit")
                .setExecutor(new DisabledCommandExecutor());
        getCommand("$logit-nop-command")
                .setExecutor(new DisabledCommandExecutor());
        
        messages = null;
        customGlobalMessages = null;
        customLocalMessages = null;
    }
    
    private void enable()
    {
        getServer().getPluginManager().enablePlugin(this);
    }
    
    private void disable()
    {
        Bukkit.getConsoleSender().sendMessage(getMessage("pluginStopping"));
        
        getServer().getPluginManager().disablePlugin(this);
    }
    
    /**
     * Loads message files.
     * 
     * <p> First, it tries to load local <i>messages_{prefix}.properties</i>
     * from the plugin JAR. If the file does not exist, it tries to load global
     * <i>messages.properties</i>. If this fails too,
     * {@code FileNotFoundException} will be thrown.
     * 
     * <p> This method will also try to load custom
     * global/local message files from the <i>lang</i> directory if present,
     * that will be merged with built-in message files.
     * 
     * @param prefix the locale prefix.
     * 
     * @throws IOException if there was an error while reading.
     */
    public void loadMessages(String prefix) throws IOException
    {
        if (prefix == null)
            throw new IllegalArgumentException();
        
        messages = null;
        
        try (JarFile jarFile = new JarFile(getFile()))
        {
            JarEntry jarEntry = jarFile.getJarEntry("messages_" + prefix + ".properties");
            
            if (jarEntry == null)
            {
                jarEntry = jarFile.getJarEntry("messages.properties");
            }
            
            if (jarEntry == null)
                throw new FileNotFoundException("No message file found.");
            
            InputStream messagesInputStream = jarFile.getInputStream(jarEntry);
            
            try (Reader messagesReader = new InputStreamReader(messagesInputStream, "UTF-8"))
            {
                messages = new PropertyResourceBundle(messagesReader);
            }
        }
        
        loadCustomGlobalMessages("lang/messages.properties");
        loadCustomLocalMessages("lang/messages_" + prefix + ".properties");
    }
    
    private void loadCustomGlobalMessages(String path) throws IOException
    {
        if (path == null)
            throw new IllegalArgumentException();
        
        File file = new File(getDataFolder(), path);
        
        if (!file.isFile())
        {
            customGlobalMessages = null;
            
            return;
        }
        
        try (InputStream is = new FileInputStream(file))
        {
            customGlobalMessages = new PropertyResourceBundle(is);
        }
    }
    
    private void loadCustomLocalMessages(String path) throws IOException
    {
        if (path == null)
            throw new IllegalArgumentException();
        
        File file = new File(getDataFolder(), path);
        
        if (!file.isFile())
        {
            customLocalMessages = null;
            
            return;
        }
        
        try (InputStream is = new FileInputStream(file))
        {
            customLocalMessages = new PropertyResourceBundle(is);
        }
    }
    
    public static String getMessage(String label)
    {
        if (label == null)
            throw new IllegalArgumentException();
        
        if (getInstance() == null)
            return label;
        
        if (getInstance().messages == null)
            return label;
        
        String message;
        
        try
        {
            message = getInstance().messages.getString(label);
            
            if (getInstance().customGlobalMessages != null
                    && getInstance().customGlobalMessages.containsKey(label))
            {
                message = getInstance().customGlobalMessages.getString(label);
            }
            
            if (getInstance().customLocalMessages != null
                    && getInstance().customLocalMessages.containsKey(label))
            {
                message = getInstance().customLocalMessages.getString(label);
            }
        }
        catch (MissingResourceException | ClassCastException ex)
        {
            return label;
        }
        
        message = ChatColor.translateAlternateColorCodes('&', message);
        
        return getInstance().replaceGlobalTokens(message);
    }
    
    public String replaceGlobalTokens(String message)
    {
        if (message == null)
            throw new IllegalArgumentException();
        
        message = message.replace("%bukkit_version%",
                Bukkit.getBukkitVersion());
        message = message.replace("%logit_version%",
                LogItPlugin.getInstance().getDescription().getVersion());
        message = message.replace("%server_id%", Bukkit.getServerId());
        message = message.replace("%server_ip%", Bukkit.getIp());
        message = message.replace("%server_motd%", Bukkit.getMotd());
        message = message.replace("%server_name%", Bukkit.getServerName());
        
        return message;
    }
    
    public void loadLibraries() throws FatalReportedException
    {
        File dir = new File(getDataFolder(), "lib");
        
        if (!dir.isDirectory())
            return;
        
        File[] files = dir.listFiles();
        
        for (File file : files)
        {
            if (!file.isFile())
                continue;
            
            if (!file.getName().endsWith(".jar"))
                continue;
            
            loadLibrary(file.getName());
        }
    }
    
    private void loadLibrary(String filename) throws FatalReportedException
    {
        if (isLibraryLoaded(filename))
            return;
        
        try
        {
            File file = new File(getDataFolder(), "lib/" + filename);
            
            if (!file.exists())
                throw new FileNotFoundException();
            
            URLClassLoader classLoader = (URLClassLoader)
                    ClassLoader.getSystemClassLoader();
            URL url = file.toURI().toURL();
            
            if (!Arrays.asList(classLoader.getURLs()).contains(url))
            {
                Method addUrlMethod = URLClassLoader.class.getDeclaredMethod(
                        "addURL", new Class[]{URL.class}
                );
                addUrlMethod.setAccessible(true);
                addUrlMethod.invoke(classLoader, new Object[]{url});
            }
        }
        catch (FileNotFoundException | MalformedURLException ex)
        {
            getLogger().log(Level.SEVERE,
                    "Library " + filename + " was not found");
            disable();
            
            FatalReportedException.throwNew(ex);
        }
        catch (ReflectiveOperationException ex)
        {
            getLogger().log(Level.SEVERE,
                    "Could not load library " + filename, ex);
            disable();
            
            FatalReportedException.throwNew(ex);
        }
    }
    
    public boolean isLibraryLoaded(String filename)
    {
        File file = new File(getDataFolder(), "lib/" + filename);
        URLClassLoader classLoader = (URLClassLoader)
                ClassLoader.getSystemClassLoader();
        URL url;
        
        try
        {
            url = file.toURI().toURL();
        }
        catch (MalformedURLException ex)
        {
            return false;
        }
        
        return Arrays.asList(classLoader.getURLs()).contains(url);
    }
    
    public static String getCraftBukkitVersion()
    {
        String packageName = Bukkit.getServer()
                .getClass().getPackage().getName();
        String[] packageParts = packageName.split("\\.");
        
        return packageParts[packageParts.length - 1];
    }
    
    /* package */ static LogItPlugin getInstance()
    {
        if (instance == null)
        {
            instance = (LogItPlugin)
                    Bukkit.getPluginManager().getPlugin("LogIt");
        }
        
        return instance;
    }
    
    static
    {
        ConfigurationSerialization.registerClass(LocationSerializable.class);
    }
    
    public static final String PACKAGE = "io.github.lucaseasedup.logit";
    
    private static LogItPlugin instance = null;
    private PropertyResourceBundle messages;
    private PropertyResourceBundle customGlobalMessages;
    private PropertyResourceBundle customLocalMessages;
    private LogItCore core;
}
