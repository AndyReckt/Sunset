package me.andyreckt.sunset.executor;

import lombok.SneakyThrows;
import me.andyreckt.sunset.Sunset;
import me.andyreckt.sunset.annotations.Command;
import me.andyreckt.sunset.annotations.MainCommand;
import me.andyreckt.sunset.annotations.Param;
import me.andyreckt.sunset.annotations.SubCommand;
import me.andyreckt.sunset.parameter.PType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

public class SunsetSubCommand extends org.bukkit.command.Command {

    private final Sunset sunset;
    private final MainCommand command;
    private final Method[] methods;
    private final Object instance;


    public SunsetSubCommand(Sunset sunset, Object instance, MainCommand command, String usage, List<String> aliases, Method... methods) {
        super(command.names()[0], command.description(), usage, aliases);
        this.sunset = sunset;
        this.command = command;
        this.methods = methods;
        this.instance = instance;
    }

    @Override @SneakyThrows
    public boolean execute(CommandSender commandSender, String s, String[] strings) {
        if (strings.length == 0 || strings[0] == null || strings[0].equals("")) {
            strings = new String[]{command.helpCommand()};
        }

        ArrayList<String> abc = new ArrayList<>(Arrays.asList(strings));
        String commandName = strings[0];
        abc.remove(0);
        String[] args = abc.toArray(new String[0]);
        Method method = null;
        for (Method m : methods) {
            if (Arrays.stream(m.getDeclaredAnnotation(SubCommand.class).names()).anyMatch(commandName::equalsIgnoreCase)) {
                method = m;
                break;
            }
        }
        if (method == null) {
            commandSender.sendMessage(ChatColor.RED + "This subcommand does not exist.");
            return false;
        }
        SubCommand subCommand = method.getDeclaredAnnotation(SubCommand.class);
        super.setUsage(subCommand.usage());

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

        if (!command.permission().equalsIgnoreCase("")) {
            if (command.permission().equalsIgnoreCase("op")) {
                if (commandSender instanceof Player && (!commandSender.hasPermission("op")) && (!commandSender.isOp())) {
                    commandSender.sendMessage(sunset.getPermissionMessage());
                    return false;
                }
            }
            if (!commandSender.hasPermission(command.permission())) {
                commandSender.sendMessage(sunset.getPermissionMessage());
                return false;
            }
        }
        if (!subCommand.permission().equalsIgnoreCase("")) {
            if (subCommand.permission().equalsIgnoreCase("op")) {
                if (commandSender instanceof Player && (!commandSender.hasPermission("op")) && (!commandSender.isOp())) {
                    commandSender.sendMessage(sunset.getPermissionMessage());
                    return false;
                }
            }
            if (!commandSender.hasPermission(subCommand.permission())) {
                commandSender.sendMessage(sunset.getPermissionMessage());
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
                    int i = 0;
                    for (Annotation[] annotationArray : method.getParameterAnnotations()) {
                        for (Annotation annotation : annotationArray) {
                            if (annotation instanceof Param) {
                                param = (Param) annotation;
                                if (param.baseValue().equalsIgnoreCase("")) bool = true;
                                else parameters.add(sunset.getTypesMap().get(method.getParameterTypes()[i]).transform(commandSender, param.baseValue()));
                            }
                        }
                        i++;
                    }
                    if (bool) {
                        commandSender.sendMessage(getUsage());
                        return false;
                    }
                } else {
                    if (args[index-1] == null || args[index-1].equals("")) {
                        if (param.baseValue().equalsIgnoreCase("")) return false;

                        if (param.wildcard()) {
                            parameters.add(sunset.getTypesMap().get(method.getParameterTypes()[index-1]).transform(commandSender, param.baseValue()));
                            break;
                        } else {
                            parameters.add(sunset.getTypesMap().get(method.getParameterTypes()[index-1]).transform(commandSender, param.baseValue()));
                        }
                    } else if (param.wildcard()) {
                        StringBuilder sb = new StringBuilder();

                        for (int arg = index-1; arg < args.length; arg++) {
                            sb.append(args[arg]).append(" ");
                        }

                        parameters.add(sunset.getTypesMap().get(method.getParameterTypes()[index]).transform(commandSender, sb.toString()));
                        break;
                    } else parameters.add(sunset.getTypesMap().get(method.getParameterTypes()[index]).transform(commandSender, args[index-1]));
                }
            }
        }


        if (subCommand.async()) {
            Method finalMethod = method;
            ForkJoinPool.commonPool().execute(() -> {
                try {
                    finalMethod.invoke(instance, parameters.toArray());
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            });
        }
        else method.invoke(instance, parameters.toArray());
        return true;
    }

    @Override @SneakyThrows
    public List<String> tabComplete(CommandSender sender, String s, String[] strings) {
        if (!(sender instanceof Player)) return (null);

        Player player = (Player) sender;

        if (strings.length == 0 || strings[0] == null || strings[0].equals("")) {
            List<String> toReturn = new ArrayList<>();
            for (Method m : methods) {
                toReturn.add(m.getDeclaredAnnotation(SubCommand.class).names()[0]);
            }
            return toReturn;
        }

        Param param = null;

        ArrayList<String> abc = new ArrayList<>(Arrays.asList(strings));
        String commandName = strings[0];
        abc.remove(0);
        String[] args = abc.toArray(new String[0]);
        Method method = null;
        for (Method m : methods) {
            if (Arrays.stream(m.getDeclaredAnnotation(SubCommand.class).names()).anyMatch(commandName::equalsIgnoreCase)) {
                method = m;
                break;
            }
        }
        if (method == null) {
            return (new ArrayList<>());
        }

        if (!((method.getParameterCount()-1) <= args.length)) return (new ArrayList<>());

        int index = args.length - 2;
        for (Annotation annotation : method.getParameterAnnotations()[args.length -1]) {
            if (annotation instanceof Param) {
                param = (Param) annotation;
                break;
            }
        }

        if (param == null) return (new ArrayList<>());
        if (!Arrays.equals(param.tabCompleteFlags(), new String[]{""})) return (Arrays.asList(param.tabCompleteFlags()));
        PType<?> pType = sunset.getTypesMap().get(method.getParameterTypes()[args.length -1]);

        if (param.wildcard()) {

            StringBuilder sb = new StringBuilder();

            for (int arg = index; arg < args.length -1; arg++) {
                sb.append(args[arg]).append(" ");
            }

            return (pType.complete(player, sb.toString()));

        } else return (pType.complete(player, args[index]));
    }
}
