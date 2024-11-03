package gg.flyte.christmas.commands

import dev.shreyasayyengar.menuapi.menu.MenuItem
import dev.shreyasayyengar.menuapi.menu.StandardMenu
import gg.flyte.christmas.ChristmasEventPlugin
import gg.flyte.christmas.minigame.engine.GameConfig
import gg.flyte.christmas.util.colourise
import gg.flyte.christmas.util.toLegacyString
import gg.flyte.twilight.extension.playSound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.util.UUID

@Suppress("unused") // power of lamp!
@Command("event")
class EventCommand(val menu: StandardMenu = StandardMenu("&c☃ Event Menu!".colourise(), 54)) {
    private val availableGames = GameConfig.entries
    private var selectedIndex = -1
    private var modifyingGame: UUID? = null

    init {
        menu.setItem(13, setGameSwitcher())

        menu.setItem(21, MenuItem(Material.PAINTING)
            .setName("&cTake a Screenie!".colourise())
            .setEnchantmentGlint(true)
            .closeWhenClicked(true)
            .onClick { whoClicked, itemStack, clickType, inventoryClickEvent ->
                // TODO!
            }
        )
        menu.setItem(23, MenuItem(Material.ENDER_PEARL)
            .setName("&cTeleport Everyone to Me!".colourise())
            .closeWhenClicked(true)
            .onClick { whoClicked, itemStack, clickType, inventoryClickEvent ->
                Bukkit.getOnlinePlayers().forEach { it.teleport(whoClicked) }
                whoClicked.sendMessage(Component.text("Teleported all players to you!", NamedTextColor.GREEN))
                whoClicked.playSound(Sound.ENTITY_ENDERMAN_TELEPORT)
            }
        )

        menu.setItem(42, MenuItem(Material.RED_CONCRETE)
            .setName(
                "&cKill Current Game: " + (ChristmasEventPlugin.instance.eventController.currentGame?.gameConfig?.displayName
                    ?: "None".colourise())
            )
            .setLore(
                "",
                "&cThis will force quit the current game".colourise(),
                "&cand teleport all players back to the lobby.".colourise(),
            )
            .onClick { whoClicked, itemStack, clickType, inventoryClickEvent ->
                if (ChristmasEventPlugin.instance.eventController.currentGame == null) {
                    whoClicked.playSound(Sound.ENTITY_VILLAGER_NO)
                    whoClicked.sendMessage(Component.text("No game is currently running!", NamedTextColor.RED))
                    return@onClick
                }

                ChristmasEventPlugin.instance.eventController.currentGame!!.endGame()
                whoClicked.sendMessage(Component.text("Game terminated!", NamedTextColor.RED))
                whoClicked.playSound(Sound.ENTITY_GENERIC_EXPLODE)
                ChristmasEventPlugin.instance.eventController.sidebarManager.update()
            }
        )

        menu.onClose { whoClosed, inventory, event ->
            if (whoClosed.uniqueId != modifyingGame) return@onClose // not the one who interacted with the game switcher

            modifyingGame = null
            ChristmasEventPlugin.instance.eventController.setMiniGame(availableGames[selectedIndex])
            ChristmasEventPlugin.instance.eventController.sidebarManager.update()
            whoClosed.sendMessage(
                Component.text("Selected game: ", NamedTextColor.GRAY)
                    .append(availableGames[selectedIndex].displayName.color(availableGames[selectedIndex].colour))
            )
            whoClosed.playSound(Sound.UI_BUTTON_CLICK)
        }
    }

    @CommandPermission("event.panel")
    fun handleCommand(sender: Player) {
        menu.open(true, sender)
    }

    @Subcommand("optout")
    @CommandPermission("event.optout")
    fun optOut(sender: Player) {
        var remove = ChristmasEventPlugin.instance.eventController.optOut.remove(sender.uniqueId)
        if (remove) {
            sender.sendMessage(Component.text("You have opted back into the event!", NamedTextColor.GREEN))
        } else {
            ChristmasEventPlugin.instance.eventController.optOut.add(sender.uniqueId)
            sender.sendMessage(Component.text("You have opted out of the event!", NamedTextColor.RED))
        }

        sender.playSound(Sound.UI_BUTTON_CLICK)
    }

    @Subcommand("DANGER-load-crash")
    @CommandPermission("event.loadcrash")
    fun loadCrash() {
        println("load crash called!")
    }

    private fun setGameSwitcher(): MenuItem {
        val menuItem = MenuItem(Material.STRUCTURE_VOID).apply {
            setName("&2&lSelect Game:".colourise())
            updateRotatingItem(this) // initial lore setup
            onClick { whoClicked, itemStack, clickType, inventoryClickEvent ->
                inventoryClickEvent.isCancelled = true

                if (modifyingGame != null && modifyingGame != whoClicked.uniqueId) {
                    whoClicked.closeInventory()
                    whoClicked.sendMessage(Component.text("Someone else is currently modifying the game!", NamedTextColor.RED))
                    whoClicked.playSound(Sound.ENTITY_VILLAGER_NO)
                    return@onClick
                }

                modifyingGame = whoClicked.uniqueId
                selectedIndex = (selectedIndex + 1) % availableGames.size // cycle around
                updateRotatingItem(this)
                this.itemStack.type = availableGames[selectedIndex].menuMaterial

                menu.setItem(13, this)
                whoClicked.playSound(Sound.UI_BUTTON_CLICK)
            }
        }

        return menuItem
    }

    private fun updateRotatingItem(menuItem: MenuItem) {
        val lore: MutableList<String> = mutableListOf()

        for (index in availableGames.indices) {
            val game = availableGames[index]
            val loreLine = if (index == selectedIndex) {
                "&c&l> ${PlainTextComponentSerializer.plainText().serialize(game.displayName)}".colourise()
            } else {
                "&7${PlainTextComponentSerializer.plainText().serialize(game.displayName)}".colourise()
            }

            lore.add(loreLine)
        }

        menuItem.setLore(lore)

        if (selectedIndex == -1) return

        menu.setItem(38, MenuItem(Material.LIME_CONCRETE)
            .setName(availableGames[selectedIndex].displayName.toLegacyString().colourise())
            .setLore(
                "",
                "&aThis will begin the countdown".colourise(),
                "&cimmediately &aand prepare the players".colourise(),
                "",
                "&eIf you do not want to start".colourise(),
                "&eyet, simply exit this menu. ".colourise(),
                "&eThe game has already been set.".colourise()
            )
            .onClick { whoClicked, itemStack, clickType, inventoryClickEvent ->
                whoClicked.closeInventory()
                if (ChristmasEventPlugin.instance.eventController.currentGame == null) {
                    whoClicked.playSound(Sound.ENTITY_VILLAGER_NO)
                    whoClicked.sendMessage(Component.text("No game is currently selected!", NamedTextColor.RED))
                    return@onClick
                }

                ChristmasEventPlugin.instance.eventController.prepareStart()
                whoClicked.playSound(Sound.ENTITY_PLAYER_LEVELUP)
                whoClicked.sendMessage(Component.text("Game starting! Please wait...", NamedTextColor.GREEN))

                selectedIndex = -1
                menu.setItem(13, setGameSwitcher())
                menu.removeItem(38)
            }
        )
    }
}
