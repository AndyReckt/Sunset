package me.andyreckt.sunset;

import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import me.andyreckt.sunset.annotations.Command;
import me.andyreckt.sunset.annotations.MainCommand;
import me.andyreckt.sunset.annotations.Param;
import me.andyreckt.sunset.annotations.SubCommand;
import me.andyreckt.sunset.executor.SunsetCommand;
import me.andyreckt.sunset.executor.SunsetSubCommand;
import me.andyreckt.sunset.parameter.PData;
import me.andyreckt.sunset.parameter.PType;
import me.andyreckt.sunset.parameter.defaults.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URL;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Getter
public class Sunset {


    private final JavaPlugin plugin;

    private final HashMap<Class<?>, PType<?>> typesMap;

    @Setter
    private String permissionMessage = ChatColor.RED + "You are lacking the permission to execute this command.";


    public Sunset(JavaPlugin plugin) {
        this.plugin = plugin;
        this.typesMap = new HashMap<>();
        this.registerDefaultTypes();
    }




    /**
     * Scans all the static methods in a class and checks if any is a command.
     *
     * @param clazz The class to scan
     */
    @Deprecated
    public void registerCommands(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) continue;
            if (!method.isAnnotationPresent(Command.class)) continue;
            registerMethod(method, null);
        }
    }


    /**
     * Scans all the methods in a class and checks if any is a command.
     *
     * @param object The instance of the class to scan
     */
    public void registerCommands(Object object) {
        for (Method method : object.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Command.class)) continue;
            registerMethod(method, object);
        }
    }


    public void registerCommandWithSubCommands(Object object) {
        if (object.getClass().getAnnotation(MainCommand.class) == null) return;
        MainCommand mainCommandAnnotation = object.getClass().getAnnotation(MainCommand.class);

        List<Method> methodList = new ArrayList<>();

        for (Method method : object.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(SubCommand.class)) continue;
            SubCommand commandAnnotation = method.getAnnotation(SubCommand.class);

            List<PData> parameterData = new ArrayList<>();

            for (int parameterIndex = 1; parameterIndex < method.getParameterTypes().length; parameterIndex++) {
                Param paramAnnotation = null;

                for (Annotation annotation : method.getParameterAnnotations()[parameterIndex]) {
                    if (annotation instanceof Param) {
                        paramAnnotation = (Param) annotation;
                        break;
                    }
                }

                if (paramAnnotation != null) {
                    Class<?> paramClass = method.getParameterTypes()[parameterIndex];
                    if (!this.typesMap.containsKey(paramClass)) {
                        plugin.getLogger().severe("[Sunset] Class '" + paramClass.getSimpleName() + ".class' does not have an assigned type adapter (did you register it?)");
                        return;
                    }
                    parameterData.add(new PData(paramAnnotation, paramClass));
                } else {
                    plugin.getLogger().warning("[Sunset] Method '" + method.getName() + "' has a parameter without a @Param annotation.");
                    return;
                }
            }

            StringBuilder usage = new StringBuilder("/").append(mainCommandAnnotation.names()[0]).append(" ").append(commandAnnotation.names()[0]);
            for (PData param : parameterData) {
                usage.append(" ").append(param.isRequired() ? "<" : "[").append(param.getName()).append(param.isRequired() ? ">" : "]");
            }

            if (!commandAnnotation.usage().equalsIgnoreCase("none")) usage = new StringBuilder(commandAnnotation.usage());
            methodList.add(method);
        }

        String mainUsage = "/" + mainCommandAnnotation.names()[0] + " " + mainCommandAnnotation.helpCommand();
        if (!mainCommandAnnotation.usage().equalsIgnoreCase("none")) mainUsage = mainCommandAnnotation.usage();

        List<String> aliases = new ArrayList<>();
        for (String alias : mainCommandAnnotation.names()) {
            if (alias.equalsIgnoreCase(mainCommandAnnotation.names()[0])) continue;
            aliases.add(alias);
        }

        SunsetSubCommand command = new SunsetSubCommand(this, object, mainCommandAnnotation, mainUsage, aliases, methodList.toArray(new Method[0]));
        getCommandMap().register(plugin.getName(), command);
    }



    /**
     * Register a Type adapter.
     *
     * @param from the PType object to register from. (IE: new WorldType())
     * @param to the class to return when transformed. (IE: World.class)
     */
    public void registerType(PType<?> from, Class<?> to) {
        this.typesMap.put(to, from);
    }


    /**
     * Get a collection of all the Classes in a package
     *
     * @param plugin The plugin to take the classes from (in most cases you will just use your plugin instance)
     * @param packageName The package to take the classes from (ie: me.andyreckt.sunset.parameter.defaults)
     * @return All the classes in the package
     */
    public Collection<Class<?>> getClassesInPackage(Plugin plugin, String packageName) {
        Collection<Class<?>> classes = new ArrayList<>();

        CodeSource codeSource = plugin.getClass().getProtectionDomain().getCodeSource();
        URL resource = codeSource.getLocation();
        String relPath = packageName.replace('.', '/');
        String resPath = resource.getPath().replace("%20", " ");
        String jarPath = resPath.replaceFirst("[.]jar[!].*", ".jar").replaceFirst("file:", "");
        JarFile jarFile;

        try {
            jarFile = new JarFile(jarPath);
        } catch (IOException e) {
            throw (new RuntimeException("Unexpected IOException reading JAR File '" + jarPath + "'", e));
        }

        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();
            String className = null;

            if (entryName.endsWith(".class") && entryName.startsWith(relPath) && entryName.length() > (relPath.length() + "/".length())) {
                className = entryName.replace('/', '.').replace('\\', '.').replace(".class", "");
            }

            if (className != null) {
                Class<?> clazz = null;

                try {
                    clazz = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }

                if (clazz != null) {
                    classes.add(clazz);
                }
            }
        }

        try {
            jarFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return (ImmutableSet.copyOf(classes));
    }


    /**
     * Registers a method as a command.
     *
     * @param method The method to register
     * @param instance The instance of the class to register the method to
     */
    private void registerMethod(Method method, Object instance) {

        Command commandAnnotation = method.getAnnotation(Command.class);
        List<PData> parameterData = new ArrayList<>();

        for (int parameterIndex = 1; parameterIndex < method.getParameterTypes().length; parameterIndex++) {
            Param paramAnnotation = null;

            for (Annotation annotation : method.getParameterAnnotations()[parameterIndex]) {
                if (annotation instanceof Param) {
                    paramAnnotation = (Param) annotation;
                    break;
                }
            }

            if (paramAnnotation != null) {
                Class<?> paramClass = method.getParameterTypes()[parameterIndex];
                if (!this.typesMap.containsKey(paramClass)) {
                    plugin.getLogger().severe("[Sunset] Class '" + paramClass.getSimpleName() + ".class' does not have an assigned type adapter (did you register it?)");
                    return;
                }
                parameterData.add(new PData(paramAnnotation, paramClass));
            } else {
                plugin.getLogger().warning("[Sunset] Method '" + method.getName() + "' has a parameter without a @Param annotation.");
                return;
            }
        }

        String name = commandAnnotation.names()[0];
        List<String> aliases = new ArrayList<>();
        for (String alias : commandAnnotation.names()) {
            if (alias.equalsIgnoreCase(name)) continue;
            aliases.add(alias);
        }
        StringBuilder usage = new StringBuilder("/").append(name);
        for (PData param : parameterData) {
            usage.append(" ").append(param.isRequired() ? "<" : "[").append(param.getName()).append(param.isRequired() ? ">" : "]");
        }

        if (!commandAnnotation.usage().equalsIgnoreCase("none")) usage = new StringBuilder(commandAnnotation.usage());

        SunsetCommand command = new SunsetCommand(this, method, instance, commandAnnotation, ChatColor.RED + usage.toString(), aliases);
        getCommandMap().register(plugin.getName(), command);
    }


    /**
     * Registers all the commands in the package.
     */
    @SneakyThrows
    public void registerCommandsInPackage(String packageName) {
        Collection<Class<?>> classes = getClassesInPackage(plugin, packageName);
        for (Class<?> clazz : classes) {
            registerCommands(clazz.newInstance());
        }
    }

    /**
     * Registers all the commands with subcommands in the package.
     */
    @SneakyThrows
    public void registerCommandsWithSubCommandsInPackage(String packageName) {
        Collection<Class<?>> classes = getClassesInPackage(plugin, packageName);
        for (Class<?> clazz : classes) {
            registerCommandWithSubCommands(clazz.newInstance());
        }
    }

    /**
     * Get the CommandMap for the server.
     *
     * @return The CommandMap for the server.
     */
    private SimpleCommandMap getCommandMap() {
        try {
            SimplePluginManager pluginManager = (SimplePluginManager) Bukkit.getPluginManager();

            Field field = pluginManager.getClass().getDeclaredField("commandMap");
            field.setAccessible(true);

            return (SimpleCommandMap) field.get(pluginManager);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Registers the default parameter types.
     */
    private void registerDefaultTypes() {
        registerType(new BooleanType(), boolean.class);
        registerType(new DoubleType(), double.class);
        registerType(new FloatType(), float.class);
        registerType(new IntegerType(), int.class);
        registerType(new StringType(), String.class);
        registerType(new OfflinePlayerType(), OfflinePlayer.class);
        registerType(new PlayerType(), Player.class);
        registerType(new WorldType(), World.class);
    }

}
