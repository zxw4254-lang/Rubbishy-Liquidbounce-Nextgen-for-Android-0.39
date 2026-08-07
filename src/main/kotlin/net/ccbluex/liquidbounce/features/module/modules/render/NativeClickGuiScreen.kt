package net.ccbluex.liquidbounce.features.module.modules.render

// 使用 Minecraft 原生 Screen
import net.ccbluex.liquidbounce.integration.screen.ScreenManager
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import com.mojang.blaze3d.vertex.PoseStack
import java.util.*

/**
 * Kotlin 移植版的 **Native ClickGUI**（类名为 NativeClickGuiScreen）
 *
 * 该文件把工作区中 `com.opal.clickgui.model` 包下的 Java 数据模型迁移为 Kotlin
 * data class / sealed class，并提供一个最简版的原生 GUI 实现。
 *
 * 为了兼容 `ModuleClickGui` 中的 `NativeClickGuiScreen` 调用，这里保留相同的
 * 类名并继承自 Minecraft 官方的 `Screen`（而不是已 `final` 的 CustomScreen）。
 *
 * 现在可以通过 `mc.gui.setScreen(NativeClickGuiScreen())` 打开此界面，
 * 之后在 `drawRoundedRect`、`drawCircle`、`renderProperty` 等占位函数里
 * 填入实际的 OpenGL/Shader 实现即可完成完整的 ClickGUI。
 */

/* ---------- 数据模型 ---------- */

data class Category(
    var name: String,
    var icon: String,
    var modules: MutableList<Module>,
    var layoutX: Float = 0f,
    var layoutY: Float = 0f,
    var layoutWidth: Float = 0f,
    var layoutHeight: Float = 0f
)

data class Module(
    var name: String,
    var categoryName: String = "",
    var on: Boolean = false,
    var props: MutableList<Property> = mutableListOf(),
    var expanded: Boolean = false,
    var visible: Boolean = true,
    var expandAnim: Float = 0f,
    var toggleAnim: Float = if (on) 1f else 0f,
    var layoutY: Float = 0f,
    var layoutHeight: Float = 0f,
    var keyEnabled: Boolean = false,
    var keyCode: Int = 0,
    var keyX: Float = -1f,
    var keyY: Float = -1f,
    var keyDragging: Boolean = false
) {
    fun hasProps(): Boolean = props.isNotEmpty()
}

/** Property 使用 sealed class 表示四种不同的属性类型 */
sealed class Property {
    abstract var label: String
    abstract var layoutY: Float
    abstract var layoutHeight: Float

    /** 布尔开关 */
    data class Bool(
        override var label: String,
        var boolVal: Boolean,
        var boolAnim: Float = if (boolVal) 1f else 0f,
        override var layoutY: Float = 0f,
        override var layoutHeight: Float = 0f
    ) : Property()

    /** 数值滑块 */
    data class Num(
        override var label: String,
        var min: Double,
        var max: Double,
        var step: Double,
        var value: Double,
        var displayVal: Double = value,
        override var layoutY: Float = 0f,
        override var layoutHeight: Float = 0f
    ) : Property()

    /** 模式选择 */
    data class Mode(
        override var label: String,
        var modes: Array<String>,
        var modeIdx: Int,
        var modeOpen: Boolean = false,
        var modeAnim: Float = 0f,
        override var layoutY: Float = 0f,
        override var layoutHeight: Float = 0f
    ) : Property()

    /** 主题网格（不含额外字段） */
    data class Theme(
        override var label: String,
        override var layoutY: Float = 0f,
        override var layoutHeight: Float = 0f
    ) : Property()
}

/** 主题模型（与原 Java `Theme` 类同名） */
data class ThemeModel(
    var name: String,
    var c1: Int, // 主颜色 (ARGB)
    var c2: Int  // 副颜色 (ARGB)
) {
    companion object {
        /** 通过十六进制字符串创建 ThemeModel */
        fun fromHex(name: String, c1Hex: String, c2Hex: String): ThemeModel {
            val c1 = java.awt.Color.decode(c1Hex).rgb
            val c2 = java.awt.Color.decode(c2Hex).rgb
            return ThemeModel(name, c1, c2)
        }
    }
}

