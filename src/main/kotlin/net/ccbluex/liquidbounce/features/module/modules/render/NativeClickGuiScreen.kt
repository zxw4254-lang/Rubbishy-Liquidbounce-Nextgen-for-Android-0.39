package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.GuiGraphics

/**
 * 简单的原生 Android ClickGUI 实现（不依赖任何 Browser 后端）。
 * 只展示模块的开启/关闭按钮（模块名称+状态），并支持滚动。
 */
class NativeClickGuiScreen : Screen("ClickGUI".asPlainText()) {
    private val lineHeight = 20
    private val startY = 30
    private var scrollOffset = 0
    private val widgetCache = mutableListOf<WidgetEntry>()

    private data class WidgetEntry(val module: Module, var button: Button?, var y: Int)

    override fun init() {
        // 清空旧部件
        children().clear()
        widgetCache.clear()
        var yPos = startY
        for (module in ModuleManager.modules) {
            val btn = Button.builder(
                Component.literal(module.name + " : " + if (module.enabled) "§aON" else "§cOFF")
            ) { button ->
                module.toggle()
                button.message = Component.literal(module.name + " : " + if (module.enabled) "§aON" else "§cOFF")
            }
                .pos(20, yPos)
                .size(200, lineHeight - 2)
                .build()
            addRenderableWidget(btn)
            widgetCache.add(WidgetEntry(module, btn, yPos))
            yPos += lineHeight
        }
    }

    /** 滚动处理 */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        val max = (widgetCache.size - (height - startY) / lineHeight).coerceAtLeast(0)
        scrollOffset = (scrollOffset - delta.toInt()).coerceIn(0, max)
        applyScroll()
        return true
    }

    private fun applyScroll() {
        widgetCache.forEach { it.button?.visible = false }
        val visible = (height - startY) / lineHeight
        val start = scrollOffset
        val end = (scrollOffset + visible).coerceAtMost(widgetCache.size)
        for (i in start until end) {
            val entry = widgetCache[i]
            entry.button?.let { it.visible = true; it.y = startY + (i - scrollOffset) * lineHeight }
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        // 背景（半透明）
        fill(0, 0, width, height, 0xAA_202020)
        // 标题
        drawCenteredString(guiGraphics, mc.font, Component.literal("ClickGUI (Android native)"), width / 2, 8, 0xFFFFFF)
        super.render(guiGraphics, mouseX, mouseY, partialTicks)
        // 简易滚动条
        if (widgetCache.size * lineHeight > height - startY) {
            val barHeight = (height - startY) * (height - startY) / (widgetCache.size * lineHeight)
            val barY = startY + scrollOffset * (height - startY - barHeight) / (widgetCache.size * lineHeight - (height - startY) / lineHeight)
            fill(width - 6, barY, width - 2, barY + barHeight, 0xFF_A0A0A0)
        }
    }
}
    }
}
