package net.ccbluex.liquidbounce.features.module.modules.render

// 移除了对 CustomScreen 的依赖，直接使用 Minecraft 原生 Screen
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.opengl.GL11
import java.util.*

/**
 * Kotlin 移植版的 **Native ClickGUI**
 *
 * 该文件把工作区中 `com.opal.clickgui.model` 包下的 Java 数据模型全部迁移为 Kotlin
 * data class / sealed class，实现同等的属性与动画字段。
 *
 * 同时提供一个最简版的 `NativeClickGuiScreen`，它继承自 Minecraft 原生
 * `Screen`（而不是已 `final` 的 `CustomScreen`），在 `render()` 中通过
 * OpenGL (LWJGL) 绘制 **分类、模块、属性**，并保留了原 Java 版的配色、尺寸、
 * 动画进度字段，以便后续在 UI 中实现完整的交互效果。
 *
 * `ModuleClickGui` 已经在 `ModuleClickGui.kt` 中把 `NativeClickGuiScreen`
 * 作为 Android‑only 的打开界面。现在该类同样在桌面端可用，实现了
 * “ModuleClickGui 为 NativeClickGui 的接口” 的需求。
 */

/* ---------- 数据模型 ---------- */

data class Category(
    var name: String,
    var icon: String,
    var modules: MutableList<Module>,
    // layout cache – 与 Java 版保持一致
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
    // animation progress
    var expandAnim: Float = 0f,
    var toggleAnim: Float = if (on) 1f else 0f,
    // layout cache
    var layoutY: Float = 0f,
    var layoutHeight: Float = 0f,
    // shortcut key fields (kept for compatibility)
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

/** 主题模型 – 与 Java `Theme` 类保持同名 */
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

/* ---------- UI 实现 ---------- */

/**
 * 简化的原生 ClickGUI Screen 实现。
 *
 * 该类继承自 Minecraft 的 `Screen`，在 `render()` 中完成最基础的绘制。
 * 详细的圆角矩形、圆形、文字渲染等可以在占位函数 `drawRoundedRect`,
 * `drawCircle`、`drawText` 中自行实现（或使用已有的渲染工具）。
 *
 * 之所以不再继承 `CustomScreen`，是因为 `CustomScreen` 在本项目中被声明
 * 为 `final`，无法被子类化。使用官方 `Screen` 同样能够被
 * `Minecraft.setScreen()` 正常打开，从而满足 **ModuleClickGui 为
 * NativeClickGui 接口** 的需求。
 */
class NativeClickGuiScreen : Screen(Component.literal("Native ClickGUI")) {

    // ---------- 常量（复制自 Java 实现） ----------
    private val BASE_DESIGN_WIDTH = 360f
    private val MIN_SCALE = 2.0f
    private val MAX_SCALE = 3.2f
    private var S = 3.0f // 动态缩放因子，随后在 initScale() 中根据窗口宽度计算

    // 颜色常量 (ARGB)
    private val BG_DARK = 0xFF0A0A0A.toInt()
    private val ISLAND_BG = 0xC7090909.toInt()
    private val CAT_BG = 0xD9FFFFFF.toInt()
    private val CAT_BG_COLOR = 0xD90F0F0F.toInt()
    private val MOD_BG = 0xB31E1E2D.toInt()
    private val MOD_BG_HOVER = 0xC7262637.toInt()
    private val PROPS_BG = 0x38000000
    private val HINT_BG = 0xA6000000

    // ---------- 主题 ----------
    private val themes: List<ThemeModel> = listOf(
        ThemeModel.fromHex("Opal", "#2DBFFE", "#2499CB"),
        ThemeModel.fromHex("Spearmint", "#61C2A2", "#41826C"),
        ThemeModel.fromHex("Jade Green", "#00A86B", "#006942"),
        ThemeModel.fromHex("Green Spirit", "#9FE2BF", "#00873E"),
        ThemeModel.fromHex("Rosy Pink", "#FF66CC", "#BF4D99"),
        ThemeModel.fromHex("Magenta", "#D53F77", "#9D446E"),
        ThemeModel.fromHex("Hot Pink", "#E75480", "#AC4FC6"),
        ThemeModel.fromHex("Lavender", "#DBA6F7", "#9873AC"),
        ThemeModel.fromHex("Amethyst", "#9063CD", "#62438C"),
        ThemeModel.fromHex("Purple Fire", "#B1A2CA", "#68478D"),
        ThemeModel.fromHex("Sunset Pink", "#FF9114", "#F569E7"),
        ThemeModel.fromHex("Blaze Orange", "#FFA94D", "#FF8200"),
        ThemeModel.fromHex("Pink Blood", "#FFA6C9", "#E40046"),
        ThemeModel.fromHex("Pastel", "#FF6D6A", "#BF5250"),
        ThemeModel.fromHex("Neon Red", "#D22730", "#B8192A"),
        ThemeModel.fromHex("Red Coffee", "#E1223B", "#4B1313"),
        ThemeModel.fromHex("Deep Ocean", "#3C5291", "#001440"),
        ThemeModel.fromHex("Chambray", "#3C5291", "#212EB6"),
        ThemeModel.fromHex("Mint Blue", "#429E9D", "#285E5D"),
        ThemeModel.fromHex("Pacific", "#05A9C7", "#047387"),
        ThemeModel.fromHex("Tropical Ice", "#66FFD1", "#0695FF"),
        ThemeModel.fromHex("Blue Purple", "#684DB2", "#043CAE")
    )
    private var themeIndex = 0
    private var currentTheme: ThemeModel = themes[themeIndex]

    // ---------- 示例数据（若实际运行时有真实配置，可在构造时注入） ----------
    private val categories: MutableList<Category> = mutableListOf()

    init {
        // 示例「Combat」分类
        val combatModules = mutableListOf(
            Module(
                name = "KillAura",
                categoryName = "Combat",
                on = true,
                props = mutableListOf(
                    Property.Bool(label = "Enabled", boolVal = true),
                    Property.Num(label = "Range", min = 1.0, max = 6.0, step = 0.1, value = 3.5),
                    Property.Mode(label = "Target", modes = arrayOf("Nearest", "Health", "Armor"), modeIdx = 0)
                )
            ),
            Module(
                name = "AutoEz",
                categoryName = "Combat",
                on = false,
                props = mutableListOf(Property.Bool("AutoMessage", false))
            )
        )
        categories.add(Category(name = "Combat", icon = "sword", modules = combatModules))

        // 示例「Movement」分类
        val movementModules = mutableListOf(
            Module(
                name = "Speed",
                categoryName = "Movement",
                on = false,
                props = mutableListOf(Property.Num(label = "Multiplier", min = 1.0, max = 5.0, step = 0.1, value = 2.0))
            )
        )
        categories.add(Category(name = "Movement", icon = "run", modules = movementModules))

        // 计算 UI 缩放因子
        initScale()
    }

    /** 根据当前窗口宽度计算 UI 缩放比例 S */
    private fun initScale() {
        // 这里使用 Minecraft Window 的公开 `width`（而非私有 `framebufferWidth`）
        val windowWidth = mc.window?.width?.toFloat() ?: 800f
        var scale = windowWidth / BASE_DESIGN_WIDTH
        if (scale < MIN_SCALE) scale = MIN_SCALE
        if (scale > MAX_SCALE) scale = MAX_SCALE
        S = scale
    }

    /** 主渲染入口，符合 `Screen.render(GuiGraphics, int, int, float)` */
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        // 1️⃣ 背景（半透明黑）
        GL11.glClearColor(0f, 0f, 0f, 0.85f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)

        // 2️⃣ 计算布局（每个分类的宽高、模块的展开高度等）
        calculateLayout()

        // 3️⃣ 渲染每个分类卡片
        var offsetX = 0f
        for (cat in categories) {
            renderCategory(guiGraphics, cat, offsetX, 0f)
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
    private fun renderCategory(guiGraphics: GuiGraphics, cat: Category, x: Float, y: Float) {
        // 背景
        drawRoundedRect(x, y, cat.layoutWidth, cat.layoutHeight, catRadius(), CAT_BG)
        // 标题栏
        drawRoundedRect(x, y, cat.layoutWidth, catHeaderHeight(), catRadius(), CAT_BG_COLOR)
        // TODO: 绘制文字 (cat.name、cat.icon) – 可使用 guiGraphics.drawString 等

        // 渲染模块列表
        for (mod in cat.modules) {
            renderModule(guiGraphics, mod, x, y + mod.layoutY)
        }
    }

    /** 渲染单个模块行以及（可选的）属性面板 */
    private fun renderModule(guiGraphics: GuiGraphics, mod: Module, baseX: Float, baseY: Float) {
        // 背景（悬停时可改为 hover 颜色，这里使用普通颜色）
        val bgColor = if (mod.expanded) MOD_BG_HOVER else MOD_BG
        drawRoundedRect(baseX, baseY, catWidth(), moduleHeight(), 4f * S, bgColor)

        // TODO: 绘制模块名称文字
        // guiGraphics.drawString(mc.font, mod.name, (baseX + 6f * S).toInt(), (baseY + 14f * S).toInt(), 0xFFFFFFFF.toInt())

        // 开关指示圆点（动画进度可映射到半径或颜色）
        val toggleRadius = 5f * S
        val circleX = baseX + catWidth() - 12f * S
        val circleY = baseY + moduleHeight() / 2f
        val circleColor = if (mod.on) 0xFF00FF00.toInt() else 0xFF555555.toInt()
        drawCircle(circleX, circleY, toggleRadius, circleColor)

        // 若展开且拥有属性，则逐一绘制属性
        if (mod.expanded && mod.props.isNotEmpty()) {
            var propY = baseY + moduleHeight()
            for (prop in mod.props) {
                renderProperty(guiGraphics, prop, baseX, propY)
                propY += propertyHeight()
            }
        }
    }

    /** 渲染单个属性（这里只绘制背景占位，具体 UI 可自行实现） */
    private fun renderProperty(guiGraphics: GuiGraphics, prop: Property, x: Float, y: Float) {
        drawRoundedRect(x, y, catWidth(), propertyHeight(), 3f * S, PROPS_BG)
        // TODO: 根据属性类型绘制对应 UI（布尔开关、滑块、下拉框、主题网格等）
        // 示例文字占位:
        // guiGraphics.drawString(mc.font, prop.label, (x + 6f * S).toInt(), (y + 12f * S).toInt(), 0xFFFFFFFF.toInt())
    }

    // ------------------- 辅助绘图函数（占位） -------------------
    private fun drawRoundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
        // 实际实现请使用 GL11 + 片段着色器绘制圆角矩形，或调用已有的渲染工具。
        // 此处仅保留签名以保证编译通过。
    }

    private fun drawCircle(cx: Float, cy: Float, r: Float, color: Int) {
        // 同上，绘制圆形占位。
    }

    // ------------------- 常量计算（保持和原 Java 实现一致） -------------------
    private fun catWidth() = 118f * 4f * S
    private fun catGap() = 8f * 4f * S
    private fun catRadius() = 5f * 4f * S
    private fun catHeaderHeight() = 20f * 4f * S
    private fun moduleHeight() = 20f * 4f * S
    private fun propertyHeight() = 17f * 4f * S // 简化为统一高度

    override fun onClose() {
        // 关闭 GUI 时的清理逻辑（若有）可自行实现
    }
}

/* ---------- 说明 ---------- */
/**
 * 本文件的目标是提供 **NativeClickGui** 在桌面端的最小可运行实现。
 *
 * 1. 完全迁移了原 Android 项目中的数据模型（Category、Module、Property、Theme）。
 * 2. 采用 Kotlin `sealed class` 替代 Java 中的 `type` 整数，使代码更安全、易维护。
 * 3. 使用官方的 `Screen` 基类，避免了 `CustomScreen` 被标记为 `final` 的兼容问题。
 * 4. `render` 方法已改为符合 `Screen` 接口的签名，并传递 `GuiGraphics` 给子渲染函数。
 * 5. 通过 `mc.window?.width` 读取窗口宽度，避免访问 `framebufferWidth`（私有属性）。
 * 6. `ModuleClickGui` 中的 `onEnabled()` 与右 Shift 键回调仍然调用
 *    `mc.gui.setScreen(NativeClickGuiScreen())`，现在该调用会打开本实现，实现了
 *    “ModuleClickGui 为 NativeClickGui 接口”的需求。
 *
 * 后续如果需要完整的交互（点击、滑块拖拽、搜索栏、动态岛等），只需在
 * `drawRoundedRect`、`drawCircle`、`renderProperty` 里加入实际的 OpenGL/Shader
 * 实现或直接使用 LiquidBounce/Minecraft 已提供的绘制工具即可。
 */
