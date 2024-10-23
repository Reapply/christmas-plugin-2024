package host.carbon.event.minigame.games

import dev.shreyasayyengar.menuapi.menu.MenuItem
import gg.flyte.twilight.event.event
import gg.flyte.twilight.extension.playSound
import gg.flyte.twilight.extension.removeActivePotionEffects
import gg.flyte.twilight.scheduler.TwilightRunnable
import gg.flyte.twilight.scheduler.delay
import gg.flyte.twilight.scheduler.repeatingTask
import gg.flyte.twilight.time.TimeUnit
import host.carbon.event.minigame.engine.EventMiniGame
import host.carbon.event.minigame.engine.GameConfig
import host.carbon.event.minigame.world.MapRegion
import host.carbon.event.minigame.world.MapSinglePoint
import host.carbon.event.util.SongReference
import host.carbon.event.util.Util
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Firework
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import kotlin.math.ceil
import kotlin.random.Random

class BlockParty() : EventMiniGame(GameConfig.BLOCK_PARTY) {
    private lateinit var overviewTask: TwilightRunnable

    private val materials = mapOf(
        Material.WHITE_CONCRETE to NamedTextColor.WHITE,
        Material.ORANGE_CONCRETE to NamedTextColor.GOLD,
        Material.MAGENTA_CONCRETE to NamedTextColor.DARK_PURPLE,
        Material.LIGHT_BLUE_CONCRETE to NamedTextColor.AQUA,
        Material.YELLOW_CONCRETE to NamedTextColor.YELLOW,
        Material.LIME_CONCRETE to NamedTextColor.GREEN,
        Material.PINK_CONCRETE to NamedTextColor.LIGHT_PURPLE,
        Material.GRAY_CONCRETE to NamedTextColor.DARK_GRAY,
        Material.LIGHT_GRAY_CONCRETE to NamedTextColor.GRAY,
        Material.CYAN_CONCRETE to NamedTextColor.DARK_AQUA,
        Material.PURPLE_CONCRETE to NamedTextColor.DARK_PURPLE,
        Material.BLUE_CONCRETE to NamedTextColor.BLUE,
        Material.BROWN_CONCRETE to NamedTextColor.GOLD,
        Material.GREEN_CONCRETE to NamedTextColor.GREEN,
        Material.RED_CONCRETE to NamedTextColor.RED,
        Material.BLACK_CONCRETE to NamedTextColor.BLACK
    )
    private lateinit var selectedMaterial: Material
    private val groupedSquares = mutableListOf<MapRegion>()
    private val eliminateBelow = 104
    private var roundNumber = 0
    private var harder = false;
    private var powerUpLocation: Location? = null
    private var secondsForRound = 10 // First round 9 seconds to find safe squares (see line 115)
    private var safeBlocks = mutableListOf<MapSinglePoint>()
    private var bombedSquares = mutableListOf<MapSinglePoint>()
    private var currentBossBar: BossBar? = null
    private var bossBarTask: TwilightRunnable? = null
    private var gameLogicTask: TwilightRunnable? = null
    private var isCountdownActive = false

    override fun startGameOverview() {
        super.startGameOverview()

        for (x in 600..632 step 3) {
            for (z in 784..816 step 3) {
                val region = MapRegion(MapSinglePoint(x, 110, z), MapSinglePoint(x + 2, 110, z + 2))
                groupedSquares.add(region)
            }
        }

        overviewTask = repeatingTask(10) {
            groupedSquares.forEach { region ->
                val material = materials.keys.random()
                region.toSingleBlockLocations().forEach { point ->
                    point.block.type = material
                }
            }
        }
    }

    override fun preparePlayer(player: Player) {
        player.gameMode = GameMode.ADVENTURE
        player.teleport(gameConfig.spawnPoints.random().randomLocation())
    }

    override fun startGame() {
        overviewTask.cancel()

        simpleCountdown {
            groupedSquares.forEach { region ->
                val material = materials.keys.random()
                region.toSingleBlockLocations().forEach { point ->
                    point.block.type = material
                }
            }

            newRound()
        }
    }

