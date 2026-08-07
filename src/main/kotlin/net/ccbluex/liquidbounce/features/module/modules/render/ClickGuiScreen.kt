package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import com.mojang.blaze3d.systems.RenderSystem

class ClickGuiScreen : Screen(Component.literal("ClickGUI")) {
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

    private fun fillRoundedRect(guiGraphics: GuiGraphics, x1: Float, y1: Float, x2: Float, y2: Float, radius: Float, color: Int) {
        val r = radius.coerceAtMost((x2 - x1) / 2f).coerceAtMost((y2 - y1) / 2f)
        guiGraphics.fill((x1 + r).toInt(), y1.toInt(), (x2 - r).toInt(), y2.toInt(), color)
        guiGraphics.fill(x1.toInt(), (y1 + r).toInt(), (x1 + r).toInt(), (y2 - r).toInt(), color)
        guiGraphics.fill((x2 - r).toInt(), (y1 + r).toInt(), x2.toInt(), (y2 - r).toInt(), color)

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
                guiGraphics.fill(minX, minY, max(minX + 1, maxX), max(minY + 1, maxY), color)
                a += 10f
            }
        }
    }

    private fun trimText(font: Font, text: String, maxW: Int): String {
        if (font.width(text) <= maxW) return text
        var str = text
        while (str.isNotEmpty() && font.width("$str...") > maxW) {
            str = str.substring(0, str.length - 1)
        }
        return "$str..."
    }

    private fun isGroupValue(v: Value<*>): Boolean {
        val clsName = v.javaClass.simpleName
        if (clsName.contains("Group", true) || clsName.contains("Container", true)) return true
        return getGroupChild(v) != null
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
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
        fillRoundedRect(guiGraphics, x, y, x + W, y + H, R, bg)
        Gui.drawRect(x.toInt() + R.toInt(), y.toInt(), (x + W - R).toInt(), (y + 24).toInt(), headerBg)
        guiGraphics.drawString(fontRenderer, "§lClickGUI", x.toInt() + 10, y.toInt() + 5, accent)

        val searchY = y + 28
        Gui.drawRect(x.toInt() + 8, searchY.toInt(), (x + W - 8).toInt(), (searchY + 15).toInt(), 0x28000000.toInt())
        val disp = if (search.isEmpty()) "§7Search modules..." else "§f$search"
        guiGraphics.drawString(fontRenderer, trimText(f, disp, W - 30), x.toInt() + 12, searchY.toInt() + 2, -1)
        if (searchFocus) {
            val cx = x.toInt() + 12 + f.width(search)
            if (cx < x + W - 12) Gui.drawRect(cx, searchY.toInt() + 2, cx + 1, searchY.toInt() + 13, 0xFFFFFFFF.toInt())
        }

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
            guiGraphics.drawString(fontRenderer, tagStr, tx.toInt() + ((tabW - 2) - cw) / 2, tabY.toInt() + 4, if (sel) -1 else textGray)
        }

        val divY = tabY + 22
        Gui.drawRect(x.toInt() + 8, divY.toInt(), (x + W - 8).toInt(), (divY + 1).toInt(), 0x20FFFFFF.toInt())
        val mods = getMods()
        val listRight = x + W - panelW - 8
        val listY = divY + 6
        val listH = H - (listY - y) - 8
        val rowH = 18
        tOff = max(0f, tOff.coerceAtMost(max(0f, mods.size * rowH - listH)))
        sOff += (tOff - sOff) * 0.3f * a
        RenderSystem.enableScissor(x.toInt(), listY.toInt(), listRight.toInt(), (listY + listH).toInt())
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
            guiGraphics.drawString(fontRenderer, nameText, x.toInt() + 14, mi + 3, if (mod.enabled) accent else textGray)
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
        RenderSystem.disableScissor()

        val curExp = expanded
        if (curExp != null) {
            val px = x + W - panelW - 2
            val py = listY
            val maxTextW = panelW - 28
            fillRoundedRect(guiGraphics, px, py, x + W - 2, y + H - 2, 4f, panelBg)
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
            RenderSystem.enableScissor(px.toInt(), (py + 4).toInt(), (x + W - 2).toInt(), (y + H - 6).toInt())
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
                                guiGraphics.drawString(fontRenderer, groupName, px.toInt() + 8 + indent, mi2 + 4, -1)
                            }
                            isColor -> {
                                val c = extractColor(v)
                                val text = trimText(f, "${v.name}:", maxTextW - indent)
                                guiGraphics.drawString(fontRenderer, text, px.toInt() + 8 + indent, mi2, -1)
                                val hexStr = "#%02X%02X%02X%02X".format(c.alpha, c.red, c.green, c.blue)
                                guiGraphics.drawString(fontRenderer, "§7$hexStr", px.toInt() + 8 + indent, mi2 + 12, -1)
                                // … (color picker drawing omitted for brevity) …
                            }
                            actualVal is Boolean -> {
                                val text = trimText(f, "${v.name}: ${if (actualVal) "§aON" else "§cOFF"}", maxTextW - indent)
                                guiGraphics.drawString(fontRenderer, text, px.toInt() + 8 + indent, mi2 + 2, -1)
                            }
                            isBindValue(v) -> {
                                val dispStr = formatDisplayValue(v)
                                val text = trimText(f, "${v.name}: §e$dispStr", maxTextW - indent)
                                guiGraphics.drawString(fontRenderer, text, px.toInt() + 8 + indent, mi2 + 2, -1)
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
                                guiGraphics.drawString(fontRenderer, text, px.toInt() + 8 + indent, mi2, -1)
                            }
                            else -> {
                                val dispStr = formatDisplayValue(v)
                                val text = trimText(f, "${v.name}: §b$dispStr", maxTextW - indent)
                                guiGraphics.drawString(fontRenderer, text, px.toInt() + 8 + indent, mi2 + 2, -1)
                            }
                        }
                    } catch (_: Exception) {}
                }
                curY += itemH
            }
            RenderSystem.disableScissor()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        if (expanded != null) {
            tOff2 = (tOff2 - v.toFloat() * 12f).coerceAtLeast(0f)
        } else {
            tOff = (tOff - v.toFloat() * 12f).coerceAtLeast(0f)
        }
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val lv = listeningValue
        if (lv != null) {
            val key = keyCode
            val targetKeyName = if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE) "NONE" else GLFW.glfwGetKeyName(key, 0)?.uppercase() ?: "KEY_$key"
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
            } catch (_: Exception) {}
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

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (searchFocus) {
            search += codePoint
            return true
        }
        return super.charTyped(codePoint, modifiers)
    }

    override fun onClose() {
        client?.setScreen(null)
    }

    private fun getMods(): List<ClientModule> = ModuleManager.modules.filter { it.category == cats[cat] }
    private fun getVisibleValues(mod: ClientModule): List<Pair<Value<*>, Int>> = listOf()
    private fun getGroupChild(v: Value<*>) = null
    private fun getGroupName(v: Value<*>) = v.name
    private fun isColorValue(v: Value<*>) = false
    private fun isSliderValue(v: Value<*>) = false
    private fun isBindValue(v: Value<*>) = false
    private fun extractColor(v: Value<*>) = Color(0,0,0,255)
    private fun formatDisplayValue(v: Value<*>) = v.toString()
    private fun getActualValue(v: Value<*>) = null
}
