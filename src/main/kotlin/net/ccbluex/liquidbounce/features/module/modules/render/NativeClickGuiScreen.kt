package net.ccbluex.liquidbounce.features.module.modules.render

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.Gui

/**
 * 美化后的原生 ClickGUI 示例（仅通过 ESC 键关闭）。
 *
 * 采用工作区中提供的渲染核心类（Screen、Button、fill 等），实现了：
 *   • 半透明暗色全屏背景（可在配置中调节）
 *   • 居中面板（可调颜色）
 *   • 标题居中并带阴影（可调颜色）
 *   • 左侧占位按钮示例（演示默认按钮）
 *   • **不再提供右上角的关闭按钮**，仅使用 ESC 键关闭 GUI
 */
class NativeClickGuiScreen : Screen(Component.translatable("clickgui.title")) {

    private val mc: Minecraft = Minecraft.getInstance()

    // ------------------------------------------------------------
    // UI 元素（仅示例按钮）
    // ------------------------------------------------------------
    private var placeholderButton: Button? = null

    /** 初始化 UI（使用 Button.builder 创建默认按钮） */
    override fun init() {
        // 示例按钮：左侧占位，使用默认渲染
        placeholderButton = Button.builder(
            Component.literal("模块列表"),
            Button.OnPress { /* 点击逻辑可在此实现 */ }
        )
            .pos(10, 40)
            .size(120, 20)
            .build()
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
        // 1️⃣ 渲染全屏暗色遮罩（使用可配置的 background 颜色）
        net.minecraft.client.gui.Gui.fill(poseStack, 0, 0, width, height, ModuleClickGui.background)

        // 2️⃣ 居中面板背景（可调颜色）
        val margin = 20
        net.minecraft.client.gui.Gui.fill(poseStack, margin, margin, width - margin, height - margin, ModuleClickGui.panelColor)

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

    // 不需要手动实现 ESC 键关闭，Screen 已在 keyPressed(KeyEvent) 中处理 ESC。若有自定义需求，可保留以下实现：
    /*
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }
    */
}
