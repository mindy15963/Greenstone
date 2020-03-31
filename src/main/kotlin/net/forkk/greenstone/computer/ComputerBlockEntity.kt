package net.forkk.greenstone.computer

import com.github.h0tk3y.betterParse.parser.ParseException
import drawer.getFrom
import drawer.put
import io.netty.buffer.Unpooled
import java.util.function.Supplier
import kotlinx.serialization.Serializable
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry
import net.forkk.greenstone.Greenstone
import net.forkk.greenstone.grpl.Context
import net.forkk.greenstone.grpl.ExecError
import net.forkk.greenstone.grpl.GrplIO
import net.forkk.greenstone.grpl.parse
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.PacketByteBuf
import net.minecraft.util.math.BlockPos

@Serializable
data class ComputerData(var context: Context, var logs: String = "")

class ComputerBlockEntity : BlockEntity(TYPE) {
    companion object {
        val TYPE: BlockEntityType<ComputerBlockEntity> = // Is this correct? idk, I copied it from Stockpile
            BlockEntityType.Builder.create(
                Supplier { ComputerBlockEntity() },
                ComputerBlock()
            ).build(null)

        /**
         * Keeps track of who has what terminal open.
         */
        private val openTerminalMap = hashMapOf<PlayerEntity, ComputerBlockEntity>()

        fun handleGuiOpen(player: PlayerEntity, pos: BlockPos) {
            val be = player.world.getBlockEntity(pos)
            if (be != null && be is ComputerBlockEntity) {
                if (openTerminalMap[player] != null) {
                    error("A terminal for ${player.name} is already open")
                }
                openTerminalMap[player] = be
                be.openPlayers += player
                be.sendLogs(player)
            } else {
                error("Player ${player.name} tried to register an open terminal with non-computer block at $pos")
            }
        }
        fun handleGuiClose(player: PlayerEntity) {
            val be = openTerminalMap[player] ?: return
            be.openPlayers.remove(player)
            openTerminalMap.remove(player)
        }
        fun handleInput(player: PlayerEntity, input: String) {
            val block = openTerminalMap[player]
            if (block == null) {
                error("Player sent input to a terminal, but they are not registered with one.")
            } else {
                block.onTerminalInput(input)
            }
        }
    }

    private val openPlayers: ArrayList<PlayerEntity> = arrayListOf()
    private var data = ComputerData(Context(ComputerIO(this).ioCommands()))

    override fun toTag(tag: CompoundTag): CompoundTag {
        ComputerData.serializer().put(data, inTag = tag)
        return super.toTag(tag)
    }

    override fun fromTag(tag: CompoundTag) {
        super.fromTag(tag)
        data = ComputerData.serializer().getFrom(tag)
    }

    /**
     * Called when the player types input into this terminal.
     */
    private fun onTerminalInput(input: String) {
        printToTerminal(">$input\n")
        try {
            data.context.exec(parse(input))
        } catch (e: ExecError) {
            printToTerminal("Error: ${e.message}\n")
        } catch (e: ParseException) {
            printToTerminal("Parse Error: ${e.message}\n")
        }
    }

    /**
     * Client-side only. Sends the terminal open packet to the server
     *
     * This will cause the server to start sending terminal output
     */
    fun openTerminal() {
        if (!world!!.isClient()) {
            error("Called openTerminal on serverside")
        }
        val packetBuf = PacketByteBuf(Unpooled.buffer())
        packetBuf.writeBlockPos(this.pos)
        ClientSidePacketRegistry.INSTANCE.sendToServer(Greenstone.PACKET_TERMINAL_OPENED, packetBuf)
    }

    /**
     * Prints a string to the terminal and alerts all clients with the GUI open.
     */
    fun printToTerminal(str: String) {
        data.logs += str
        openPlayers.forEach { player ->
            val passedData = PacketByteBuf(Unpooled.buffer())
            passedData.writeString(str)
            ServerSidePacketRegistry.INSTANCE.sendToPlayer(player, Greenstone.PACKET_TERMINAL_OUTPUT, passedData)
        }
    }

    /**
     * Sends the contents of the terminal screen to the given player.
     */
    private fun sendLogs(player: PlayerEntity) {
        if (world!!.isClient()) {
            error("Called sendLogs on clientside")
        }

        val passedData = PacketByteBuf(Unpooled.buffer())
        passedData.writeString(data.logs)
        ServerSidePacketRegistry.INSTANCE.sendToPlayer(player, Greenstone.PACKET_TERMINAL_CONTENTS, passedData)
    }

    fun clearTerminal() {
        data.logs = ""
        for (player in openPlayers) {
            val passedData = PacketByteBuf(Unpooled.buffer())
            passedData.writeString(data.logs)
            ServerSidePacketRegistry.INSTANCE.sendToPlayer(player, Greenstone.PACKET_TERMINAL_CONTENTS, passedData)
        }
    }
}

class ComputerIO(private val block: ComputerBlockEntity) : GrplIO {
    override fun print(str: String) {
        block.printToTerminal(str)
    }

    override fun clear() {
        block.clearTerminal()
    }
}