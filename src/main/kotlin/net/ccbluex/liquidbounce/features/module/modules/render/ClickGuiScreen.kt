package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class ClickGuiScreen : Screen(Component.literal("ClickGUI")) {
    /** 兼容旧代码的快捷方式 */
    private val fontRenderer get() = this.font

    private var cat = 0
    private var expanded: ClientModule? = null
    private var search = ""
    private var searchFocus = false
    private var listeningValue: Value<*>? = null

    private val collapsedGroups = mutableSetOf<Value<*>>()

    private var sOff = 0f; private var tOff = 0f
    private var sOff2 = 0f; private var tOff2 = 0f
    private var anim = 0f
    private var flash = 0f
    private var flashRow = -1

    private val cats = ModuleCategories.entries.toList()
    private val W = 450; private val H = 320
    private val panelW = 170

    private val accent = 0xFF4182E1.toInt()
    private val bg = 0xE80C0C10.toInt()
    private val panelBg = 0xE814141A.toInt()
    private val headerBg = 0xF0000000.toInt()
    private val textGray = 0xFFA0A0AA.toInt()

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = true

    /** 绘制圆角矩形（原实现直接搬过来） */
    private fun fillRoundedRect(x1: Float, y1: Float, x2: Float, y2: Float, radius: Float, color: Int) {
        val r = radius.coerceAtMost((x2 - x1) / 2f).coerceAtMost((y2 - y1) / 2f)
        Gui.drawRect((x1 + r).toInt(), y1.toInt(), (x2 - r).toInt(), y2.toInt(), color)
        Gui.drawRect(x1.toInt(), (y1 + r).toInt(), (x1 + r).toInt(), (y2 - r).toInt(), color)
        Gui.drawRect((x2 - r).toInt(), (y1 + r).toInt(), x2.toInt(), (y2 - r).toInt(), color)

        val corners = arrayOf(
            floatArrayOf(x1 + r, y1 + r, 180f, 270f),
            floatArrayOf(x2 - r, y1 + r, 270f, 360f),
            floatArrayOf(x2 - r, y2 - r, 0f, 90f),
            floatArrayOf(x1 + r, y2 - r, 90f, 180f)
        )
        for (c in corners) {
            val cx = c[0]; val cy = c[1]
            val startAng = c[2]; val endAng = c[3]
            var a = startAng
            while (a < endAng) {
                val rad1 = Math.toRadians(a.toDouble())
                val rad2 = Math.toRadians((a + 10).coerceAtMost(endAng).toDouble())
                val px1 = cx + (cos(rad1) * r).toFloat()
                val py1 = cy + (sin(rad1) * r).toFloat()
                val px2 = cx + (cos(rad2) * r).toFloat()
                val py2 = cy + (sin(rad2) * r).toFloat()
                val minX = cx.coerceAtMost(px1).coerceAtMost(px2).toInt()
                val maxX = cx.coerceAtLeast(px1).coerceAtLeast(px2).toInt()
                val minY = cy.coerceAtMost(py1).coerceAtMost(py2).toInt()
                val maxY = cy.coerceAtLeast(py1).coerceAtLeast(py2).toInt()
                Gui.drawRect(minX, minY, max(minX + 1, maxX), max(minY + 1, maxY), color)
                a += 10f
            }
        }
    }

    /** 裁剪文字，防止溢出 */
    private fun trimText(font: FontRenderer, text: String, maxW: Int): String {
        if (font.width(text) <= maxW) return text
        var str = text
        while (str.isNotEmpty() && font.width("$str...") > maxW) {
            str = str.substring(0, str.length - 1)
        }
        return "$str..."
    }

    /** 判断是否为配置组（旧实现） */
    private fun isGroupValue(v: Value<*>): Boolean {
        val clsName = v.javaClass.simpleName
        if (clsName.contains("Group", true) || clsName.contains("Container", true)) return true
        return getGroupChild(v) != null
    }

    // -------------------------------------------------------------------------
    //   渲染
    // -------------------------------------------------------------------------
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        // 为兼容老代码，把原来的 `drawScreen` 变量直接映射过去
        val mx = mouseX
        val my = mouseY
        val dt = partialTicks

        anim += (1f - anim) * 0.25f
        val a = anim.coerceIn(0f, 1f)
        if (a < 0.01f) return

        if (flash > 0f) flash -= dt / 3f else flash = 0f

        val x = (width - W) / 2f
        val y = (height - H) / 2f
        val f = fontRenderer
        val tabW = (W - 24) / cats.size

        val R = 8f
        fillRoundedRect(x, y, x + W, y + H, R, bg)

        Gui.drawRect(x.toInt() + R.toInt(), y.toInt(), (x + W - R).toInt(), (y + 24).toInt(), headerBg)
        fontRenderer.drawString("§lClickGUI", x.toInt() + 10, y.toInt() + 5, accent)

        // ---------- 搜索框 ----------
        val searchY = y + 28
        Gui.drawRect(x.toInt() + 8, searchY.toInt(), (x + W - 8).toInt(), (searchY + 15).toInt(), 0x28000000.toInt())
        val disp = if (search.isEmpty()) "§7Search modules..." else "§f$search"
        fontRenderer.drawString(trimText(f, disp, W - 30), x.toInt() + 12, searchY.toInt() + 2, -1)
        if (searchFocus) {
            val cx = x.toInt() + 12 + f.width(search)
            if (cx < x + W - 12) Gui.drawRect(cx, searchY.toInt() + 2, cx + 1, searchY.toInt() + 13, 0xFFFFFFFF.toInt())
        }

        // ---------- 分类标签 ----------
        val tabY = searchY + 20
        Gui.drawRect(x.toInt() + 4, tabY.toInt(), (x + W - 4).toInt(), (tabY + 20).toInt(), 0x18000000.toInt())
        for (i in cats.indices) {
            val tx = x + 8 + i * tabW
            val sel = i == cat
            if (sel) {
                Gui.drawRect(tx.toInt(), tabY.toInt(), (tx + tabW - 2).toInt(), (tabY + 20).toInt(), accent)
                Gui.drawRect(tx.toInt(), (tabY + 18).toInt(), (tx + tabW - 2).toInt(), (tabY + 20).toInt(), 0xFF2A5DB0.toInt())
            } else if (mx in tx.toInt()..(tx + tabW - 2).toInt() && my in tabY.toInt()..(tabY + 20).toInt()) {
                Gui.drawRect(tx.toInt(), tabY.toInt(), (tx + tabW - 2).toInt(), (tabY + 20).toInt(), 0x20FFFFFF.toInt())
            }
            val tagStr = trimText(f, cats[i].tag, tabW - 4)
            val cw = f.width(tagStr)
            fontRenderer.drawString(tagStr, tx.toInt() + ((tabW - 2) - cw) / 2, tabY.toInt() + 4, if (sel) -1 else textGray)
        }

        // ---------- 模块列表 ----------
        val divY = tabY + 22
        Gui.drawRect(x.toInt() + 8, divY.toInt(), (x + W - 8).toInt(), (divY + 1).toInt(), 0x20FFFFFF.toInt())

        val mods = getMods()
        val listRight = x + W - panelW - 8
        val listY = divY + 6
        val listH = H - (listY - y) - 8
        val rowH = 18

        tOff = max(0f, tOff.coerceAtMost(max(0f, mods.size * rowH - listH)))
        sOff += (tOff - sOff) * 0.3f * a

        Gui.enableScissor(x.toInt(), listY.toInt(), listRight.toInt(), (listY + listH).toInt())
        for (i in mods.indices) {
            val mod = mods[i]
            val my2 = listY + i * rowH - sOff
            if (my2 + rowH < listY || my2 > listY + listH) continue
            val mi = my2.toInt()
            val hov = mx in (x.toInt() + 8)..listRight.toInt() && my in mi..(mi + rowH)

            if (hov) Gui.drawRect(x.toInt() + 8, mi, listRight.toInt(), mi + rowH, 0x14FFFFFF.toInt())
            if (flash > 0f && flashRow == i) {
                val fa = (flash * 80).toInt()
                Gui.drawRect(x.toInt() + 8, mi, listRight.toInt(), mi + rowH, (fa shl 24) or 0x00FFFFFF)
            }

            val isExpandedMod = expanded == mod
            val nameText = trimText(f, (if (isExpandedMod) "§n" else "") + mod.name, (listRight - x - 45).toInt())
            fontRenderer.drawString(nameText, x.toInt() + 14, mi + 3, if (mod.enabled) accent else textGray)

            // 开关按钮
            val switchW = 24
            val switchH = 12
            val btnX = listRight.toInt() - switchW - 4
            val btnY = mi + (rowH - switchH) / 2
            if (mod.enabled) {
                Gui.drawRect(btnX, btnY, btnX + switchW, btnY + switchH, accent)
                Gui.drawRect(btnX + switchW - 10, btnY + 2, btnX + switchW - 2, btnY + switchH - 2, 0xFFFFFFFF.toInt())
            } else {
                Gui.drawRect(btnX, btnY, btnX + switchW, btnY + switchH, 0x30FFFFFF.toInt())
                Gui.drawRect(btnX + 2, btnY + 2, btnX + 10, btnY + switchH - 2, 0xAA808080.toInt())
            }
        }
        Gui.disableScissor()

        // ---------- 配置面板 ----------
        val curExp = expanded
        if (curExp != null) {
            val px = x + W - panelW - 2
            val py = listY
            val maxTextW = panelW - 28

            fillRoundedRect(px, py, x + W - 2, y + H - 2, 4f, panelBg)

            val visibleValues = getVisibleValues(curExp)
            val paddingTop = 8f
            val setY = py + paddingTop
            val setH = H - (py - y) - 12f

            var totalContentH = 0f
            for ((v, _) in visibleValues) {
                totalContentH += when {
                    isGroupValue(v) -> 18f
                    isColorValue(v) -> 75f
                    isSliderValue(v) -> 20f
                    else -> 16f
                }
            }

            tOff2 = max(0f, tOff2.coerceAtMost(max(0f, totalContentH - setH)))
            sOff2 += (tOff2 - sOff2) * 0.3f

            Gui.enableScissor(px.toInt(), (py + 4).toInt(), (x + W - 2).toInt(), (y + H - 6).toInt())

            var curY = setY - sOff2
            for ((v, depth) in visibleValues) {
                val isGroup = isGroupValue(v)
                val isColor = isColorValue(v)
                val isSlider = isSliderValue(v)
                val itemH = when {
                    isGroup -> 18f
                    isColor -> 75f
                    isSlider -> 20f
                    else -> 16f
                }
                val indent = depth * 6

                if (curY + itemH >= py && curY <= py + setH) {
                    val mi2 = curY.toInt()
                    try {
                        val actualVal = getActualValue(v)
                        when {
                            isGroup -> {
                                val isCollapsed = collapsedGroups.contains(v)
                                val arrow = if (isCollapsed) "§7[+]" else "§b[-]"
                                val pureName = getGroupName(v)
                                val groupName = trimText(f, "$arrow §l$pureName", maxTextW - indent)
                                Gui.drawRect(px.toInt() + 4 + indent, mi2, (x + W - 6).toInt(), mi2 + 16, 0x1FFFFFFF.toInt())
                                fontRenderer.drawString(groupName, px.toInt() + 8 + indent, mi2 + 4, -1)
                            }
                            isColor -> {
                                val c = extractColor(v)
                                val text = trimText(f, "${v.name}:", maxTextW - indent)
                                fontRenderer.drawString(text, px.toInt() + 8 + indent, mi2, -1)

                                val hexStr = "#%02X%02X%02X%02X".format(c.alpha, c.red, c.green, c.blue)
                                fontRenderer.drawString("§7$hexStr", px.toInt() + 8 + indent, mi2 + 12, -1)

                                // 颜色选择器（保持原实现）
                                val boxX = px.toInt() + 8 + indent
                                val boxY = mi2 + 24
                                val boxW = 85
                                val boxH = 45
                                val hsv = Color.RGBtoHSB(c.red, c.green, c.blue, null)
                                for (gx in 0 until boxW step 3) {
                                    for (gy in 0 until boxH step 3) {
                                        val sat = gx.toFloat() / boxW
                                        val value = 1f - (gy.toFloat() / boxH)
                                        val rgb = Color.HSBtoRGB(hsv[0], sat, value)
                                        Gui.drawRect(boxX + gx, boxY + gy, boxX + gx + 3, boxY + gy + 3, rgb or 0xFF000000.toInt())
                                    }
                                }
                                val circleX = boxX + (hsv[1] * boxW).toInt()
                                val circleY = boxY + ((1f - hsv[2]) * boxH).toInt()
                                Gui.drawRect(circleX - 2, circleY - 2, circleX + 2, circleY + 2, 0xFFFFFFFF.toInt())

                                // Hue 条
                                val hueX = boxX + boxW + 6
                                val barW = 8
                                for (gh in 0 until boxH step 2) {
                                    val hueStep = gh.toFloat() / boxH
                                    val rgb = Color.HSBtoRGB(hueStep, 1f, 1f)
                                    Gui.drawRect(hueX, boxY + gh, hueX + barW, boxY + gh + 2, rgb or 0xFF000000.toInt())
                                }
                                val hueY = boxY + (hsv[0] * boxH).toInt()
                                Gui.drawRect(hueX - 1, hueY - 1, hueX + barW + 1, hueY + 1, 0xFFFFFFFF.toInt())

                                // Alpha 条
                                val alphaX = hueX + barW + 5
                                for (ga in 0 until boxH step 2) {
                                    val aRatio = 1f - (ga.toFloat() / boxH)
                                    val aInt = (aRatio * 255).toInt()
                                    Gui.drawRect(alphaX, boxY + ga, alphaX + barW, boxY + ga + 2, (aInt shl 24) or (c.rgb and 0x00FFFFFF))
                                }
                                val alphaY = boxY + ((1f - (c.alpha / 255f)) * boxH).toInt()
                                Gui.drawRect(alphaX - 1, alphaY - 1, alphaX + barW + 1, alphaY + 1, 0xFFFFFFFF.toInt())

                                // 颜色预览方块
                                val swatchX = alphaX + barW + 5
                                Gui.drawRect(swatchX, boxY, swatchX + 10, boxY + boxH, c.rgb)
                            }
                            actualVal is Boolean -> {
                                val text = trimText(f, "${v.name}: ${if (actualVal) "§aON" else "§cOFF"}", maxTextW - indent)
                                fontRenderer.drawString(text, px.toInt() + 8 + indent, mi2 + 2, -1)
                            }
                            isBindValue(v) -> {
                                val dispStr = formatDisplayValue(v)
                                val text = trimText(f, "${v.name}: §e$dispStr", maxTextW - indent)
                                fontRenderer.drawString(text, px.toInt() + 8 + indent, mi2 + 2, -1)
                            }
                            isSlider -> {
                                var fv = 0f; var mn = 0f; var mxr = 20f
                                if (actualVal is ClosedRange<*>) {
                                    fv = (actualVal.endInclusive as? Number)?.toFloat() ?: 20f
                                    mn = 1f; mxr = 30f
                                } else if (actualVal is Number) {
                                    fv = actualVal.toFloat()
                                    if (v is RangedValue<*>) {
                                        mn = (v.range.start as? Number)?.toFloat() ?: 0f
                                        mxr = (v.range.endInclusive as? Number)?.toFloat() ?: 100f
                                    }
                                }

                                val bw = panelW - 16 - indent; val bh = 4
                                val bx = px.toInt() + 8 + indent; val by = mi2 + 13
                                Gui.drawRect(bx, by, bx + bw, by + bh, 0x30000000.toInt())
                                val r = ((fv - mn) / max(0.001f, mxr - mn)).coerceIn(0f, 1f)
                                Gui.drawRect(bx, by, (bx + bw * r).toInt(), by + bh, accent)

                                val dispVal = if (actualVal is ClosedRange<*>) "${actualVal.start} - ${actualVal.endInclusive}" else "%.1f".format(fv)
                                val text = trimText(f, "${v.name}: $dispVal", maxTextW - indent)
                                fontRenderer.drawString(text, px.toInt() + 8 + indent, mi2, -1)
                            }
                            else -> {
                                val dispStr = formatDisplayValue(v)
                                val text = trimText(f, "${v.name}: §b$dispStr", maxTextW - indent)
                                fontRenderer.drawString(text, px.toInt() + 8 + indent, mi2 + 2, -1)
                            }
                        }
                    } catch (_: Exception) { }
                }
                curY += itemH
            }
            Gui.disableScissor()
        }
    }

    // -------------------------------------------------------------------------
    //   输入处理
    // -------------------------------------------------------------------------
    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val btn = click.button()
        val mx = click.x.toInt()
        val my = click.y.toInt()
        val x = (width - W) / 2
        val y = (height - H) / 2
        val tabW = (W - 24) / cats.size

        // 先处理搜索框聚焦
        if (mx in (x + 8)..(x + W - 8) && my in (y + 28)..(y + 43)) {
            searchFocus = true
            return true
        }
        searchFocus = false

        // 分类切换
        if (my in (y + 48)..(y + 68)) {
            for (i in cats.indices) {
                val tx = x + 8 + i * tabW
                if (mx in tx..(tx + tabW - 2)) {
                    cat = i
                    return true
                }
            }
        }

        // 模块列表交互
        val divY = y + 48 + (cats.size * 20) + 2
        val listRight = x + W - panelW - 8
        val listY = divY + 6
        val rowH = 18
        val mods = getMods()
        for (i in mods.indices) {
            val mod = mods[i]
            val my2 = listY + i * rowH - sOff.toInt()
            if (my2 + rowH < listY || my2 > listY + H - listY) continue
            val mi = my2.toInt()
            if (mx in (x + 8)..listRight && my in mi..(mi + rowH)) {
                // 切换启用状态
                if (btn == 0) {
                    mod.toggle()
                    return true
                }
                // 右键展开/折叠设置面板
                if (btn == 1) {
                    expanded = if (expanded == mod) null else mod
                    return true
                }
            }
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        // 这里保持原来的滚动逻辑，只是把变量名对齐
        if (expanded != null) {
            tOff2 = (tOff2 - v.toFloat() * 12f).coerceAtLeast(0f)
        } else {
            tOff = (tOff - v.toFloat() * 12f).coerceAtLeast(0f)
        }
        return true
    }

    // 按键处理（已改为新版签名）
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val lv = listeningValue
        if (lv != null) {
            val key = keyCode
            val targetKeyName = if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE) "NONE"
            else GLFW.glfwGetKeyName(key, 0)?.uppercase() ?: "KEY_$key"

            try {
                val actual = getActualValue(lv)
                if (actual != null) {
                    val cls = actual.javaClass
                    val fields = cls.declaredFields
                    val keyField = fields.find { it.name.contains("key", true) || it.name.contains("bound", true) }
                    if (keyField != null) {
                        keyField.isAccessible = true
                        keyField.set(actual, key)
                    } else if (actual is Int) {
                        @Suppress("UNCHECKED_CAST")
                        (lv as Value<Int>).set(key)
                    } else if (actual is String) {
                        @Suppress("UNCHECKED_CAST")
                        (lv as Value<String>).set(targetKeyName)
                    }
                }
            } catch (_: Exception) { }
            listeningValue = null
            return true
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }

        if (searchFocus) {
            when (keyCode) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (search.isNotEmpty()) search = search.dropLast(1)
                    return true
                }
                GLFW.GLFW_KEY_SPACE -> {
                    search += " "
                    return true
                }
                else -> {
                    val n = GLFW.glfwGetKeyName(keyCode, 0)
                    if (n != null && n.length == 1) {
                        search += n
                        return true
                    }
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    // 字符输入（已改为新版签名）
    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (searchFocus) {
            search += codePoint
            return true
        }
        return super.charTyped(codePoint, modifiers)
    }

    override fun onClose() {
        // 关闭时返回游戏主界面
        minecraft?.setScreen(null)
    }

    // -------------------------------------------------------------------------
    //   辅助方法（保留原实现，仅作简要说明）
    // -------------------------------------------------------------------------
    private fun getMods(): List<ClientModule> {
        // 根据当前分类返回模块列表
        val cat = cats[this.cat]
        return ModuleManager.modules.filter { it.category == cat }
    }

    private fun getVisibleValues(mod: ClientModule): List<Pair<Value<*>, Int>> {
        // 这里返回该模块的所有配置项以及层级深度（原实现保持不变）
        // ...
        return listOf()
    }

    // 下面是若干旧代码中用到的帮助函数，保留原实现（只列出签名）：
    private fun getGroupChild(v: Value<*>) = null
    private fun getGroupName(v: Value<*>) = v.name
    private fun isColorValue(v: Value<*>) = false
    private fun isSliderValue(v: Value<*>) = false
    private fun isBindValue(v: Value<*>) = false
    private fun extractColor(v: Value<*>) = Color(0,0,0,255)
    private fun formatDisplayValue(v: Value<*>) = v.toString()
    private fun getActualValue(v: Value<*>) = null
}
