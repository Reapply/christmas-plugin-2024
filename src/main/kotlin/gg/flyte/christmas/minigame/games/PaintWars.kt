package gg.flyte.christmas.minigame.games

import com.google.common.collect.HashBiMap
import gg.flyte.christmas.minigame.engine.EventMiniGame
import gg.flyte.christmas.minigame.engine.GameConfig
import gg.flyte.christmas.util.eventController
import gg.flyte.twilight.event.event
import gg.flyte.twilight.scheduler.repeatingTask
import gg.flyte.twilight.time.TimeUnit
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.random.Random

class PaintWars : EventMiniGame(GameConfig.PAINT_WARS) {
    private var gameTime = 90
    private val paintMaterials = mutableListOf(
        Material.RED_WOOL,
        Material.ORANGE_WOOL,
        Material.YELLOW_WOOL,
        Material.LIME_WOOL,
        Material.GREEN_WOOL,
        Material.LIGHT_BLUE_WOOL,
        Material.CYAN_WOOL,
        Material.BLUE_WOOL,
        Material.PURPLE_WOOL,
        Material.PINK_WOOL,
        Material.MAGENTA_WOOL,
        Material.LIGHT_GRAY_WOOL,
        Material.GRAY_WOOL,
        Material.BROWN_WOOL,
        Material.BLACK_WOOL,
        Material.WHITE_WOOL,
        Material.RED_CONCRETE,
        Material.ORANGE_CONCRETE,
        Material.YELLOW_CONCRETE,
        Material.LIME_CONCRETE,
        Material.GREEN_CONCRETE,
        Material.LIGHT_BLUE_CONCRETE,
        Material.CYAN_CONCRETE,
        Material.BLUE_CONCRETE,
        Material.PURPLE_CONCRETE,
        Material.PINK_CONCRETE,
        Material.MAGENTA_CONCRETE,
        Material.LIGHT_GRAY_CONCRETE,
        Material.GRAY_CONCRETE,
        Material.BROWN_CONCRETE,
        Material.BLACK_CONCRETE,
        Material.WHITE_CONCRETE,
        Material.RED_STAINED_GLASS,
        Material.ORANGE_STAINED_GLASS,
        Material.YELLOW_STAINED_GLASS,
        Material.LIME_STAINED_GLASS,
        Material.GREEN_STAINED_GLASS,
        Material.LIGHT_BLUE_STAINED_GLASS,
        Material.CYAN_STAINED_GLASS,
        Material.BLUE_STAINED_GLASS,
        Material.PURPLE_STAINED_GLASS,
        Material.PINK_STAINED_GLASS,
        Material.MAGENTA_STAINED_GLASS,
        Material.LIGHT_GRAY_STAINED_GLASS,
        Material.GRAY_STAINED_GLASS,
        Material.BROWN_STAINED_GLASS,
        Material.BLACK_STAINED_GLASS,
        Material.WHITE_STAINED_GLASS,
        Material.RED_GLAZED_TERRACOTTA,
        Material.ORANGE_GLAZED_TERRACOTTA,
        Material.YELLOW_GLAZED_TERRACOTTA,
        Material.LIME_GLAZED_TERRACOTTA,
        Material.GREEN_GLAZED_TERRACOTTA,
        Material.LIGHT_BLUE_GLAZED_TERRACOTTA,
        Material.CYAN_GLAZED_TERRACOTTA,
        Material.BLUE_GLAZED_TERRACOTTA,
        Material.PURPLE_GLAZED_TERRACOTTA,
        Material.PINK_GLAZED_TERRACOTTA,
        Material.MAGENTA_GLAZED_TERRACOTTA,
        Material.LIGHT_GRAY_GLAZED_TERRACOTTA,
        Material.GRAY_GLAZED_TERRACOTTA,
        Material.BROWN_GLAZED_TERRACOTTA,
        Material.BLACK_GLAZED_TERRACOTTA,
        Material.WHITE_GLAZED_TERRACOTTA,
        Material.RED_SHULKER_BOX,
        Material.ORANGE_SHULKER_BOX,
        Material.YELLOW_SHULKER_BOX,
        Material.LIME_SHULKER_BOX,
        Material.GREEN_SHULKER_BOX,
        Material.LIGHT_BLUE_SHULKER_BOX,
        Material.CYAN_SHULKER_BOX,
        Material.BLUE_SHULKER_BOX,
        Material.PURPLE_SHULKER_BOX,
        Material.PINK_SHULKER_BOX,
        Material.MAGENTA_SHULKER_BOX,
        Material.LIGHT_GRAY_SHULKER_BOX,
        Material.GRAY_SHULKER_BOX,
        Material.BROWN_SHULKER_BOX,
        Material.BLACK_SHULKER_BOX,
        Material.WHITE_SHULKER_BOX
    ) // only *really* supports 79 players...
    private val playerBrushesBiMap = HashBiMap.create<UUID, Material>()
    private val changedBlocks = mutableListOf<Block>()
    private val scores = mutableMapOf<UUID, Int>()
    private var hasEnded = false

