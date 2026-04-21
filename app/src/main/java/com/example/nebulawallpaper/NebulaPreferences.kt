package com.example.nebulawallpaper

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object NebulaPreferences {
    private const val PREFS_NAME = "nebula_prefs"

    // Default Presets
    val defaultRules = listOf(
        RuleItem("Conway's Life", "B3/S23"),
        RuleItem("HighLife", "B36/S23"),
        RuleItem("Day & Night", "B3678/S34678"),
        RuleItem("Anneal", "B4678/S35678"),
        RuleItem("Vote", "B5678/S45678"),
        RuleItem("Mazectric", "B3/S1234"),
        RuleItem("Life without Death", "B3/S012345678"),
        RuleItem("Seeds", "B2/S"),
        RuleItem("Diffusion", "B1/S")
    )

    data class RuleItem(val name: String, val rule: String)

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Current Rule ---
    fun getCurrentRule(context: Context): RuleItem {
        val json = getPrefs(context).getString("current_rule_json", null)
        return if (json != null) {
            val obj = JSONObject(json)
            RuleItem(obj.getString("name"), obj.getString("rule"))
        } else defaultRules[0]
    }

    fun setCurrentRule(context: Context, item: RuleItem) {
        val obj = JSONObject().put("name", item.name).put("rule", item.rule)
        getPrefs(context).edit().putString("current_rule_json", obj.toString()).apply()
    }

    // --- Custom Rule List ---
    fun getSavedRules(context: Context): List<RuleItem> {
        val jsonStr = getPrefs(context).getString("custom_rules_json", null)
        val list = ArrayList<RuleItem>()
        if (jsonStr == null) {
            list.addAll(defaultRules)
            saveRules(context, list)
        } else {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(RuleItem(obj.getString("name"), obj.getString("rule")))
            }
        }
        return list
    }

    fun saveRules(context: Context, list: List<RuleItem>) {
        val arr = JSONArray()
        for (item in list) {
            arr.put(JSONObject().put("name", item.name).put("rule", item.rule))
        }
        getPrefs(context).edit().putString("custom_rules_json", arr.toString()).apply()
    }

    // --- Bitmask Parser for Renderer ---
    fun getRuleBitmasks(context: Context): IntArray {
        val rule = getCurrentRule(context).rule.uppercase()
        var birth = 0; var survival = 0
        try {
            val parts = rule.split("/")
            for (part in parts) {
                if (part.startsWith("B")) {
                    for (char in part) if (char.isDigit()) birth = birth or (1 shl char.toString().toInt())
                } else if (part.startsWith("S")) {
                    for (char in part) if (char.isDigit()) survival = survival or (1 shl char.toString().toInt())
                }
            }
        } catch (e: Exception) {
            birth = 8; survival = 12 // Fallback B3/S23
        }
        return intArrayOf(birth, survival)
    }

    // --- Generic Settings ---
    fun getCellSize(context: Context) = getPrefs(context).getInt("cell_size", 5)
    fun setCellSize(context: Context, v: Int) = getPrefs(context).edit().putInt("cell_size", v).apply()

    fun getFps(context: Context) = getPrefs(context).getInt("target_fps", 30) // 0 = Unlimited
    fun setFps(context: Context, v: Int) = getPrefs(context).edit().putInt("target_fps", v).apply()

    fun isTouchEnabled(context: Context) = getPrefs(context).getBoolean("touch_enabled", true)
    fun setTouchEnabled(context: Context, v: Boolean) = getPrefs(context).edit().putBoolean("touch_enabled", v).apply()

    fun isKeepalive(context: Context) = getPrefs(context).getBoolean("keep_enabled", true)
    fun setKeepalive(context: Context, v: Boolean) = getPrefs(context).edit().putBoolean("keep_enabled", v).apply()

    fun getKeepaliveChance(context: Context) = getPrefs(context).getInt("keep_chance", 50)
    fun setKeepaliveChance(context: Context, v: Int) = getPrefs(context).edit().putInt("keep_chance", v).apply()

    fun getKeepaliveThreshold(context: Context): Float {
        val slider = getKeepaliveChance(context)
        return 0.9999f - ((slider / 100f) * 0.0099f)
    }
}