    private fun newRound() {
        roundNumber++
        if (secondsForRound > 2) secondsForRound--

        if (roundNumber == 12) {
            if (!harder) {
                harder = true
                roundNumber = 8 // hard round needs more time to find safe squares first.
                Util.handlePlayers(
                    // TODO add info explaining what the actual change is
                    eventPlayerAction = {
                        it.showTitle(Title.title(Component.text("Hard Mode!", gameConfig.colour), Component.text("")))
                        it.playSound(Sound.ENTITY_ENDER_DRAGON_GROWL)
                    },
                    optedOutAction = {
                        it.sendMessage(Component.text("The game is getting harder!").color(NamedTextColor.RED).decorate(TextDecoration.BOLD))
                        it.playSound(Sound.ENTITY_ENDER_DRAGON_GROWL)
                    }
                )
            }
        }

        // let song play for a few rounds
        if (roundNumber % 3 == 0) {
            eventController.startPlaylist(SongReference.ALL_I_WANT_FOR_CHRISTMAS_IS_YOU) // beginning makes it hard to differentiate when it has stopped.
        } else {
            eventController.songPlayer?.isPlaying = true
        }
        newFloor()
        powerUp()

        val delayBeforePrepareRemoveFloor = (6..10).random()
        delay(delayBeforePrepareRemoveFloor, TimeUnit.SECONDS) {
            if (!isCountdownActive) {
                prepareRemoveFloor()
            }
        }

        // TODO increment score system here
    }

    private fun newFloor(clearBombs: Boolean = true) {
        safeBlocks.clear()
        if (clearBombs) bombedSquares.clear()

        this.selectedMaterial = materials.keys.random()
        val safeSquare1 = groupedSquares.indices.random()
        val safeSquare2 = groupedSquares.indices.random()

        groupedSquares.forEachIndexed { index, groupedSquareRegion ->
            val mat: Material = if (index == safeSquare1 || index == safeSquare2) selectedMaterial else materials.keys.random()
            var blockLocations = groupedSquareRegion.toSingleBlockLocations()

            if (mat == selectedMaterial) safeBlocks.addAll(blockLocations)

            blockLocations.forEach { it.block.type = mat }
        }

        bombedSquares.forEach { it.block.type = selectedMaterial }

        Util.handlePlayers(
            eventPlayerAction = {
                it.playSound(it.location, Sound.BLOCK_BEACON_ACTIVATE, 1F, 5F)
                for (itemStack in it.inventory.storageContents) {
                    if (itemStack?.type == selectedMaterial) itemStack.type = Material.AIR // ensure no power-up items are removed
                }
            },
            optedOutAction = {
                it.playSound(Sound.BLOCK_BEACON_ACTIVATE)
            }
        )
    }

    private fun powerUp() {
        var reducedFrequency = remainingPlayers().size < 4 && roundNumber % 4 == 0 // 4 remaining -> every 4th round
        var regularPowerUp = remainingPlayers().size > 4 && roundNumber % 2 == 0 // 5+ remaining -> every 2nd round

        if (reducedFrequency || regularPowerUp) {

            val announcePowerUp: (Player) -> Unit = { player ->
                {
                    player.sendMessage(
                        Component.text("A power-up has spawned on the floor!").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                    )
                    player.sendMessage(Component.text("Find the beacon on the map to unlock it!", NamedTextColor.GRAY))
                    player.playSound(Sound.BLOCK_NOTE_BLOCK_PLING)
                }
            }
            Util.handlePlayers(eventPlayerAction = announcePowerUp, optedOutAction = announcePowerUp)

            val localLocation = groupedSquares.random()
            powerUpLocation = localLocation.randomLocation().apply { add(0.0, 1.0, 0.0) }
            powerUpLocation!!.block.type = Material.BEACON
            powerUpLocation!!.world.spawn(powerUpLocation!!, Firework::class.java) { firework ->
                {
                    firework.fireworkMeta = firework.fireworkMeta.apply {
                        addEffect(
                            FireworkEffect.builder()
                                .with(FireworkEffect.Type.BALL_LARGE)
                                .withColor(Color.FUCHSIA, Color.PURPLE, Color.MAROON).withFade(Color.FUCHSIA, Color.PURPLE, Color.MAROON).build()
                        )
                    }
                    firework.detonate()
                }
            }

            val notification =
                Component.text(">> A mysterious power-up has spawned on the floor! <<").color(gameConfig.colour).decorate(TextDecoration.BOLD)

            Util.handlePlayers(
                eventPlayerAction = {
                    it.sendMessage(Component.text())
                    it.sendMessage(notification)
                    it.sendMessage(Component.text("Find the beacon on the map to unlock it!", NamedTextColor.GRAY))
                },
                optedOutAction = {
                    it.sendMessage(Component.text())
                    it.sendMessage(notification)
                }
            )
        }
    }

