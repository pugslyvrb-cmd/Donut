package com.pugsly.donutcore;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class DonutOrders {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final List<Order> ORDERS = new ArrayList<>();

    private DonutOrders() {}

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("orders")
                .executes(context -> list(context.getSource(), ""))
                .then(Commands.literal("create")
                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> create(context.getSource(),
                                                DoubleArgumentType.getDouble(context, "price"),
                                                IntegerArgumentType.getInteger(context, "amount"))))))
                .then(Commands.literal("fill")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(context -> fill(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "id"),
                                                IntegerArgumentType.getInteger(context, "amount"))))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(context -> cancel(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "id"))))));

        dispatcher.register(Commands.literal("order")
                .then(Commands.argument("search", StringArgumentType.greedyString())
                        .executes(context -> list(context.getSource(), StringArgumentType.getString(context, "search")))));
    }

    private static int create(CommandSourceStack source, double price, int amount) {
        ServerPlayer buyer = getPlayer(source);
        if (buyer == null) {
            source.sendFailure(Component.literal("Only players can create orders."));
            return 0;
        }

        ItemStack held = buyer.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Hold the item you want to buy in your main hand."));
            return 0;
        }

        double total = price * amount;
        if (DonutCore.getBalance(buyer) < total) {
            source.sendFailure(Component.literal("You need $" + DonutCore.format(total) + " to create this order."));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        DonutCore.removeBalance(buyer, total);
        int id = NEXT_ID.getAndIncrement();
        ORDERS.add(new Order(id, buyer.getUUID(), buyer.getName().getString(), itemId, price, amount));
        source.sendSuccess(() -> Component.literal("Order #" + id + " created: " + amount + "x " + itemId + " at $" + DonutCore.format(price) + " each."), false);
        return 1;
    }

    private static int fill(CommandSourceStack source, int id, int amount) {
        ServerPlayer supplier = getPlayer(source);
        if (supplier == null) {
            source.sendFailure(Component.literal("Only players can fill orders."));
            return 0;
        }

        Order order = find(id);
        if (order == null || order.remaining <= 0) {
            source.sendFailure(Component.literal("That order is no longer active."));
            return 0;
        }
        if (order.buyer.equals(supplier.getUUID())) {
            source.sendFailure(Component.literal("You cannot fill your own order."));
            return 0;
        }

        ItemStack held = supplier.getMainHandItem();
        if (held.isEmpty() || !BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals(order.itemId)) {
            source.sendFailure(Component.literal("Hold the exact item requested by the order."));
            return 0;
        }

        int accepted = Math.min(Math.min(amount, held.getCount()), order.remaining);
        held.shrink(accepted);
        double payment = accepted * order.price;
        order.remaining -= accepted;
        DonutCore.addBalance(supplier, payment);
        source.sendSuccess(() -> Component.literal("Filled " + accepted + "x " + order.itemId + " for $" + DonutCore.format(payment) + "."), false);
        return 1;
    }

    private static int cancel(CommandSourceStack source, int id) {
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            source.sendFailure(Component.literal("Only players can cancel orders."));
            return 0;
        }

        Order order = find(id);
        if (order == null || !order.buyer.equals(player.getUUID())) {
            source.sendFailure(Component.literal("You don't own that order."));
            return 0;
        }
        double refund = order.remaining * order.price;
        DonutCore.addBalance(player, refund);
        ORDERS.remove(order);
        source.sendSuccess(() -> Component.literal("Order #" + id + " cancelled. Refunded $" + DonutCore.format(refund) + "."), false);
        return 1;
    }

    private static int list(CommandSourceStack source, String search) {
        String needle = search.toLowerCase();
        source.sendSuccess(() -> Component.literal("§6§lACTIVE ORDERS"), false);
        int shown = 0;
        for (Order order : ORDERS) {
            if (order.remaining <= 0 || (!needle.isEmpty() && !order.itemId.toLowerCase().contains(needle))) continue;
            int id = order.id;
            source.sendSuccess(() -> Component.literal("§e#" + id + " §f" + order.itemId + " §7x" + order.remaining + " §a$" + DonutCore.format(order.price) + "/item §8by " + order.buyerName), false);
            shown++;
            if (shown >= 20) break;
        }
        if (shown == 0) source.sendSuccess(() -> Component.literal("§7No matching orders."), false);
        return 1;
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }

    private static Order find(int id) {
        for (Order order : ORDERS) if (order.id == id) return order;
        return null;
    }

    private static final class Order {
        final int id;
        final UUID buyer;
        final String buyerName;
        final String itemId;
        final double price;
        int remaining;

        Order(int id, UUID buyer, String buyerName, String itemId, double price, int remaining) {
            this.id = id;
            this.buyer = buyer;
            this.buyerName = buyerName;
            this.itemId = itemId;
            this.price = price;
            this.remaining = remaining;
        }
    }
}
