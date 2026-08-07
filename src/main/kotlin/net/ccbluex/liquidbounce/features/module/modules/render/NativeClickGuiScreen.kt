package net.ccbluex.liquidbounce.features.module.modules.render

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.mojang.blaze3d.vertex.PoseStack

class NativeClickGuiScreen : Screen(Component.translatable("clickgui.title")) {

    private val mc: Minecraft = Minecraft.getInstance()
    private var placeholderButton: Button? = null

    /** 初始化 UI（使用 Button.builder 创建默认按钮） */
    override fun init() {
        placeholderButton = Button.builder(
            Component.literal("模块列表"),
            Button.OnPress { /* 点击逻辑 */ }
        )
            .pos(10, 40)
            .size(120, 20)
            .build()
        addRenderableWidget(placeholderButton!!)
    }

    /** 渲染背景、面板、标题及子控件 */
    override fun render(poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTicks: Float) {
        this.renderBackground(poseStack)                     // 全屏暗色遮罩
        val margin = 20
        fill(poseStack, margin, margin, width - margin, height - margin, ModuleClickGui.panelColor) // 面板
        val titleText = this.title
        val titleX = (width - mc.font.width(titleText)) / 2f
        val titleY = margin + 8f
        mc.font.draw(poseStack, titleText, titleX + 1f, titleY + 1f, ModuleClickGui.titleShadowColor) // 阴影
        mc.font.draw(poseStack, titleText, titleX, titleY, ModuleClickGui.titleColor)                 // 正文
        super.render(poseStack, mouseX, mouseY, partialTicks) // 子控件（按钮）
    }

    override fun isPauseScreen(): Boolean = true

    // ESC 键已在 Screen.keyPressed(KeyEvent) 中自动处理，无需额外实现
}
