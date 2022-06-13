package me.andyreckt.sunset;

import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import me.andyreckt.sunset.annotations.Command;
import me.andyreckt.sunset.annotations.Param;
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


public class Sunset {

    private final JavaPlugin plugin;

    private final HashMap<Class<?>, PType<?>> typesMap;

    @Getter @Setter
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

        org.bukkit.command.Command command = new org.bukkit.command.Command(name, commandAnnotation.description(), ChatColor.RED + usage.toString(), aliases) {
            @Override @SneakyThrows
            public boolean execute(CommandSender commandSender, String alias, String[] args) {

                List<Object> parameters = new ArrayList<>();

                if (method.getParameterTypes()[0].equals(ConsoleCommandSender.class)) {
                    if (!(commandSender instanceof ConsoleCommandSender)) {
                        commandSender.sendMessage(ChatColor.RED + "This command can only be executed with the console.");
                        return false;
                    }
                }

                if (method.getParameterTypes()[0].equals(Player.class)) {
                    if (!(commandSender instanceof Player)) {
                        commandSender.sendMessage(ChatColor.RED + "This command can only be executed as a player.");
                        return false;
                    }

                    parameters.add(commandSender);
                } else parameters.add(commandSender);

                if (!commandAnnotation.permission().equalsIgnoreCase("")) {
                    if (commandAnnotation.permission().equalsIgnoreCase("op")) {
                        if (commandSender instanceof Player && (!commandSender.hasPermission("op")) && (!commandSender.isOp())) {
                            commandSender.sendMessage(permissionMessage);
                            return false;
                        }
                    }
                    if (!commandSender.hasPermission(commandAnnotation.permission())) {
                        commandSender.sendMessage(permissionMessage);
                        return false;
                    }
                }
                if (method.getParameterTypes().length > 1) {
                    for (int index = 1; index < method.getParameterTypes().length; index++) {

                        Param param = null;

                        for (Annotation annotation : method.getParameterAnnotations()[index]) {
                            if (annotation instanceof Param) {
                                param = (Param) annotation;
                                break;
                            }
                        }

                        if (param == null) {
                            commandSender.sendMessage(ChatColor.RED + "Parameter annotation is null ?!");
                            return false;
                        }

                        if (args.length == 0) {
                            boolean bool = false;

                            for (Annotation[] annotationArray : method.getParameterAnnotations()) {
                                for (Annotation annotation : annotationArray) {
                                    if (annotation instanceof Param) {
                                        param = (Param) annotation;
                                        if (param.baseValue().equalsIgnoreCase("")) bool = true;
                                    }
                                }
                            }
                            if (bool) {
                                commandSender.sendMessage(getUsage());
                                return false;
                            }
                        } else {
                            if (args[index-1] == null) {
                                if (param.baseValue().equalsIgnoreCase("")) return false;
                                if (param.wildcard()) {
                                    parameters.add(typesMap.get(method.getParameterTypes()[index]).transform(commandSender, param.baseValue()));
                                    break;
                                } else parameters.add(typesMap.get(method.getParameterTypes()[index]).transform(commandSender, param.baseValue()));
                            } else if (param.wildcard()) {
                                StringBuilder sb = new StringBuilder();

                                for (int arg = index-1; arg < args.length; arg++) {
                                    sb.append(args[arg]).append(" ");
                                }

                                parameters.add(typesMap.get(method.getParameterTypes()[index]).transform(commandSender, sb.toString()));
                                break;
                            } else parameters.add(typesMap.get(method.getParameterTypes()[index]).transform(commandSender, args[index-1]));
                        }
                    }
                }


                if (commandAnnotation.async()) ForkJoinPool.commonPool().execute(() -> {
                    try {
                        method.invoke(instance, parameters.toArray());
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                });
                else method.invoke(instance, parameters.toArray());
                return true;
            }

            @Override @SneakyThrows
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
                if (!(sender instanceof Player)) return (null);

                Player player = (Player) sender;

                Param param = null;
                if (!((method.getParameterCount()-1) <= args.length)) return (new ArrayList<>());

                int index = args.length - 1;
                for (Annotation annotation : method.getParameterAnnotations()[args.length]) {
                    if (annotation instanceof Param) {
                        param = (Param) annotation;
                        break;
                    }
                }

                if (param == null) return (new ArrayList<>());
                if (!Arrays.equals(param.tabCompleteFlags(), new String[]{""})) return (Arrays.asList(param.tabCompleteFlags()));
                PType<?> pType = typesMap.get(method.getParameterTypes()[args.length]);

                if (param.wildcard()) {

                    StringBuilder sb = new StringBuilder();

                    for (int arg = index; arg < args.length; arg++) {
                        sb.append(args[arg]).append(" ");
                    }

                    return (pType.complete(player, sb.toString()));

                } else return (pType.complete(player, args[index]));
            }
        };
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
