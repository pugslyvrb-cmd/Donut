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
    private static final double STARTING_BALANCE = 1000.0;

    @Override
    public void onInitialize() {
        LOGGER.info("DonutCore loaded for Minecraft 26.2");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("bal")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        double balance = getBalance(player);
                        context.getSource().sendSuccess(
                                () -> Component.literal("Balance: $" + format(balance)), false);
                        return 1;
                    })
                    .then(Commands.literal("balance")
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                context.getSource().sendSuccess(
                                        () -> Component.literal("Balance: $" + format(getBalance(player))), false);
                                return 1;
                            })));

            dispatcher.register(Commands.literal("money")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        context.getSource().sendSuccess(
                                () -> Component.literal("You have $" + format(getBalance(player))), false);
                        return 1;
                    }));

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
                                        context.getSource().sendSuccess(
                                                () -> Component.literal("Paid $" + format(amount) + " to " + target.getName().getString() + "."), false);
                                        target.sendSystemMessage(Component.literal("You received $" + format(amount) + " from " + sender.getName().getString() + "."));
                                        return 1;
                                    }))));

            dispatcher.register(Commands.literal("baltop")
                    .executes(context -> {
                        context.getSource().sendSuccess(() -> Component.literal("--- DonutCore BalTop ---"), false);
                        BALANCES.entrySet().stream()
                                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                                .limit(10)
                                .forEach(entry -> context.getSource().sendSuccess(
                                        () -> Component.literal(entry.getKey().toString() + " : $" + format(entry.getValue())), false));
                        return 1;
                    }));
        });
    }

    private static double getBalance(ServerPlayer player) {
        return BALANCES.computeIfAbsent(player.getUUID(), ignored -> STARTING_BALANCE);
    }

    private static void addBalance(ServerPlayer player, double amount) {
        BALANCES.put(player.getUUID(), getBalance(player) + amount);
    }

    private static void removeBalance(ServerPlayer player, double amount) {
        BALANCES.put(player.getUUID(), getBalance(player) - amount);
    }

    private static String format(double amount) {
        if (amount == Math.rint(amount)) {
            return String.format("%.0f", amount);
        }
        return String.format("%.2f", amount);
    }
}