    private fun prepareRemoveFloor() {
        isCountdownActive = true

        if (harder) newFloor(false)
        eventController.songPlayer?.isPlaying = false
        remainingPlayers().forEach { it.playSound(Sound.BLOCK_NOTE_BLOCK_BASEDRUM) }

        var itemStack = MenuItem(ItemStack(selectedMaterial)).itemStack
        itemStack.itemMeta = itemStack.itemMeta.apply {
            isHideTooltip = true
        }

        val timerBar: BossBar = BossBar.bossBar(
            Component.text("Time left: $secondsForRound", gameConfig.colour).decorate(TextDecoration.BOLD),
            1.0f,
            BossBar.Color.GREEN,
            BossBar.Overlay.NOTCHED_20
        )

        currentBossBar = timerBar

        remainingPlayers().forEach {
            for ((index, stack) in it.inventory.storageContents.withIndex()) {
                if (stack == null) it.inventory.setItem(index, itemStack)
            }
        } // only remaining players get items
        Util.handlePlayers(
            eventPlayerAction = { it.showBossBar(timerBar) },
            optedOutAction = { it.showBossBar(timerBar) } // all can see ticker
        )

        val totalTicks = secondsForRound * 20
        var remainingTicks = totalTicks

        // game logic timer
        gameLogicTask = repeatingTask(5, 5) {
            if (remainingTicks <= 0) {
                this.cancel()
                gameLogicTask = null

                powerUpLocation?.block?.type = Material.AIR
                powerUpLocation = null

                for (groupSquares in groupedSquares) {
                    for (loc in groupSquares.toSingleBlockLocations()) {
                        if (!(safeBlocks.contains(loc))) {
                            loc.block.type = Material.AIR
                        }
                    }
                }

                remainingPlayers().forEach { player ->
                    player.inventory.remove(selectedMaterial)
                    player.inventory.setItemInOffHand(null)
                    player.playSound(Sound.ENTITY_EVOKER_FANGS_ATTACK)
                }

                isCountdownActive = false

                tasks += delay(80) { newRound() }
            } else {
                remainingPlayers().forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 1.0f) }
            }
        }

        // BossBar ticker
        bossBarTask = repeatingTask(1, 1) {
            if (remainingTicks <= 0) {
                this.cancel()
                bossBarTask = null
                Util.handlePlayers(eventPlayerAction = { it.hideBossBar(timerBar) }, optedOutAction = { it.hideBossBar(timerBar) })
                currentBossBar = null
            } else {
                val progress = remainingTicks.toDouble() / totalTicks
                timerBar.progress(progress.toFloat())

                val secondsRemaining = ceil(remainingTicks / 20.0).toInt()
                timerBar.name(Component.text("Time left: $secondsRemaining", gameConfig.colour).decorate(TextDecoration.BOLD))
                remainingTicks--
            }
        }

        tasks += bossBarTask
        tasks += gameLogicTask
    }

    override fun eliminate(player: Player, reason: EliminationReason) {
        super.eliminate(player, reason)
        if (currentBossBar != null) player.hideBossBar(currentBossBar!!)

        Util.handlePlayers(
            eventPlayerAction = {
                val eliminatedMessage = player.displayName().color(NamedTextColor.RED)
                    .append(Component.text(" has been eliminated!").color(NamedTextColor.GRAY))
                it.sendMessage(eliminatedMessage)
            },
            optedOutAction = {
                val eliminatedMessage = player.displayName().color(NamedTextColor.RED)
                    .append(Component.text(" has been eliminated!").color(NamedTextColor.GRAY))
                it.sendMessage(eliminatedMessage)
            }
        )

        player.apply {
            inventory.storageContents = arrayOf()
            inventory.setItemInOffHand(null)
            removeActivePotionEffects()

            world.strikeLightning(location)

            if (reason == EliminationReason.ELIMINATED) {

                val itemDisplay = world.spawn(location, ItemDisplay::class.java) {
                    it.setItemStack(ItemStack(Material.AIR))
                    it.teleportDuration = 59 // max (minecraft limitation)
                }

                addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 20 * 4, 1, false, false, false))
                playSound(Sound.ENTITY_PLAYER_HURT)

                delay(1) {
                    val randomSpecLocation = gameConfig.spectatorSpawnLocations.random()
                    itemDisplay.teleport(randomSpecLocation)
                    itemDisplay.addPassenger(player)

                    delay(59) {
                        itemDisplay.remove()
                        player.teleport(randomSpecLocation)
                    }
                }
            }
        }

        if (remainingPlayers().size == 1) endGame()
    }

    override fun endGame() {
        super.endGame()
        Util.handlePlayers(eventPlayerAction = { it.gameMode = GameMode.SURVIVAL })
    }

    override fun onPlayerJoin(player: Player) {
        super.onPlayerJoin(player)
    }

    override fun handleGameEvents() {
        listeners += event<PlayerDropItemEvent> { isCancelled = true }

        listeners += event<InventoryClickEvent> { isCancelled = true }

        listeners += event<PlayerMoveEvent> {
            if (player.location.blockY < eliminateBelow) {
                if (eliminatedPlayers.contains(player.uniqueId)) return@event
                eliminate(player, EliminationReason.ELIMINATED)
            }
        }

        listeners += event<PlayerInteractEvent> {
            if (clickedBlock?.type == Material.BEACON) {
                clickedBlock?.type = Material.AIR
                var randomPowerUp = PowerUp.COLOR_BOMB

                player.sendMessage(
                    Component.text("You've found a ${randomPowerUp.displayName} power-up!")
                        .color(NamedTextColor.GREEN)
                        .decorate(TextDecoration.BOLD)
                )

                Util.handlePlayers(
                    eventPlayerAction = {
                        if (it != player) {
                            it.sendMessage(
                                Component.text(">> ${player.displayName()} has found a {${randomPowerUp.displayName} power-up! <<")
                                    .color(NamedTextColor.GREEN)
                                    .decorate(TextDecoration.BOLD)
                            )
                        }
                    },
                    optedOutAction = {
                        it.sendMessage(
                            Component.text(">> ${player.displayName()} has found a {${randomPowerUp.displayName} power-up! <<")
                                .color(NamedTextColor.GREEN)
                                .decorate(TextDecoration.BOLD)
                        )
                    }
                )

                when (randomPowerUp) {
                    PowerUp.ENDER_PEARL -> player.inventory.setItem(0, ItemStack(Material.ENDER_PEARL, 1))

                    PowerUp.COLOR_BOMB -> {
                        // TODO if newFloor is called, make sure these blocks have changed colour depending on the selectedMaterial
                        val x = clickedBlock!!.location.blockX
                        val y = clickedBlock!!.location.blockY
                        val z = clickedBlock!!.location.blockZ

                        // First loop: 5x5 randomness
                        for (i in (x - 3) until (x + 3)) {
                            for (k in (z - 3) until (z + 3)) {
                                if (Random.nextBoolean()) {
                                    if (!(i in 600..632 && k in 784..816)) return@event // bomb outside of map

                                    val block = clickedBlock!!.world.getBlockAt(i, y - 1, k)
                                    if (block.type != Material.AIR) {
                                        block.type = selectedMaterial
                                        safeBlocks.add(MapSinglePoint(i, y - 1, k))

                                        bombedSquares.add(MapSinglePoint(i, y - 1, k))
                                    }
                                }
                            }
                        }

                        // Second loop: central area
                        for (i in (x - 1) until (x + 1)) {
                            for (k in (z - 1) until (z + 1)) {
                                if (!(i in 600..632 && k in 784..816)) return@event // bomb outside of map

                                val block = clickedBlock!!.world.getBlockAt(i, y - 1, k)
                                if (block.type != Material.AIR) {
                                    block.type = selectedMaterial
                                    safeBlocks.add(MapSinglePoint(i, y - 1, k))

                                    bombedSquares.add(MapSinglePoint(i, y - 1, k))
                                }
                            }
                        }

                        clickedBlock!!.world.playSound(clickedBlock!!.location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f)
                    }

                    PowerUp.JUMP_BOOST -> player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 8, 3, false, false, false))

                    PowerUp.FISHING_ROD -> player.inventory.setItem(0, ItemStack(Material.FISHING_ROD, 1))

                    PowerUp.SLOWNESS -> player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 20 * 10, 2, false, false, false))

                    PowerUp.BLINDNESS -> player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 20 * 10, 2, false, false, false))

                    PowerUp.RANDOM_TP -> player.teleport(groupedSquares.random().randomLocation().add(0.0, 1.5, 0.0))

                    PowerUp.PUSH_SELF -> player.velocity = player.location.direction.multiply(2).add(Vector(0.0, 1.5, 0.0))

                    PowerUp.PUSH_RANDOM -> {
                        remainingPlayers().random().apply {
                            velocity = this.location.direction.multiply(2).add(Vector(0.0, 1.5, 0.0))
                            sendMessage(Component.text("You've been pushed by a power-up!").color(gameConfig.colour))
                        }
                    }
                }
            }
        }
    }

    private enum class PowerUp(
        val displayName: String,
    ) {
        ENDER_PEARL("Ender Pearl"),
        COLOR_BOMB("Color Bomb"),
        JUMP_BOOST("Jump Boost"),
        FISHING_ROD("Fishing Rod"),
        SLOWNESS("Slowness"),
        BLINDNESS("Blindness"),
        RANDOM_TP("Random TP"),
        PUSH_SELF("Dangerous Self-Boost"),
        PUSH_RANDOM("Dangerous Random-Boost")
    }
}