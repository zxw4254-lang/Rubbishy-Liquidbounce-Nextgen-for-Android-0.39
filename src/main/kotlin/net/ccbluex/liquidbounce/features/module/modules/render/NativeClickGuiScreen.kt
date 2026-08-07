package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.utils.client.mc

import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * 简单的原生 Android ClickGUI 实现（不依赖任何 Browser 后端）。
 * 只展示模块的开启/关闭按钮和一个复选框用于快速切换。
 */
class NativeClickGuiScreen : Screen("ClickGUI".asPlainText()) {
    private val lineHeight = 20
    private val startY = 30
    private var scrollOffset = 0
    private val widgetCache = mutableListOf<WidgetEntry>()

    private data class WidgetEntry(val module: Module, var button: Button?, var checkbox: Checkbox?, var y: Int)

    override fun init() {
        clearWidgets()
        widgetCache.clear()
        var yPos = startY
        for (module in ModuleManager.modules) {
            // 开/关按钮
            val toggleBtn = Button.builder(
                Component.literal(if (module.enabled) "§aON" else "§cOFF")
            ) {
                module.toggle()
                it.message = Component.literal(if (module.enabled) "§aON" else "§cOFF")
            }
                .pos(20, yPos)
                .size(40, lineHeight - 2)
                .build()
            addRenderableWidget(toggleBtn)

            // 复选框（仅示例）
            val cb = Checkbox(
                70, yPos, 80, lineHeight - 2,
                Component.literal(module.name),
                module.enabled
            ) { newVal ->
                if (newVal != module.enabled) module.toggle()
            }
            addRenderableWidget(cb)

            widgetCache.add(WidgetEntry(module, toggleBtn, cb, yPos))
            yPos += lineHeight
        }
    }

    private fun clearWidgets() {
        children().clear()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        val maxOffset = (widgetCache.size - (height - startY) / lineHeight).coerceAtLeast(0)
        scrollOffset = (scrollOffset - delta.toInt()).coerceIn(0, maxOffset)
        applyScroll()
        return true
    }

    private fun applyScroll() {
        for (e in widgetCache) {
            e.button?.visible = false
            e.checkbox?.visible = false
        }
        val visibleCount = (height - startY) / lineHeight
        val startIdx = scrollOffset
        val endIdx = (scrollOffset + visibleCount).coerceAtMost(widgetCache.size)
        for (i in startIdx until endIdx) {
            val e = widgetCache[i]
            e.button?.let { it.visible = true; it.y = startY + (i - scrollOffset) * lineHeight }
            e.checkbox?.let { it.visible = true; it.y = startY + (i - scrollOffset) * lineHeight }
        }
    }

    override fun render(guiGraphics: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        // 半透明背景
        fill(guiGraphics.pose(), 0, 0, width, height, 0xAA_202020)
        drawCenteredString(guiGraphics, mc.font, Component.literal("ClickGUI (Android native)"), width / 2, 8, 0xFFFFFF)
        super.render(guiGraphics, mouseX, mouseY, partialTicks)
        // 简易滚动条
        if (widgetCache.size * lineHeight > height - startY) {
            val barHeight = (height - startY) * (height - startY) / (widgetCache.size * lineHeight)
            val barY = startY + scrollOffset * (height - startY - barHeight) / (widgetCache.size * lineHeight - (height - startY) / lineHeight)
            fill(guiGraphics.pose(), width - 6, barY, width - 2, barY + barHeight, 0xFF_A0A0A0)
        }
    }
}