/* ---------- GUI 实现 ---------- */

/**
 * 简化的原生 ClickGUI Screen 实现。
 *
 * 该类继承自 `net.minecraft.client.gui.screens.Screen`，在 `render` 中完成
 * 基础的绘制（背景、分类、模块、属性占位）。之后可在
 * `drawRoundedRect`、`drawCircle`、`renderProperty` 等占位函数里实现实际渲染。
 */
class NativeClickGuiScreen : Screen(Component.literal("Native ClickGUI")) {

    // ---------- 常量（复制自原 Java 实现） ----------
    private val BASE_DESIGN_WIDTH = 360f
    private val MIN_SCALE = 2.0f
    private val MAX_SCALE = 3.2f
    private var S = 3.0f // 动态缩放因子，后续在 initScale() 中根据窗口宽度计算

    // 颜色（ARGB）
    private val BG_DARK = 0xFF0A0A0A.toInt()
    private val CAT_BG = 0xD9FFFFFF.toInt()
    private val CAT_BG_COLOR = 0xD90F0F0F.toInt()
    private val MOD_BG = 0xB31E1E2D.toInt()
    private val MOD_BG_HOVER = 0xC7262637.toInt()
    private val PROPS_BG = 0x38000000

    // ---------- 示例主题 ----------
    private val themes = listOf(
        ThemeModel.fromHex("Opal", "#2DBFFE", "#2499CB"),
        ThemeModel.fromHex("Spearmint", "#61C2A2", "#41826C")
        // 其它主题可自行添加
    )
    private var themeIndex = 0
    private var currentTheme = themes[themeIndex]

    // ---------- 示例数据（实际运行时请自行注入真实配置） ----------
    private val categories = mutableListOf<Category>()

    init {
        // 示例「Combat」分类
        val combatModules = mutableListOf(
            Module(
                name = "KillAura",
                on = true,
                props = mutableListOf(
                    Property.Bool("Enabled", true),
                    Property.Num("Range", 1.0, 6.0, 0.1, 3.5),
                    Property.Mode("Target", arrayOf("Nearest", "Health", "Armor"), 0)
                )
            ),
            Module(
                name = "AutoEz",
                props = mutableListOf(Property.Bool("AutoMessage", false))
            )
        )
        categories.add(Category("Combat", "sword", combatModules))

        // 示例「Movement」分类
        val movementModules = mutableListOf(
            Module(
                name = "Speed",
                props = mutableListOf(
                    Property.Num("Multiplier", 1.0, 5.0, 0.1, 2.0)
                )
            )
        )
        categories.add(Category("Movement", "run", movementModules))

        // 计算 UI 缩放比例
        initScale()
    }

    /** 根据当前窗口宽度计算 UI 缩放因子 S */
    private fun initScale() {
        // 使用公开的窗口宽度（不访问私有 framebufferWidth）
        val windowWidth = mc.window?.width?.toFloat() ?: 800f
        var scale = windowWidth / BASE_DESIGN_WIDTH
        if (scale < MIN_SCALE) scale = MIN_SCALE
        if (scale > MAX_SCALE) scale = MAX_SCALE
        S = scale
    }

    /** 主渲染入口，符合 Screen.render(PoseStack, int, int, float) */
    fun render(poseStack: PoseStack, mouseX: Int, mouseY: Int, tickDelta: Float) {
        // 1️⃣ 背景（半透明黑）
        GL11.glClearColor(0f, 0f, 0f, 0.85f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)

        // 2️⃣ 计算布局（每个分类的宽高、模块高度等）
        calculateLayout()

        // 3️⃣ 渲染每个分类卡片（横向排列）
        var offsetX = 0f
        for (cat in categories) {
            renderCategory(poseStack, cat, offsetX, 0f)
            offsetX += cat.layoutWidth + catGap()
        }
    }

