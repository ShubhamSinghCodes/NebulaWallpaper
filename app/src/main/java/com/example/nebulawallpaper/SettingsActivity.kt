package com.example.nebulawallpaper

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.example.nebulawallpaper.NebulaPreferences.RuleItem

class SettingsActivity : Activity() {

    private lateinit var ruleNameInput: EditText
    private lateinit var ruleStringInput: EditText
    private lateinit var ruleSpinner: Spinner
    private lateinit var adapter: RuleAdapter
    private var rulesList = mutableListOf<RuleItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 100)
            setBackgroundColor(Color.parseColor("#121212"))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "Nebula Settings"
            textSize = 24f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 40)
        })

        // --- RULE EDITOR SECTION ---
        createSection(root, "Simulation Rule")

        ruleSpinner = Spinner(this).apply { background.setTint(Color.DKGRAY) }
        root.addView(ruleSpinner)

        val inputLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 20, 0, 20) }

        ruleNameInput = EditText(this).apply {
            hint = "Rule Name (e.g. Life)"; setTextColor(Color.WHITE); setHintTextColor(Color.DKGRAY)
        }
        inputLayout.addView(ruleNameInput)

        ruleStringInput = EditText(this).apply {
            hint = "Rule String (e.g. B3/S23)"; setTextColor(Color.WHITE); setHintTextColor(Color.DKGRAY)
        }
        inputLayout.addView(ruleStringInput)
        root.addView(inputLayout)

        val btnLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnAdd = Button(this).apply { text = "Save New"; setOnClickListener { saveNewRule() } }
        val btnUpd = Button(this).apply { text = "Update"; setOnClickListener { updateRule() } }
        val btnDel = Button(this).apply { text = "Delete"; setOnClickListener { deleteRule() } }
        btnLayout.addView(btnAdd, LinearLayout.LayoutParams(0, -2, 1f))
        btnLayout.addView(btnUpd, LinearLayout.LayoutParams(0, -2, 1f))
        btnLayout.addView(btnDel, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(btnLayout)

        // --- PERFORMANCE SECTION ---
        createSection(root, "Performance & Interaction")

        val touchSwitch = Switch(this).apply { text = "Draw with touch enabled"; setTextColor(Color.WHITE) }
        root.addView(touchSwitch)

        val fpsLabel = TextView(this).apply { setTextColor(Color.LTGRAY); setPadding(0, 20, 0, 0) }
        root.addView(fpsLabel)
        val fpsSeek = SeekBar(this).apply { max = 51 } // 0..50 maps to 10..60. 51 = Unlimited.
        root.addView(fpsSeek)

        val sizeLabel = TextView(this).apply { setTextColor(Color.LTGRAY); setPadding(0, 20, 0, 0) }
        root.addView(sizeLabel)
        val sizeSeek = SeekBar(this).apply { max = 19 }
        root.addView(sizeSeek)

        // --- KEEPALIVE SECTION ---
        createSection(root, "Life Injection")
        val keepSwitch = Switch(this).apply { text = "Random Births"; setTextColor(Color.WHITE) }
        root.addView(keepSwitch)
        val chanceLabel = TextView(this).apply { setTextColor(Color.LTGRAY); setPadding(0, 20, 0, 0) }
        root.addView(chanceLabel)
        val chanceSeek = SeekBar(this).apply { max = 100 }
        root.addView(chanceSeek)

        // --- INITIALIZATION & LOGIC ---

        // Rules
        fun refreshRules() {
            rulesList = NebulaPreferences.getSavedRules(this).toMutableList()
            adapter = RuleAdapter(this, rulesList)
            ruleSpinner.adapter = adapter

            val current = NebulaPreferences.getCurrentRule(this)
            val idx = rulesList.indexOfFirst { it.name == current.name && it.rule == current.rule }
            if (idx >= 0) ruleSpinner.setSelection(idx)
        }
        refreshRules()

        ruleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val item = rulesList[pos]
                ruleNameInput.setText(item.name)
                ruleStringInput.setText(item.rule)
                NebulaPreferences.setCurrentRule(this@SettingsActivity, item)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // Toggles
        touchSwitch.isChecked = NebulaPreferences.isTouchEnabled(this)
        touchSwitch.setOnCheckedChangeListener { _, b -> NebulaPreferences.setTouchEnabled(this, b) }

        keepSwitch.isChecked = NebulaPreferences.isKeepalive(this)
        keepSwitch.setOnCheckedChangeListener { _, b -> NebulaPreferences.setKeepalive(this, b) }

        // SeekBars
        fun updateFpsLabel(p: Int) {
            fpsLabel.text = if (p == 51) "Target FPS: UNLIMITED" else "Target FPS: ${p + 10}"
        }
        val currentFps = NebulaPreferences.getFps(this)
        fpsSeek.progress = if (currentFps == 0) 51 else (currentFps - 10).coerceIn(0, 50)
        updateFpsLabel(fpsSeek.progress)
        fpsSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                val value = if (p == 51) 0 else p + 10
                NebulaPreferences.setFps(this@SettingsActivity, value)
                updateFpsLabel(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        sizeSeek.progress = NebulaPreferences.getCellSize(this) - 1
        sizeLabel.text = "Cell Size: ${sizeSeek.progress + 1}px"
        sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                NebulaPreferences.setCellSize(this@SettingsActivity, p + 1)
                sizeLabel.text = "Cell Size: ${p + 1}px"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        chanceSeek.progress = NebulaPreferences.getKeepaliveChance(this)
        chanceLabel.text = "Inject Chance: ${chanceSeek.progress}%"
        chanceSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                NebulaPreferences.setKeepaliveChance(this@SettingsActivity, p)
                chanceLabel.text = "Inject Chance: $p%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun saveNewRule() {
        val name = ruleNameInput.text.toString().trim()
        val rule = ruleStringInput.text.toString().trim()
        if (name.isNotEmpty() && rule.isNotEmpty()) {
            val item = RuleItem(name, rule)
            rulesList.add(item)
            NebulaPreferences.saveRules(this, rulesList)
            NebulaPreferences.setCurrentRule(this, item)
            adapter.notifyDataSetChanged()
            ruleSpinner.setSelection(rulesList.size - 1)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRule() {
        val pos = ruleSpinner.selectedItemPosition
        if (pos >= 0) {
            val name = ruleNameInput.text.toString().trim()
            val rule = ruleStringInput.text.toString().trim()
            if (name.isNotEmpty() && rule.isNotEmpty()) {
                val item = RuleItem(name, rule)
                rulesList[pos] = item
                NebulaPreferences.saveRules(this, rulesList)
                NebulaPreferences.setCurrentRule(this, item)
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteRule() {
        val pos = ruleSpinner.selectedItemPosition
        if (pos >= 0) {
            rulesList.removeAt(pos)
            if (rulesList.isEmpty()) rulesList.addAll(NebulaPreferences.defaultRules)
            NebulaPreferences.saveRules(this, rulesList)
            NebulaPreferences.setCurrentRule(this, rulesList[0])
            adapter.notifyDataSetChanged()
            ruleSpinner.setSelection(0)
            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createSection(root: ViewGroup, title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(Color.CYAN)
            setPadding(0, 40, 0, 20)
        })
    }

    class RuleAdapter(context: Context, objects: List<RuleItem>) :
        ArrayAdapter<RuleItem>(context, android.R.layout.simple_spinner_item, objects) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = createView(position)
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = createView(position)

        private fun createView(position: Int): View {
            val item = getItem(position)!!
            val text = SpannableString("${item.name}\n(${item.rule})")

            val split = item.name.length + 1
            text.setSpan(RelativeSizeSpan(1.2f), 0, item.name.length, 0)
            text.setSpan(StyleSpan(Typeface.BOLD), 0, item.name.length, 0)
            text.setSpan(StyleSpan(Typeface.ITALIC), split, text.length, 0)
            text.setSpan(RelativeSizeSpan(0.85f), split, text.length, 0)

            return TextView(context).apply {
                this.text = text
                setPadding(20, 20, 20, 20)
                setTextColor(Color.WHITE)
            }
        }
    }
}