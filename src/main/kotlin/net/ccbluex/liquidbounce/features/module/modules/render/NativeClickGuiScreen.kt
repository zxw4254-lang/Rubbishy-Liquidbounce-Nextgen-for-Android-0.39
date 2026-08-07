package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.GuiGraphics

/**
 * 简单的原生 Android ClickGUI（不依赖任何 Browser 后端）。
 * - 列出所有已注册的模块（`ClientModule`）并显示 ON/OFF 按钮
 * - 支持滚动、半透明背景、右侧滚动条
 */
class NativeClickGuiScreen : Screen("ClickGUI".asPlainText()) {

    /* ---------- 视觉常量 ---------- */
    private val lineHeight = 20          // 每行的高度
    private val startY = 30              // 列表起始 Y 坐标
    private var scrollOffset = 0        // 当前滚动的行号
    private val widgetCache = mutableListOf<WidgetEntry>()

    /** 用于保存模块 ↔ 按钮 的对应关系，以便滚动时显示/隐藏 */
    private data class WidgetEntry(val module: ClientModule, var button: Button?, var y: Int)

    /* ---------- 初始化（创建按钮） ---------- */
    override fun init() {
        // 清空可能残留的 UI 部件
        children().clear()
        widgetCache.clear()

        var yPos = startY
        // 直接遍历 ModuleManager 中的所有模块
        for (module in ModuleManager.modules) {
            // 按钮文字：模块名称 + 当前状态
            val btn = Button.builder(
                Component.literal("${module.name} : ${if (module.enabled) "§aON" else "§cOFF"}")
            ) { button ->
                // 切换模块状态后同步按钮文字
                module.toggle()
                button.message = Component.literal(
                    "${module.name} : ${if (module.enabled) "§aON" else "§cOFF"}"
                )
            }
                .pos(20, yPos)                // 按钮在屏幕左侧的 X/Y
                .size(200, lineHeight - 2)    // 按钮宽高
                .build()

            addRenderableWidget(btn)
            widgetCache.add(WidgetEntry(module, btn, yPos))
            yPos += lineHeight
        }
    }

    /* ---------- 滚动处理（Minecraft 1.20+） ---------- */
    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        scrollX: Double,
        scrollY: Double
    ): Boolean {
        // scrollY > 0 表示向下滚动（内容向上移动），取负号保持列表滚动方向一致
        val maxOffset = (widgetCache.size - (height - startY) / lineHeight).coerceAtLeast(0)
        scrollOffset = (scrollOffset - scrollY.toInt()).coerceIn(0, maxOffset)
        applyScroll()
        return true
    }

    /** 根据 `scrollOffset` 隐藏/显示对应的按钮 */
    private fun applyScroll() {
        // 先全部隐藏
        widgetCache.forEach { it.button?.visible = false }

        val visibleRows = (height - startY) / lineHeight
        val startIdx = scrollOffset
        val endIdx = (scrollOffset + visibleRows).coerceAtMost(widgetCache.size)

        for (i in startIdx until endIdx) {
            val entry = widgetCache[i]
            entry.button?.let { btn ->
                btn.visible = true
                btn.y = startY + (i - scrollOffset) * lineHeight
            }
        }
    }

    /* ---------- 渲染（背景、标题、滚动条） ---------- */
    override fun render(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float
    ) {
        // 1️⃣ 半透明暗色背景
        guiGraphics.fill(0, 0, width, height, 0xAA_202020)

        // 2️⃣ 居中标题
        guiGraphics.drawCenteredString(
            mc.font,
            Component.literal("ClickGUI (Android native)"),
            width / 2,
            8,
            0xFFFFFF
        )

        // 3️⃣ 渲染所有子部件（按钮）
        super.render(guiGraphics, mouseX, mouseY, partialTicks)

        // 4️⃣ 右侧滚动条（仅在需要时绘制）
        if (widgetCache.size * lineHeight > height - startY) {
            val barHeight = (height - startY) * (height - startY) / (widgetCache.size * lineHeight)

            // 计算滚动条的比例（0..1）
            val maxScroll = widgetCache.size - (height - startY) / lineHeight
            val proportion = scrollOffset.toFloat() / maxScroll.coerceAtLeast(1)

            val barY = startY + ((height - startY - barHeight) * proportion).toInt()
            guiGraphics.fill(width - 6, barY, width - 2, barY + barHeight, 0xFF_A0A0A0)
        }
    }
}