    override fun preparePlayer(player: Player) {
        player.gameMode = GameMode.ADVENTURE
        player.inventory.clear()
        player.teleport(gameConfig.spawnPoints.random().randomLocation())

        ItemStack(Material.BRUSH).apply {
            itemMeta = itemMeta.apply {
                displayName(text("Paint Brush", gameConfig.colour, TextDecoration.BOLD))
                lore(listOf(text("Use this to paint the map!", NamedTextColor.GRAY)))
            }
        }.apply { player.inventory.setItem(0, this) }

        // assign a unique paint brush to the player
        if (paintMaterials.isEmpty()) { // edge case
            var randomMaterial = Material.entries.toTypedArray().filter { it.isBlock }.random()
            while (playerBrushesBiMap.inverse().containsKey(randomMaterial)) {
                randomMaterial = Material.entries.toTypedArray().random()
            }
        } else {
            paintMaterials.random()
                .also { playerBrushesBiMap[player.uniqueId] = it }
                .also { paintMaterials.remove(it) }
                .also { player.inventory.setItem(1, ItemStack(it)) }
        }
    }

    override fun startGame() {
        eventController().sidebarManager.dataSupplier = scores

        tasks += repeatingTask(1, TimeUnit.SECONDS) {
            gameTime--

            if (gameTime == 0) {
                cancel()
                endGame()
            }

            updateScoreboard()
        }
    }

    override fun eliminate(player: Player, reason: EliminationReason) {
        // super.eliminate(player, reason) | Note: this game does not eliminate players, remove call to super
    }

    override fun endGame() {
        hasEnded = true

        changedBlocks.forEach { it.type = Material.AIR }
        playerBrushesBiMap.clear()
        changedBlocks.clear()
        for (entry in scores) eventController().addPoints(entry.key, entry.value)

        scores.entries
            .sortedByDescending { it.value }
            .take(1)
            .also { it.forEach { formattedWinners.put(it.key, it.value.toString() + " blocks") } }

        super.endGame()
    }

    private fun updateScoreboard() {
        val timeComponent = Component.text("ᴛɪᴍᴇ ʟᴇғᴛ: ", NamedTextColor.AQUA)
            .append(text(gameTime.toString(), NamedTextColor.RED, TextDecoration.BOLD))

        Bukkit.getOnlinePlayers().forEach { eventController().sidebarManager.updateLines(it, listOf(Component.empty(), timeComponent)) }
    }

    fun updateBlock(block: Block, player: Player, overrideRandom: Boolean) {
        if (!overrideRandom && Random.nextDouble() < 0.25) return // 75% chance to paint block
        if (block.type == Material.AIR) return
        if (block.type == playerBrushesBiMap[player.uniqueId]) return // block already painted by same player

        // if the block has no contact with any air blocks, don't paint it (it's surrounded by other blocks)
        var canChange = false
        BlockFace.entries.forEach {
            if (block.getRelative(it).type == Material.AIR) {
                canChange = true
                return@forEach
            }
        }
        if (!canChange) return

        // decrement the score of previous painter:
        playerBrushesBiMap.inverse()[block.type]?.let { previousPlayerId ->
            scores[previousPlayerId]?.let { scores[previousPlayerId] = it - 1 }
        }

        block.type = playerBrushesBiMap[player.uniqueId]!!

        // increment the score of current painter:
        scores[player.uniqueId] = scores.getOrDefault(player.uniqueId, 0) + 1
        if (!(changedBlocks.contains(block))) changedBlocks.add(block)
    }

    override fun onPlayerJoin(player: Player) {
        super.onPlayerJoin(player)
        preparePlayer(player)
    }

    override fun handleGameEvents() {
        event<PlayerInteractEvent> {
            if (hasEnded) return@event

            if (!hasItem()) return@event
            if (item!!.type != Material.BRUSH) return@event
            if (!(action.name.lowercase().contains("right"))) return@event

            player.launchProjectile(Snowball::class.java).apply {
                item = ItemStack(playerBrushesBiMap[player.uniqueId]!!)
            }
        }

        event<InventoryOpenEvent> {
            if (inventory.type == InventoryType.SHULKER_BOX) isCancelled = true
        }

        event<ProjectileHitEvent> {
            if (hasEnded) return@event

            if (hitBlock == null) return@event
            // if hit block was white wool, or any paintable block
            if (!(hitBlock!!.type == Material.WHITE_WOOL || playerBrushesBiMap.inverse().containsKey(hitBlock!!.type))) return@event

            hitBlock!!.world.playSound(hitBlock!!.location, Sound.ENTITY_PLAYER_SPLASH, 0.5F, 1.0f)
            updateBlock(hitBlock!!, entity.shooter as Player, false)

            var hitBlockLocation = hitBlock!!.location
            for (x in -1..1) {
                for (y in -1..1) {
                    for (z in -1..1) {
                        updateBlock(
                            hitBlock!!.world.getBlockAt(
                                Location(
                                    hitBlock!!.world,
                                    hitBlockLocation.x + x,
                                    hitBlockLocation.y + y,
                                    hitBlockLocation.z + z
                                )
                            ),
                            entity.shooter as Player,
                            false,
                        )
                    }
                }
            }
        }

        event<InventoryClickEvent> { isCancelled = true }
    }
}