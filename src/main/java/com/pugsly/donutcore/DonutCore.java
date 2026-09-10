package com.pugsly.donutcore;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DonutCore implements ModInitializer {
    public static final String MOD_ID = "donutcore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<UUID, Double> BALANCES = new HashMap<>();
    private static final Map<UUID, Long> SHARDS = new HashMap<>();
    private static final double STARTING_BALANCE = 0.0;

    @Override
    public void onInitialize() {
        LOGGER.info("DonutCore loaded for Minecraft 26.2");
        DonutStats.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("bal")
                    .executes(context -> showBalance(context.getSource().getPlayerOrException(), context.getSource()))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .executes(context -> {
                                ServerPlayer target = context.getSource().getServer().getPlayerList()
                                        .getPlayerByName(StringArgumentType.getString(context, "player"));
                                if (target == null) {
                                    context.getSource().sendFailure(Component.literal("That player is not online."));
                                    return 0;
                                }
                                return showBalance(target, context.getSource());
                            })));

            dispatcher.register(Commands.literal("money")
                    .executes(context -> showBalance(context.getSource().getPlayerOrException(), context.getSource())));

            dispatcher.register(Commands.literal("pay")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                    .executes(context -> {
                                        ServerPlayer sender = context.getSource().getPlayerOrException();
                                        String targetName = StringArgumentType.getString(context, "player");
                                        double amount = DoubleArgumentType.getDouble(context, "amount");
                                        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);
                                        if (target == null) {
                                            context.getSource().sendFailure(Component.literal("That player is not online."));
                                            return 0;
                                        }
                                        if (target == sender) {
                                            context.getSource().sendFailure(Component.literal("You cannot pay yourself."));
                                            return 0;
                                        }
                                        if (getBalance(sender) < amount) {
                                            context.getSource().sendFailure(Component.literal("You don't have enough money."));
                                            return 0;
                                        }
                                        removeBalance(sender, amount);
                                        addBalance(target, amount);
                                        context.getSource().sendSuccess(() -> Component.literal(
                                                "Paid $" + format(amount) + " to " + target.getName().getString() + "."), false);
                                        target.sendSystemMessage(Component.literal(
                                                "You received $" + format(amount) + " from " + sender.getName().getString() + "."));
                                        return 1;
                                    }))));

            dispatcher.register(Commands.literal("baltop")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.literal("--- Balance Top ---"), false);
                        BALANCES.entrySet().stream()
                                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                                .limit(10)
                                .forEach(entry -> context.getSource().sendSuccess(
                                        () -> Component.literal(entry.getKey() + " : $" + format(entry.getValue())), false));
                        return 1;
                    }));

            dispatcher.register(Commands.literal("shards")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        context.getSource().sendSuccess(() -> Component.literal(
                                "Shards: " + getShards(player)), false);
                        return 1;
                    }));

            dispatcher.register(Commands.literal("leaderboard")
                    .executes(context -> showLeaderboards(context.getSource().getPlayerOrException())));

            dispatcher.register(Commands.literal("shop")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.literal("§6§lDONUT SHOP"), false);
                        context.getSource().sendSuccess(() -> Component.literal("§7Shop categories are being wired into the market system."), false);
                        context.getSource().sendSuccess(() -> Component.literal("§eUse /sell and /orders for the economy systems."), false);
                        return 1;
                    }));

            dispatcher.register(Commands.literal("orders")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.literal("§6§lORDERS"), false);
                        context.getSource().sendSuccess(() -> Component.literal("§7Player buy-orders will appear here."), false);
                        context.getSource().sendSuccess(() -> Component.literal("§e/order <search>"), false);
                        return 1;
                    }));

            dispatcher.register(Commands.literal("order")
                    .then(Commands.argument("search", StringArgumentType.greedyString())
                            .executes(context -> {
                                String search = StringArgumentType.getString(context, "search");
                                context.getSource().sendSuccess(() -> Component.literal(
                                        "Searching Orders for: " + search), false);
                                return 1;
                            })));
        });
    }

    private static int showBalance(ServerPlayer player, net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Balance: $" + format(getBalance(player))), false);
        return 1;
    }

    private static int showLeaderboards(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6§lDONUT LEADERBOARDS"));
        player.sendSystemMessage(Component.literal("§e/baltop §7— richest players"));
        player.sendSystemMessage(Component.literal("§eLeaderboard §7— kills, deaths and playtime"));
        return 1;
    }

    public static double getBalance(ServerPlayer player) {
        return BALANCES.computeIfAbsent(player.getUUID(), ignored -> STARTING_BALANCE);
    }

    public static long getShards(ServerPlayer player) {
        return SHARDS.getOrDefault(player.getUUID(), 0L);
    }

    private static void addBalance(ServerPlayer player, double amount) {
        BALANCES.put(player.getUUID(), getBalance(player) + amount);
    }

    private static void removeBalance(ServerPlayer player, double amount) {
        BALANCES.put(player.getUUID(), getBalance(player) - amount);
    }

    public static String format(double amount) {
        if (amount == Math.rint(amount)) {
            return String.format("%.0f", amount);
        }
        return String.format("%.2f", amount);
    }
}
