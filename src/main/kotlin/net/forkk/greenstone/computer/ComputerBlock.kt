package net.forkk.greenstone.computer

import net.minecraft.block.Block
import net.minecraft.block.BlockEntityProvider
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.HorizontalFacingBlock
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.BlockView
import net.minecraft.world.World

class ComputerBlock : HorizontalFacingBlock(Settings.copy(Blocks.IRON_BLOCK)), BlockEntityProvider {
    init {
        defaultState = this.stateManager.defaultState.with(Properties.HORIZONTAL_FACING, Direction.NORTH)
    }

    override fun createBlockEntity(blockview: BlockView): BlockEntity {
        return ComputerBlockEntity()
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(Properties.HORIZONTAL_FACING)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState {
        return defaultState.with(
            FACING,
            ctx.playerFacing.opposite
        ) as BlockState
    }

    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hand: Hand?,
        hit: BlockHitResult
    ): ActionResult? {
        if (world.isClient) {
            val be = world.getBlockEntity(pos)
            if (be != null && be is ComputerBlockEntity) {
                // Tell the server that we're listening for terminal output
                be.openTerminal()

                // Open the terminal GUI
                MinecraftClient.getInstance().openScreen(ComputerScreen())
            }
            return ActionResult.SUCCESS
        }
        return ActionResult.SUCCESS
    }

    override fun onBreak(world: World?, pos: BlockPos?, state: BlockState?, player: PlayerEntity?) {
        if (world?.isClient != true) {
            val be = world!!.getBlockEntity(pos)
            if (be != null && be is ComputerBlockEntity) {
                // Tell the server that we're listening for terminal output
                be.interruptProgram()
            }
        }
        return super.onBreak(world, pos, state, player)
    }
}