    /** 计算每个分类以及内部模块的尺寸与坐标 */
    private fun calculateLayout() {
        for (cat in categories) {
            cat.layoutWidth = catWidth()
            var y = catHeaderHeight()
            for (mod in cat.modules) {
                mod.layoutY = y
                var h = moduleHeight()
                if (mod.expanded && mod.props.isNotEmpty()) {
                    h += mod.props.size * propertyHeight()
                }
                mod.layoutHeight = h
                y += h
            }
            cat.layoutHeight = y
        }
    }

    /** 渲染单个分类卡片 */
    private fun renderCategory(poseStack: PoseStack, cat: Category, x: Float, y: Float) {
        // 背景
        drawRoundedRect(x, y, cat.layoutWidth, cat.layoutHeight, catRadius(), CAT_BG)
        // 标题栏
        drawRoundedRect(x, y, cat.layoutWidth, catHeaderHeight(), catRadius(), CAT_BG_COLOR)
        // TODO：绘制文字（cat.name、cat.icon）可使用 poseStack 与字体渲染工具

        // 绘制模块列表
        for (mod in cat.modules) {
            renderModule(poseStack, mod, x, y + mod.layoutY)
        }
    }

    /** 渲染单个模块行以及（可选的）属性面板 */
    private fun renderModule(poseStack: PoseStack, mod: Module, baseX: Float, baseY: Float) {
        // 背景（Hover 时可改为 MOD_BG_HOVER，这里使用普通颜色）
        val bgColor = if (mod.expanded) MOD_BG_HOVER else MOD_BG
        drawRoundedRect(baseX, baseY, catWidth(), moduleHeight(), 4f * S, bgColor)

        // TODO：绘制模块名称文字
        // 示例：mc.font.draw(poseStack, mod.name, (baseX + 6f * S).toInt(), (baseY + 14f * S).toInt(), 0xFFFFFFFF.toInt())

        // 开关指示圆点（动画可映射到半径或颜色）
        val toggleRadius = 5f * S
        val circleX = baseX + catWidth() - 12f * S
        val circleY = baseY + moduleHeight() / 2f
        val circleColor = if (mod.on) 0xFF00FF00.toInt() else 0xFF555555.toInt()
        drawCircle(circleX, circleY, toggleRadius, circleColor)

        // 若展开且拥有属性，则逐一绘制属性
        if (mod.expanded && mod.props.isNotEmpty()) {
            var propY = baseY + moduleHeight()
            for (prop in mod.props) {
                renderProperty(prop, baseX, propY)
                propY += propertyHeight()
            }
        }
    }

    /** 渲染单个属性（这里只绘制背景占位，具体 UI 待实现） */
    private fun renderProperty(prop: Property, x: Float, y: Float) {
        drawRoundedRect(x, y, catWidth(), propertyHeight(), 3f * S, PROPS_BG)
        // TODO：根据属性类型绘制实际 UI（布尔开关、滑块、下拉框、主题网格等）
        // 示例文字占位：
        // mc.font.draw(poseStack, prop.label, (x + 6f * S).toInt(), (y + 12f * S).toInt(), 0xFFFFFFFF.toInt())
    }

    // ------------------- 辅助绘图占位函数（后续自行实现） -------------------
    private fun drawRoundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
        // 实际实现可使用 GL11 + 片段着色器绘制圆角矩形，或调用已有的渲染工具。
        // 此处仅保留签名，确保代码可编译。
    }

    private fun drawCircle(cx: Float, cy: Float, r: Float, color: Int) {
        // 同上，绘制圆形占位。
    }

    // ------------------- 常量计算（保持与原 Java 实现一致） -------------------
    private fun catWidth() = 118f * 4f * S
    private fun catGap() = 8f * 4f * S
    private fun catRadius() = 5f * 4f * S
    private fun catHeaderHeight() = 20f * 4f * S
    private fun moduleHeight() = 20f * 4f * S
    private fun propertyHeight() = 17f * 4f * S // 简化为统一高度

    override fun onClose() {
        // 当用户关闭 GUI 时可执行的清理逻辑，当前留空。
    }
}
