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
 * - 只显示每个模块的名称与 ON/OFF 按钮
 * - 支持滚动、半透明背景、右侧滚动条
 */
class NativeClickGuiScreen : Screen("ClickGUI".asPlainText()) {

    /* ---------- 常量 & 状态 ---------- */
    private val lineHeight = 20          // 每行高度
    private val startY = 30            // 列表起始 Y 坐标
    private var scrollOffset = 0       // 当前滚动的行数
    private val widgetCache = mutableListOf<WidgetEntry>()

    /** 保存模块 ↔ 按钮 的映射，滚动时用来显示/隐藏 */
    private data class WidgetEntry(val module: ClientModule, var button: Button?, var y: Int)

    /* ---------- 初始化（创建按钮列表） ---------- */
    override fun init() {
        // 清空可能残留的部件
        children().clear()
        widgetCache.clear()

        var yPos = startY
        ModuleManager.modules.forEach { module ->
            // 为每个模块创建一个按钮，文字显示“名称 : ON/OFF”
            val btn = Button.builder(
                Component.literal("${module.name} : ${if (module.enabled) "§aON" else "§cOFF"}")
            ) { button ->
                // 切换状态后立即刷新按钮文字
                module.toggle()
                button.message = Component.literal(
                    "${module.name} : ${if (module.enabled) "§aON" else "§cOFF"}"
                )
            }
                .pos(20, yPos)                // 按钮位置
                .size(200, lineHeight - 2)     // 按钮尺寸
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
        // scrollY > 0 为向下滚动，< 0 为向上滚动 → 为让列表向相反方向移动取负号
        val maxOffset = (widgetCache.size - (height - startY) / lineHeight).coerceAtLeast(0)
        scrollOffset = (scrollOffset - scrollY.toInt()).coerceIn(0, maxOffset)
        applyScroll()
        return true
    }

    /** 根据当前 scrollOffset 隐藏/显示对应的按钮 */
    private fun applyScroll() {
        // 隐藏全部按钮
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

        // 4️⃣ 简易右侧滚动条（仅在需要时绘制）
        if (widgetCache.size * lineHeight > height - startY) {
            val barHeight = (height - startY) * (height - startY) / (widgetCache.size * lineHeight)

            // 计算滚动条在 0..1 区间的比例
            val maxScroll = widgetCache.size - (height - startY) / lineHeight
            val proportion = scrollOffset.toFloat() / maxScroll.coerceAtLeast(1)

            val barY = startY + ((height - startY - barHeight) * proportion).toInt()
            guiGraphics.fill(width - 6, barY, width - 2, barY + barHeight, 0xFF_A0A0A0)
        }
    }
}
