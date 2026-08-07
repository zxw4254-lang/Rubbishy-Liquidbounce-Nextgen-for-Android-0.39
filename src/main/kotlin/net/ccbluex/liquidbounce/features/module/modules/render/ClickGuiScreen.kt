package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.minecraft.client.Minecraft
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class ClickGuiScreen : Screen(Text.literal("ClickGUI")) {

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

    private val cats: List<Any?> = try {
        val categoriesClass = ModuleCategories::class.java
        if (categoriesClass.isEnum) {
            categoriesClass.enumConstants?.toList() ?: emptyList()
        } else {
            val valuesMethod = categoriesClass.methods.find { it.name == "values" || it.name == "getEntries" }
            if (valuesMethod != null) {
                (valuesMethod.invoke(null) as? Array<*>)?.toList() ?: emptyList()
            } else {
                categoriesClass.declaredFields
                    .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                    .mapNotNull { field ->
                        runCatching {
                            field.isAccessible = true
                            field.get(null)
                        }.getOrNull()
                    }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private val W = 450; private val H = 320
    private val panelW = 170

    private val accent = 0xFF4182E1.toInt()
    private val bg = 0xE80C0C10.toInt()
    private val panelBg = 0xE814141A.toInt()
    private val headerBg = 0xF0000000.toInt()
    private val textGray = 0xFFA0A0AA.toInt()

    override fun shouldPause() = false
    override fun shouldCloseOnEsc() = true

    // 反射兼容绘制文字
    private fun drawText(matrices: Any?, text: String, x: Int, y: Int, color: Int) {
        val mc = Minecraft.getInstance()
        val textRenderer = mc.textRenderer
        try {
            val drawMethod = textRenderer.javaClass.methods.find { 
                it.name == "draw" || it.name == "drawString" || it.name == "drawWithShadow"
            }
            if (drawMethod != null) {
                when (drawMethod.parameterCount) {
                    5 -> drawMethod.invoke(textRenderer, matrices, text, x.toFloat(), y.toFloat(), color)
                    6 -> drawMethod.invoke(textRenderer, matrices, text, x.toFloat(), y.toFloat(), color, false)
                    else -> textRenderer.draw(matrices as MatrixStack, text, x.toFloat(), y.toFloat(), color)
                }
            }
        } catch (_: Exception) {
            try {
                textRenderer.draw(matrices as MatrixStack, text, x.toFloat(), y.toFloat(), color)
            } catch (_: Exception) {}
        }
    }

    // 纯 GL / 反射兼容填充矩形
    private fun fillRect(matrices: Any?, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        try {
            val drawableHelper = Class.forName("net.minecraft.client.gui.DrawableHelper")
            val fillMethod = drawableHelper.methods.find { it.name == "fill" }
            if (fillMethod != null) {
                fillMethod.invoke(null, matrices, x1, y1, x2, y2, color)
                return
            }
        } catch (_: Exception) {}

        // Fallback 直接使用 GL 绘制
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        
        val a = (color shr 24 and 255) / 255.0f
        val r = (color shr 16 and 255) / 255.0f
        val g = (color shr 8 and 255) / 255.0f
        val b = (color and 255) / 255.0f
        GL11.glColor4f(r, g, b, a)

        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex2f(x1.toFloat(), y2.toFloat())
        GL11.glVertex2f(x2.toFloat(), y2.toFloat())
        GL11.glVertex2f(x2.toFloat(), y1.toFloat())
        GL11.glVertex2f(x1.toFloat(), y1.toFloat())
        GL11.glEnd()

        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_BLEND)
    }

    private fun safeEnableScissor(x1: Int, y1: Int, x2: Int, y2: Int) {
        val mc = Minecraft.getInstance()
        val window = mc.window
        val scale = window.scaleFactor
        val windowH = window.height
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(
            (x1 * scale).toInt(),
            (windowH - (y2 * scale)).toInt(),
            ((x2 - x1) * scale).toInt(),
            ((y2 - y1) * scale).toInt()
        )
    }

    private fun safeDisableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
    }

    private fun fillRoundedRect(matrices: Any?, x1: Float, y1: Float, x2: Float, y2: Float, radius: Float, color: Int) {
        val r = radius.coerceAtMost((x2 - x1) / 2f).coerceAtMost((y2 - y1) / 2f)
        fillRect(matrices, (x1 + r).toInt(), y1.toInt(), (x2 - r).toInt(), y2.toInt(), color)
        fillRect(matrices, x1.toInt(), (y1 + r).toInt(), (x1 + r).toInt(), (y2 - r).toInt(), color)
        fillRect(matrices, (x2 - r).toInt(), (y2 + r).toInt(), x2.toInt(), (y2 - r).toInt(), color)

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
                fillRect(matrices, minX, minY, max(minX + 1, maxX), max(minY + 1, maxY), color)
                a += 10f
            }
        }
    }

    private fun trimText(textRenderer: TextRenderer, text: String, maxW: Int): String {
        if (textRenderer.getWidth(text) <= maxW) return text
        var str = text
        while (str.isNotEmpty() && textRenderer.getWidth("$str...") > maxW) {
            str = str.substring(0, str.length - 1)
        }
        return "$str..."
    }

    private fun getCategoryTag(catObj: Any?): String {
        if (catObj == null) return "Unknown"
        if (catObj is Enum<*>) return catObj.name
        try {
            val f = catObj.javaClass.declaredFields.find { 
                it.name.equals("tag", true) || it.name.equals("name", true) || it.name.equals("displayName", true) 
            }
            if (f != null) {
                f.isAccessible = true
                val v = f.get(catObj) as? String
                if (!v.isNullOrBlank()) return v
            }
        } catch (_: Exception) {}
        return catObj.toString()
    }

    private fun isGroupValue(v: Value<*>): Boolean {
        val clsName = v.javaClass.simpleName
        if (clsName.contains("Group", true) || clsName.contains("Container", true)) return true
        return getGroupChildren(v).isNotEmpty()
    }

    private fun getGroupName(v: Value<*>): String {
        try {
            val nameProp = v.javaClass.methods.find { it.name == "getName" && it.parameterCount == 0 }
            if (nameProp != null) {
                val res = nameProp.invoke(v) as? String
                if (!res.isNullOrBlank() && res != "null") return res
            }
            val fieldProp = v.javaClass.fields.find { it.name.equals("name", true) }
            if (fieldProp != null) {
                val res = fieldProp.get(v) as? String
                if (!res.isNullOrBlank() && res != "null") return res
            }
        } catch (_: Exception) {}
        
        var fallback = v.name ?: v.javaClass.simpleName.replace("Value", "").replace("Group", "")
        if (fallback.isBlank() || fallback == "null") fallback = "Settings"
        return fallback
    }

    private fun getGroupChildren(v: Value<*>): List<Value<*>> {
        val list = mutableListOf<Value<*>>()
        try {
            for (m in v.javaClass.methods) {
                if ((m.name.equals("getValues", true) || m.name.equals("getSubValues", true)) && m.parameterCount == 0) {
                    val res = m.invoke(v)
                    if (res is Collection<*>) list.addAll(res.filterIsInstance<Value<*>>())
                    if (res != null && res.javaClass.isArray) list.addAll((res as Array<*>).filterIsInstance<Value<*>>())
                }
            }
            for (f in v.javaClass.declaredFields) {
                f.isAccessible = true
                val fVal = f.get(v)
                if (fVal is Value<*>) list.add(fVal)
                else if (fVal is Collection<*>) list.addAll(fVal.filterIsInstance<Value<*>>())
                else if (fVal != null && fVal.javaClass.isArray) list.addAll((fVal as Array<*>).filterIsInstance<Value<*>>())
            }
            val obj = v.get()
            if (obj != null && obj !is Number && obj !is String && obj !is Boolean && obj !is Enum<*>) {
                if (obj is Collection<*>) list.addAll(obj.filterIsInstance<Value<*>>())
                else if (obj.javaClass.isArray) list.addAll((obj as Array<*>).filterIsInstance<Value<*>>())
                else {
                    for (f in obj.javaClass.declaredFields) {
                        f.isAccessible = true
                        val fVal = f.get(obj)
                        if (fVal is Value<*>) list.add(fVal)
                    }
                }
            }
        } catch (_: Exception) {}
        return list.distinct()
    }

    private fun getVisibleValues(module: ClientModule): List<Pair<Value<*>, Int>> {
        val result = mutableListOf<Pair<Value<*>, Int>>()
        val rawValues = try {
            module.collectValuesRecursively()
        } catch (e: Exception) {
            try {
                module.javaClass.getMethod("getValues").invoke(module)
            } catch (ex: Exception) {
                emptyList<Value<*>>()
            }
        }

        val topValues: List<Value<*>> = when (rawValues) {
            is Iterable<*> -> rawValues.filterIsInstance<Value<*>>()
            is Array<*> -> rawValues.filterIsInstance<Value<*>>()
            else -> emptyList()
        }

        val visited = mutableSetOf<Value<*>>()

        fun process(v: Value<*>, depth: Int) {
            if (!visited.add(v)) return
            result.add(Pair(v, depth))
            if (isGroupValue(v)) {
                if (!collapsedGroups.contains(v)) {
                    val children = getGroupChildren(v)
                    for (child in children) {
                        process(child, depth + 1)
                    }
                }
            }
        }

        for (v in topValues) {
            var isChildOfAny = false
            for (other in topValues) {
                if (other != v && isGroupValue(other)) {
                    val children = getGroupChildren(other)
                    if (children.contains(v)) {
                        isChildOfAny = true
                        break
                    }
                }
            }
            if (!isChildOfAny) process(v, 0)
        }
        return result
    }

    private fun getActualValue(v: Value<*>): Any? {
        var obj: Any? = try { v.get() } catch (_: Exception) { null } ?: return null
        var depth = 0
        while (obj is Value<*> && depth < 5) {
            obj = try { obj.get() } catch (_: Exception) { null }
            depth++
        }
        if (obj is Collection<*>) {
            if (obj.isEmpty()) return "NONE"
            val first = obj.firstOrNull() ?: return "NONE"
            if (first is Value<*>) return getActualValue(first)
            return first
        } else if (obj != null && obj.javaClass.isArray) {
            val arr = obj as Array<*>
            if (arr.isEmpty()) return "NONE"
            val first = arr.firstOrNull() ?: return "NONE"
            if (first is Value<*>) return getActualValue(first)
            return first
        }
        return obj
    }

    private fun isBindValue(v: Value<*>): Boolean {
        val name = v.name.lowercase()
        if (name.contains("key") || name.contains("bind")) return true
        val actual = getActualValue(v) ?: return false
        val clsName = actual.javaClass.simpleName.lowercase()
        return clsName.contains("key") || clsName.contains("bind")
    }

    private fun formatDisplayValue(v: Value<*>): String {
        if (v == listeningValue) return "[LISTENING...]"
        val actual = getActualValue(v) ?: return "NONE"
        try {
            val cls = actual.javaClass
            val keyField = cls.declaredFields.find { it.name.equals("boundKey", true) || it.name.equals("key", true) || it.name.equals("name", true) }
            if (keyField != null) {
                keyField.isAccessible = true
                val innerKey = keyField.get(actual)
                if (innerKey != null) {
                    val keyStr = innerKey.toString().replace("key.keyboard.", "", ignoreCase = true).replace("key.", "", ignoreCase = true).uppercase()
                    if (keyStr.isNotEmpty()) return keyStr
                }
            }
        } catch (_: Exception) {}
        var str = actual.toString()
        if (str.contains("Value(") || str.contains("name=")) {
            val match = Regex("""name=([^,\s\)]+)""").find(str)
            if (match != null) return match.groupValues[1].uppercase()
        }
        str = str.replace("key.keyboard.", "", ignoreCase = true).replace("key.", "", ignoreCase = true).replace("InputBind", "", ignoreCase = true)
        if (str.startsWith("(") && str.endsWith(")")) str = str.substring(1, str.length - 1)
        return if (str.isBlank()) "NONE" else str.take(18).uppercase()
    }

    private fun isSliderValue(v: Value<*>): Boolean {
        val obj = getActualValue(v) ?: return false
        return obj is Number || obj is ClosedRange<*> || v is RangedValue<*>
    }

    private fun isColorValue(v: Value<*>): Boolean {
        val obj = getActualValue(v) ?: return false
        if (obj is Color) return true
        val className = obj.javaClass.simpleName
        return className.contains("Color", true) || v.name.contains("Color", true)
    }

    private fun extractColor(v: Value<*>): Color {
        val valObj = getActualValue(v) ?: return Color.WHITE
        if (valObj is Color) return valObj
        if (valObj is Number) return Color(valObj.toInt(), true)
        try {
            val cls = valObj.javaClass
            val rgbMethod = cls.methods.find { it.name == "getRGB" || it.name == "rgb" }
            if (rgbMethod != null) {
                val rgb = (rgbMethod.invoke(valObj) as Number).toInt()
                return Color(rgb, true)
            }
        } catch (_: Exception) {}
        return Color.WHITE
    }

    private fun updateColorValue(v: Value<*>, color: Color) {
        val actual = getActualValue(v)
        try {
            if (actual is Color) {
                @Suppress("UNCHECKED_CAST")
                (v as Value<Color>).set(color)
            } else {
                @Suppress("UNCHECKED_CAST")
                (v as Value<Int>).set(color.rgb)
            }
        } catch (_: Exception) {}
    }

    private fun toggleNextValue(v: Value<*>) {
        val cls = v.javaClass
        try {
            val nextMethod = cls.methods.find { (it.name == "next" || it.name == "toggle" || it.name == "setNext") && it.parameterCount == 0 }
            if (nextMethod != null) {
                nextMethod.invoke(v)
                return
            }
        } catch (_: Exception) {}
        val actual = getActualValue(v)
        if (actual is Enum<*>) {
            val constants = actual.javaClass.enumConstants
            if (constants != null && constants.isNotEmpty()) {
                val nextIdx = (actual.ordinal + 1) % constants.size
                val nextVal: Any = constants[nextIdx]
                try {
                    @Suppress("UNCHECKED_CAST")
                    (v as Value<Any>).set(nextVal)
                } catch (_: Exception) {}
                return
            }
        }
        if (actual is Boolean) {
            try {
                @Suppress("UNCHECKED_CAST")
                (v as Value<Boolean>).set(!actual)
            } catch (_: Exception) {}
        }
    }

    // 正确覆盖旧版 Fabric/Yarn Screen 的 render 方法 (第一参数为 MatrixStack)
    override fun render(matrices: MatrixStack, mx: Int, my: Int, dt: Float) {
        val mc = Minecraft.getInstance()
        anim += (1f - anim) * 0.25f
        val a = anim.coerceIn(0f, 1f)
        if (a < 0.01f) return

        if (flash > 0f) flash -= dt / 3f
        else flash = 0f

        val windowWidth = mc.window.scaledWidth
        val windowHeight = mc.window.scaledHeight
        val x = (windowWidth - W) / 2f
        val y = (windowHeight - H) / 2f
        val f = mc.textRenderer
        val safeCatSize = max(1, cats.size)
        val tabW = (W - 24) / safeCatSize

        val R = 8f
        fillRoundedRect(matrices, x, y, x + W, y + H, R, bg)
        
        fillRect(matrices, x.toInt() + R.toInt(), y.toInt(), (x + W - R).toInt(), (y + 24).toInt(), headerBg)
        drawText(matrices, "§lClickGUI", x.toInt() + 10, y.toInt() + 5, accent)

        val searchY = y + 28
        fillRect(matrices, x.toInt() + 8, searchY.toInt(), (x + W - 8).toInt(), (searchY + 15).toInt(), 0x28000000.toInt())
        val disp = if (search.isEmpty()) "§7Search modules..." else "§f$search"
        drawText(matrices, trimText(f, disp, W - 30), x.toInt() + 12, searchY.toInt() + 2, -1)
        if (searchFocus) {
            val cx = x.toInt() + 12 + f.getWidth(search)
            if (cx < x + W - 12) fillRect(matrices, cx, searchY.toInt() + 2, cx + 1, searchY.toInt() + 13, 0xFFFFFFFF.toInt())
        }

        val tabY = searchY + 20
        fillRect(matrices, x.toInt() + 4, tabY.toInt(), (x + W - 4).toInt(), (tabY + 20).toInt(), 0x18000000.toInt())
        for (i in cats.indices) {
            val tx = x + 8 + i * tabW
            val sel = i == cat
            if (sel) {
                fillRect(matrices, tx.toInt(), tabY.toInt(), (tx + tabW - 2).toInt(), (tabY + 20).toInt(), accent)
                fillRect(matrices, tx.toInt(), (tabY + 18).toInt(), (tx + tabW - 2).toInt(), (tabY + 20).toInt(), 0xFF2A5DB0.toInt())
            } else if (mx in tx.toInt()..(tx + tabW - 2).toInt() && my in tabY.toInt()..(tabY + 20).toInt()) {
                fillRect(matrices, tx.toInt(), tabY.toInt(), (tx + tabW - 2).toInt(), (tabY + 20).toInt(), 0x20FFFFFF.toInt())
            }
            val tagStr = trimText(f, getCategoryTag(cats[i]), tabW - 4)
            val cw = f.getWidth(tagStr)
            drawText(matrices, tagStr, tx.toInt() + ((tabW - 2) - cw) / 2, tabY.toInt() + 4, if (sel) -1 else textGray)
        }

        val divY = tabY + 22
        fillRect(matrices, x.toInt() + 8, divY.toInt(), (x + W - 8).toInt(), (divY + 1).toInt(), 0x20FFFFFF.toInt())

        val mods = getMods()
        val listRight = x + W - panelW - 8
        val listY = divY + 6
        val listH = H - (listY - y) - 8
        val rowH = 18

        tOff = max(0f, tOff.coerceAtMost(max(0f, mods.size * rowH - listH)))
        sOff += (tOff - sOff) * 0.3f * a

        safeEnableScissor(x.toInt(), listY.toInt(), listRight.toInt(), (listY + listH).toInt())

        for (i in mods.indices) {
            val mod = mods[i]
            val my2 = listY + i * rowH - sOff
            if (my2 + rowH < listY || my2 > listY + listH) continue
            val mi = my2.toInt()
            val hov = mx in (x.toInt() + 8)..listRight.toInt() && my in mi..(mi + rowH)

            if (hov) fillRect(matrices, x.toInt() + 8, mi, listRight.toInt(), mi + rowH, 0x14FFFFFF.toInt())
            if (flash > 0f && flashRow == i) {
                val fa = (flash * 80).toInt()
                fillRect(matrices, x.toInt() + 8, mi, listRight.toInt(), mi + rowH, (fa shl 24) or 0x00FFFFFF)
            }

            val isExpandedMod = expanded == mod
            val nameText = trimText(f, (if (isExpandedMod) "§n" else "") + mod.name, (listRight - x - 45).toInt())
            drawText(matrices, nameText, x.toInt() + 14, mi + 3, if (mod.enabled) accent else textGray)

            val switchW = 24
            val switchH = 12
            val btnX = listRight.toInt() - switchW - 4
            val btnY = mi + (rowH - switchH) / 2

            if (mod.enabled) {
                fillRect(matrices, btnX, btnY, btnX + switchW, btnY + switchH, accent)
                fillRect(matrices, btnX + switchW - 10, btnY + 2, btnX + switchW - 2, btnY + switchH - 2, 0xFFFFFFFF.toInt())
            } else {
                fillRect(matrices, btnX, btnY, btnX + switchW, btnY + switchH, 0x30FFFFFF.toInt())
                fillRect(matrices, btnX + 2, btnY + 2, btnX + 10, btnY + switchH - 2, 0xAA808080.toInt())
            }
        }
        safeDisableScissor()

        val curExp = expanded
        if (curExp != null) {
            val px = x + W - panelW - 2
            val py = listY
            val maxTextW = panelW - 28

            fillRoundedRect(matrices, px, py, x + W - 2, y + H - 2, 4f, panelBg)

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

            safeEnableScissor(px.toInt(), (py + 4).toInt(), (x + W - 2).toInt(), (y + H - 6).toInt())

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
                                
                                fillRect(matrices, px.toInt() + 4 + indent, mi2, (x + W - 6).toInt(), mi2 + 16, 0x1FFFFFFF.toInt())
                                drawText(matrices, groupName, px.toInt() + 8 + indent, mi2 + 4, -1)
                            }
                            isColor -> {
                                val c = extractColor(v)
                                val text = trimText(f, "${v.name}:", maxTextW - indent)
                                drawText(matrices, text, px.toInt() + 8 + indent, mi2, -1)

                                val hexStr = "#%02X%02X%02X%02X".format(c.alpha, c.red, c.green, c.blue)
                                drawText(matrices, "§7$hexStr", px.toInt() + 8 + indent, mi2 + 12, -1)

                                val boxX = px.toInt() + 8 + indent
                                val boxY = mi2 + 24
                                val boxW = 85
                                val boxH = 45

                                val hsv = Color.RGBtoHSB(c.red, c.green, c.blue, null)
                                for (gx in 0 until boxW step 3) {
                                    for (gy in 0 until boxH step 3) {
                                        val sat = gx.toFloat() / boxW
                                        val valStep = 1f - (gy.toFloat() / boxH)
                                        val rgb = Color.HSBtoRGB(hsv[0], sat, valStep)
                                        fillRect(matrices, boxX + gx, boxY + gy, boxX + gx + 3, boxY + gy + 3, rgb or 0xFF000000.toInt())
                                    }
                                }
                                val circleX = boxX + (hsv[1] * boxW).toInt()
                                val circleY = boxY + ((1f - hsv[2]) * boxH).toInt()
                                fillRect(matrices, circleX - 2, circleY - 2, circleX + 2, circleY + 2, 0xFFFFFFFF.toInt())

                                val hueX = boxX + boxW + 6
                                val barW = 8
                                for (gh in 0 until boxH step 2) {
                                    val hueStep = gh.toFloat() / boxH
                                    val rgb = Color.HSBtoRGB(hueStep, 1f, 1f)
                                    fillRect(matrices, hueX, boxY + gh, hueX + barW, boxY + gh + 2, rgb or 0xFF000000.toInt())
                                }
                                val hueY = boxY + (hsv[0] * boxH).toInt()
                                fillRect(matrices, hueX - 1, hueY - 1, hueX + barW + 1, hueY + 1, 0xFFFFFFFF.toInt())

                                val alphaX = hueX + barW + 5
                                for (ga in 0 until boxH step 2) {
                                    val aRatio = 1f - (ga.toFloat() / boxH)
                                    val aInt = (aRatio * 255).toInt()
                                    fillRect(matrices, alphaX, boxY + ga, alphaX + barW, boxY + ga + 2, (aInt shl 24) or (c.rgb and 0x00FFFFFF))
                                }
                                val alphaY = boxY + ((1f - (c.alpha / 255f)) * boxH).toInt()
                                fillRect(matrices, alphaX - 1, alphaY - 1, alphaX + barW + 1, alphaY + 1, 0xFFFFFFFF.toInt())

                                val swatchX = alphaX + barW + 5
                                fillRect(matrices, swatchX, boxY, swatchX + 10, boxY + boxH, c.rgb)
                            }
                            actualVal is Boolean -> {
                                val text = trimText(f, "${v.name}: ${if (actualVal) "§aON" else "§cOFF"}", maxTextW - indent)
                                drawText(matrices, text, px.toInt() + 8 + indent, mi2 + 2, -1)
                            }
                            isBindValue(v) -> {
                                val dispStr = formatDisplayValue(v)
                                val text = trimText(f, "${v.name}: §e$dispStr", maxTextW - indent)
                                drawText(matrices, text, px.toInt() + 8 + indent, mi2 + 2, -1)
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
                                fillRect(matrices, bx, by, bx + bw, by + bh, 0x30000000.toInt())
                                val r = ((fv - mn) / max(0.001f, mxr - mn)).coerceIn(0f, 1f)
                                fillRect(matrices, bx, by, (bx + bw * r).toInt(), by + bh, accent)

                                val dispVal = if (actualVal is ClosedRange<*>) "${actualVal.start} - ${actualVal.endInclusive}" else "%.1f".format(fv)
                                val text = trimText(f, "${v.name}: $dispVal", maxTextW - indent)
                                drawText(matrices, text, px.toInt() + 8 + indent, mi2, -1)
                            }
                            else -> {
                                val dispStr = formatDisplayValue(v)
                                val text = trimText(f, "${v.name}: §b$dispStr", maxTextW - indent)
                                drawText(matrices, text, px.toInt() + 8 + indent, mi2 + 2, -1)
                            }
                        }
                    } catch (_: Exception) {}
                }
                curY += itemH
            }

            safeDisableScissor()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mc = Minecraft.getInstance()
        val mx = mouseX.toInt()
        val my = mouseY.toInt()
        val x = (mc.window.scaledWidth - W) / 2
        val y = (mc.window.scaledHeight - H) / 2
        val safeCatSize = max(1, cats.size)
        val tabW = (W - 24) / safeCatSize

        if (mx in (x + 8)..(x + W - 8) && my in (y + 28)..(y + 43)) {
            searchFocus = true
            return true
        }
        searchFocus = false

        val tabY = y + 48
        if (button == 0 && my in tabY..(tabY + 20)) {
            for (i in cats.indices) {
                val tx = x + 8 + i * tabW
                if (mx in tx..(tx + tabW)) {
                    cat = i
                    sOff = 0f; tOff = 0f
                    expanded = null
                    listeningValue = null
                    return true
                }
            }
        }

        val divY = tabY + 22
        val listY = divY + 6
        val listH = H - (listY - y) - 8
        val listRight = x + W - panelW - 8
        val rowH = 18

        if (mx in (x + 8)..listRight && my in listY..(listY + listH)) {
            val mods = getMods()
            val clickIdx = ((my - listY + sOff) / rowH).toInt()

            if (clickIdx in mods.indices) {
                val mod = mods[clickIdx]
                if (button == 0) {
                    mod.enabled = !mod.enabled
                    flash = 1f; flashRow = clickIdx
                } else if (button == 1) {
                    expanded = if (expanded == mod) null else mod
                    sOff2 = 0f; tOff2 = 0f
                    flash = 1f; flashRow = clickIdx
                    listeningValue = null
                }
                return true
            }
        }

        val curExp = expanded
        if (curExp != null) {
            val px = x + W - panelW - 2
            val py = listY
            val setY = py + 8f
            val setH = H - (py - y) - 12f

            if (mx in px..(px + panelW) && my in py.toInt()..(py + setH).toInt()) {
                val visibleValues = getVisibleValues(curExp)
                var curY = setY - sOff2

                for ((v, depth) in visibleValues) {
                    val isGroup = isGroupValue(v)
                    val isColor = isColorValue(v)
                    val isSlider = isSliderValue(v)
                    val isBind = isBindValue(v)
                    val itemH = when {
                        isGroup -> 18f
                        isColor -> 75f
                        isSlider -> 20f
                        else -> 16f
                    }

                    val indent = depth * 6

                    if (my >= curY && my < curY + itemH) {
                        try {
                            if (isGroup) {
                                if (button == 0 || button == 1) {
                                    if (collapsedGroups.contains(v)) {
                                        collapsedGroups.remove(v)
                                    } else {
                                        collapsedGroups.add(v)
                                    }
                                    return true
                                }
                            }

                            if (button == 0) {
                                val actualVal = getActualValue(v)
                                when {
                                    isColor -> {
                                        val c = extractColor(v)
                                        val boxX = px.toInt() + 8 + indent
                                        val boxY = curY.toInt() + 24
                                        val boxW = 85
                                        val boxH = 45
                                        val barW = 8

                                        val hueX = boxX + boxW + 6
                                        val alphaX = hueX + barW + 5

                                        val hsv = Color.RGBtoHSB(c.red, c.green, c.blue, null)

                                        if (mx in boxX..(boxX + boxW) && my in boxY..(boxY + boxH)) {
                                            val sat = ((mx - boxX).toFloat() / boxW).coerceIn(0f, 1f)
                                            val br = (1f - ((my - boxY).toFloat() / boxH)).coerceIn(0f, 1f)
                                            val newRgb = Color.HSBtoRGB(hsv[0], sat, br)
                                            val newColor = Color((newRgb and 0x00FFFFFF) or (c.alpha shl 24), true)
                                            updateColorValue(v, newColor)
                                        } else if (mx in hueX..(hueX + barW) && my in boxY..(boxY + boxH)) {
                                            val newHue = ((my - boxY).toFloat() / boxH).coerceIn(0f, 1f)
                                            val newRgb = Color.HSBtoRGB(newHue, hsv[1], hsv[2])
                                            val newColor = Color((newRgb and 0x00FFFFFF) or (c.alpha shl 24), true)
                                            updateColorValue(v, newColor)
                                        } else if (mx in alphaX..(alphaX + barW) && my in boxY..(boxY + boxH)) {
                                            val newAlpha = ((1f - ((my - boxY).toFloat() / boxH)) * 255).toInt().coerceIn(0, 255)
                                            val newColor = Color(c.red, c.green, c.blue, newAlpha)
                                            updateColorValue(v, newColor)
                                        }
                                    }
                                    isBind -> {
                                        listeningValue = if (listeningValue == v) null else v
                                    }
                                    actualVal is Boolean -> {
                                        @Suppress("UNCHECKED_CAST")
                                        (v as Value<Boolean>).set(!actualVal)
                                    }
                                    isSlider -> {
                                        var mn = 0f; var mxr = 20f
                                        if (actualVal is ClosedRange<*>) {
                                            mn = 1f; mxr = 30f
                                        } else if (v is RangedValue<*>) {
                                            mn = (v.range.start as? Number)?.toFloat() ?: 0f
                                            mxr = (v.range.endInclusive as? Number)?.toFloat() ?: 100f
                                        }

                                        val bw = panelW - 16 - indent; val bx = px + 8 + indent
                                        val nr = ((mx - bx).toFloat() / bw).coerceIn(0f, 1f)
                                        val nv = mn + nr * (mxr - mn)

                                        if (actualVal is IntRange) {
                                            val center = nv.toInt()
                                            @Suppress("UNCHECKED_CAST")
                                            (v as Value<IntRange>).set((center - 1).coerceAtLeast(1)..center)
                                        } else if (actualVal is Float) {
                                            @Suppress("UNCHECKED_CAST")
                                            (v as Value<Float>).set(nv)
                                        } else if (actualVal is Int) {
                                            @Suppress("UNCHECKED_CAST")
                                            (v as Value<Int>).set(nv.toInt())
                                        }
                                    }
                                    else -> {
                                        toggleNextValue(v)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                        return true
                    }
                    curY += itemH
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val mc = Minecraft.getInstance()
        val x = (mc.window.scaledWidth - W) / 2
        val panelX = x + W - panelW - 2
        if (expanded != null && mouseX >= panelX) {
            tOff2 = (tOff2 - amount.toFloat() * 18f).coerceAtLeast(0f)
        } else {
            tOff = (tOff - amount.toFloat() * 18f).coerceAtLeast(0f)
        }
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val lv = listeningValue
        if (lv != null) {
            val targetKeyName = if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) "NONE" else GLFW.glfwGetKeyName(keyCode, 0)?.uppercase() ?: "KEY_$keyCode"

            try {
                val actual = getActualValue(lv)
                if (actual != null) {
                    val cls = actual.javaClass
                    val fields = cls.declaredFields
                    val keyField = fields.find { it.name.contains("key", true) || it.name.contains("bound", true) }
                    
                    if (keyField != null) {
                        keyField.isAccessible = true
                        keyField.set(actual, keyCode)
                    } else if (actual is Int) {
                        @Suppress("UNCHECKED_CAST")
                        (lv as Value<Int>).set(keyCode)
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
            close()
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

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (searchFocus && chr.code > 31) {
            search += chr
            return true
        }
        return super.charTyped(chr, modifiers)
    }

    override fun close() {
        try {
            val mc = Minecraft.getInstance()
            val setScreenMethod = mc.javaClass.methods.find { 
                it.name == "setScreen" || it.name == "openScreen" 
            }
            if (setScreenMethod != null) {
                setScreenMethod.invoke(mc, null)
            } else {
                super.close()
            }
        } catch (_: Exception) {
            super.close()
        }
        anim = 0f
    }

    private fun getMods(): List<ClientModule> {
        val catObj = cats.getOrNull(cat)
        
        val allModules = try {
            ModuleManager.getModules()
        } catch (_: Exception) {
            try {
                val prop = ModuleManager::class.java.methods.find { it.name == "getModules" || it.name == "modules" }
                @Suppress("UNCHECKED_CAST")
                (prop?.invoke(ModuleManager) as? List<ClientModule>) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        return allModules
            .filter { (catObj == null || it.category == catObj) && it.name != "ClickGUI" }
            .filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) }
    }
}
