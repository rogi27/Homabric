package space.rogi27.homabric.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.lucko.fabric.api.permissions.v0.Permissions
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.LiteralText
import net.minecraft.text.TranslatableText
import net.minecraft.util.Formatting
import space.rogi27.homabric.Homabric
import space.rogi27.homabric.config.HomesConfig
import space.rogi27.homabric.helpers.TeleportHelper
import space.rogi27.homabric.objects.HomeObject
import space.rogi27.homabric.objects.PlayerObject
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer

object AdminCommands {
    fun init() {
        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback {
            dispatcher: CommandDispatcher<ServerCommandSource?>, _: Boolean ->
                dispatcher.register(
                    CommandManager.literal("homabric")
                        .requires(Permissions.require("homabric.admin.use", 2))
                        .then(CommandManager.literal("reload")
                                .requires(Permissions.require("homabric.admin.reload", 2))
                                .executes(AdminCommands::reload)
                        )
                        .then(CommandManager.literal("teleport")
                                .requires(Permissions.require("homabric.admin.teleport", 2))
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .suggests(AdminCommands::suggestPlayers)
                                        .then(CommandManager.argument("home", StringArgumentType.word())
                                                .suggests(AdminCommands::suggestPlayerHomes)
                                                .executes((AdminCommands::teleport))
                                        )
                                )
                        )
                        .then(CommandManager.literal("set")
                                .requires(Permissions.require("homabric.admin.set", 2))
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .suggests(AdminCommands::suggestPlayers)
                                        .then(CommandManager.argument("home", StringArgumentType.word())
                                                .suggests(AdminCommands::suggestPlayerHomes)
                                                .executes((AdminCommands::set))
                                        )
                                )
                        )
                        .then(CommandManager.literal("remove")
                                .requires(Permissions.require("homabric.admin.remove", 2))
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .suggests(AdminCommands::suggestPlayers)
                                        .then(CommandManager.argument("home", StringArgumentType.word())
                                                .suggests(AdminCommands::suggestPlayerHomes)
                                                .executes((AdminCommands::remove))
                                        )
                                )
                        )
                        .then(CommandManager.literal("list")
                                .requires(Permissions.require("homabric.admin.list", 2))
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .suggests(AdminCommands::suggestPlayers)
                                        .executes((AdminCommands::list))
                                )
                        ).executes((AdminCommands::info))
                )
        })
    }

    fun suggestPlayers(context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder): CompletableFuture<Suggestions?> {
        HomesConfig.getPlayers().forEach(BiConsumer { name: String?, _: PlayerObject? ->
            builder.suggest(name)
        })
        return builder.buildFuture()
    }

    fun suggestPlayerHomes(context: CommandContext<ServerCommandSource>, builder: SuggestionsBuilder): CompletableFuture<Suggestions?> {
        val player = HomesConfig.getPlayer(context.getArgument("player", String::class.java))
        player?.homeNames?.forEach(builder::suggest)
        return builder.buildFuture()
    }

    @Throws(CommandSyntaxException::class)
    fun reload(context: CommandContext<ServerCommandSource>): Int {
        Homabric.config.load()
        Homabric.homesConfig.load()
        context.source.sendFeedback(
            TranslatableText("text.homabric.admin_config_reloaded").formatted(Formatting.GRAY),
            false
        )
        return 1
    }

    @Throws(CommandSyntaxException::class)
    fun info(context: CommandContext<ServerCommandSource>): Int {
        val info = Arrays.asList(
            "%s by Rogi27",
            "%s",
            "%s",
            "%s"
        )
        context.source.sendFeedback(
            TranslatableText(
                java.lang.String.join("\n", info),
                LiteralText("Homabric").formatted(Formatting.AQUA),
                TranslatableText("text.homabric.admin_info_line1").formatted(Formatting.GRAY),
                LiteralText(" - /help homabric").formatted(Formatting.GRAY),
                LiteralText(" - /help home").formatted(Formatting.GRAY)
            ).formatted(Formatting.GREEN), false
        )
        return 1
    }

    @Throws(CommandSyntaxException::class)
    fun teleport(context: CommandContext<ServerCommandSource>): Int {
        val homeName = context.getArgument("home", String::class.java)
        val player = HomesConfig.getPlayer(context.getArgument("player", String::class.java))
        if (player == null) {
            context.source.sendFeedback(
                TranslatableText("text.homabric.no_player_exists").formatted(Formatting.RED), false
            )
            return 1
        }

        val home: HomeObject? = player.getHome(homeName)
        if (home == null) {
            context.source.sendFeedback(TranslatableText("text.homabric.no_home").formatted(Formatting.RED), false)
            return 0
        }

        TeleportHelper.runTeleport(context.source.player, fun() {
            home.teleportPlayer(context.source.player)
            context.source.sendFeedback(
                TranslatableText(
                    "text.homabric.teleport_done", LiteralText(homeName)
                        .formatted(Formatting.WHITE)
                )
                    .formatted(Formatting.GREEN), false
            )
        })
        return 1
    }

    @Throws(CommandSyntaxException::class)
    fun set(context: CommandContext<ServerCommandSource>): Int {
        if (!context.source.entity!!.isPlayer) return 0
        var homeName: String? = "home"
        try {
            if (context.getArgument("home", String::class.java) != null)
                homeName = context.getArgument("home", String::class.java)
        } catch (_: Exception) {}
        val player = HomesConfig.getPlayer(context.getArgument("player", String::class.java))
        if (player == null) {
            context.source.sendFeedback(
                TranslatableText("text.homabric.no_player_exists").formatted(Formatting.RED), false
            )
            return 1
        }
        val result: PlayerObject.HomeCreationResult = player.createOrUpdateHome(context.source, homeName!!)
        if (result === PlayerObject.HomeCreationResult.HOME_CREATED) {
            context.source.sendFeedback(
                TranslatableText(
                    "text.homabric.admin_new_home_created", LiteralText(homeName).formatted(
                        Formatting.WHITE
                    ), context.getArgument("player", String::class.java).format(Formatting.AQUA)
                ).formatted(Formatting.GREEN), false
            )
        } else {
            context.source.sendFeedback(
                TranslatableText(
                    "text.homabric.admin_home_location_updated", LiteralText(homeName).formatted(
                        Formatting.WHITE
                    ), context.getArgument("player", String::class.java).format(Formatting.AQUA)
                ).formatted(Formatting.GREEN), false
            )
        }
        Homabric.reloadConfig()
        return 1
    }

    @Throws(CommandSyntaxException::class)
    fun remove(context: CommandContext<ServerCommandSource>): Int {
        if (!context.source.entity!!.isPlayer) {
            return 0
        }
        val homeName = context.getArgument("home", String::class.java)
        val player = HomesConfig.getPlayer(
            context.getArgument(
                "player",
                String::class.java
            )
        )
        if (player == null) {
            context.source.sendFeedback(
                TranslatableText("text.homabric.no_player_exists").formatted(Formatting.RED),
                false
            )
            return 1
        }
        val result: PlayerObject.HomeRemoveResult = player.removeHome(homeName)
        when (result) {
            PlayerObject.HomeRemoveResult.NO_HOME -> {
                context.source.sendFeedback(TranslatableText("text.homabric.no_home").formatted(Formatting.RED), false)
            }
            PlayerObject.HomeRemoveResult.HOME_REMOVED -> {
                context.source.sendFeedback(
                    TranslatableText(
                        "text.homabric.admin_home_removed", LiteralText(homeName).formatted(
                            Formatting.WHITE
                        ), context.getArgument("player", String::class.java).format(Formatting.AQUA)
                    ).formatted(Formatting.GREEN), false
                )
            }
        }
        return 1
    }

    @Throws(CommandSyntaxException::class)
    fun list(context: CommandContext<ServerCommandSource>): Int {
        val playerData = HomesConfig.getPlayer(context.getArgument("player", String::class.java))
        if (playerData == null) {
            context.source.sendFeedback(TranslatableText("text.homabric.no_homes").formatted(Formatting.YELLOW), false)
            return 1
        }
        val gui = playerData.getHomesGUI(context.source)
        gui.open()
        return 1
    }
}