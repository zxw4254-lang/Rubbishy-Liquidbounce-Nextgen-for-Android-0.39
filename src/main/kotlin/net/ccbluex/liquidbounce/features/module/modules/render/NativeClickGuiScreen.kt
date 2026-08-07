package net.ccbluex.liquidbounce.features.module.modules.render

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import com.mojang.blaze3d.vertex.PoseStack

/**
 * 美化后的原生 ClickGUI 示例（仅通过 ESC 键关闭）。
 *
 * 采用工作区中提供的渲染核心类（Screen、Button、fill 等），实现了：
 *   • 半透明暗色全屏背景（可在配置中调节）
 *   • 居中面板（可调颜色）
 *   • 标题居中并带阴影（可调颜色）
 *   • 左侧占位按钮示例（演示自定义按钮渲染）
 *   • **不再提供右上角的关闭按钮**，仅使用 ESC 键关闭 GUI
 */
class NativeClickGuiScreen : Screen(Component.translatable("clickgui.title")) {

    private val mc: Minecraft = Minecraft.getInstance()

    // ------------------------------------------------------------
    // UI 元素（仅保留示例按钮）
    // ------------------------------------------------------------
    private var placeholderButton: Button? = null

    /** 初始化 UI（演示一个自定义渲染的按钮） */
    override fun init() {
        // 左侧占位按钮（示例），演示如何自定义按钮颜色与悬停效果
        placeholderButton = object : Button(
            10, 40, 120, 20,
            Component.literal("模块列表")
        ) {
            override fun renderButton(poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTicks: Float) {
                val hovered = this.isHovered(mouseX.toDouble(), mouseY.toDouble())
                val bgColor = if (hovered) ModuleClickGui.buttonHoverColor else ModuleClickGui.buttonColor
                // 绘制按钮背景矩形
                fill(poseStack, x, y, x + width, y + height, bgColor)
                // 绘制居中文本
                val txt = this.message.string
                val txtWidth = mc.font.width(txt)
                mc.font.draw(
                    poseStack,
                    txt,
                    (x + width / 2 - txtWidth / 2).toFloat(),
                    (y + height / 2 - 4).toFloat(),
                    ModuleClickGui.titleColor
                )
            }
        }
        addRenderableWidget(placeholderButton!!)
    }

    /**
     * 渲染整个 GUI（背景 + 面板 + 标题）
     *   1️⃣ 全屏暗色遮罩（默认实现）
     *   2️⃣ 居中面板（可调颜色）
     *   3️⃣ 标题（带阴影，可调颜色）
     *   4️⃣ 渲染子控件（按钮）
     */
    override fun render(poseStack: PoseStack, mouseX: Int, mouseY: Int, partialTicks: Float) {
        // 1️⃣ 全屏暗色遮罩（使用 Screen 的 renderBackground）
        this.renderBackground(poseStack)

        // 2️⃣ 居中面板背景（使用配置的 panelColor）
        val margin = 20
        fill(poseStack, margin, margin, width - margin, height - margin, ModuleClickGui.panelColor)

        // 3️⃣ 标题（带阴影，可调颜色）
        val titleText = this.title
        val titleX = (width - mc.font.width(titleText)) / 2f
        val titleY = margin + 8f
        // 阴影（左上角偏移 1 像素）
        mc.font.draw(poseStack, titleText, titleX + 1f, titleY + 1f, ModuleClickGui.titleShadowColor)
        // 正文本身
        mc.font.draw(poseStack, titleText, titleX, titleY, ModuleClickGui.titleColor)

        // 4️⃣ 渲染子控件（按钮等）
        super.render(poseStack, mouseX, mouseY, partialTicks)
    }

    /** 让该界面在打开时暂停游戏 */
    override fun isPauseScreen(): Boolean = true

    /** ESC 键关闭 GUI */
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }
}
