package com.example.nebulawallpaper

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Stack

class EditorActivity : Activity() {

    private lateinit var editorView: EditorView
    private lateinit var colorPreview: View
    private var currentColorX = 0.5f
    private var currentColorY = 0.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        editorView = EditorView(this)
        root.addView(editorView, FrameLayout.LayoutParams(-1, -1))

        // --- TOP TOOLBAR ---
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6000000"))
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }
        actionRow.addView(createButton("Pen") { editorView.setMode(EditorMode.PEN) })
        actionRow.addView(createButton("Select") { editorView.setMode(EditorMode.SELECT) })
        actionRow.addView(createButton("Copy") { copyToClipboard() })
        actionRow.addView(createButton("Paste") { pasteFromClipboard() })
        actionRow.addView(createButton("Undo") { editorView.undo() })
        actionRow.addView(createButton("Redo") { editorView.redo() })
        topBar.addView(actionRow)

        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 16)
            gravity = Gravity.CENTER_VERTICAL
        }
        val slidersLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        slidersLayout.addView(createColorSlider("Color X") { v -> currentColorX = v; updateColor() })
        slidersLayout.addView(createColorSlider("Color Y") { v -> currentColorY = v; updateColor() })
        colorRow.addView(slidersLayout)

        colorPreview = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80).apply { setMargins(16, 0, 0, 0) }
            setBackgroundColor(EditorView.getShaderColor(currentColorX, currentColorY, true))
        }
        colorRow.addView(colorPreview)
        topBar.addView(colorRow)

        root.addView(topBar, FrameLayout.LayoutParams(-1, -2).apply { gravity = Gravity.TOP })

        // --- BOTTOM TOOLBAR ---
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#E6000000"))
            setPadding(8, 8, 8, 8)
        }

        bottomBar.addView(createButton("Flip H") { editorView.flipHorizontal() })
        bottomBar.addView(createButton("Flip V") { editorView.flipVertical() })
        bottomBar.addView(createButton("Invert") { editorView.invert() })
        bottomBar.addView(createButton("Save & Exit") {
            editorView.saveToDisk()
            finish()
        })
        root.addView(bottomBar, FrameLayout.LayoutParams(-1, -2).apply { gravity = Gravity.BOTTOM })

        setContentView(root)
        updateColor()
    }

    private fun updateColor() {
        val c = EditorView.getShaderColor(currentColorX, currentColorY, true)
        colorPreview.setBackgroundColor(c)
        editorView.setDrawingColor(currentColorX, currentColorY)
    }

    private fun createButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text; textSize = 10f; setPadding(4, 4, 4, 4)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener { onClick() }
        }
    }

    private fun createColorSlider(label: String, onChange: (Float) -> Unit): LinearLayout {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tv = TextView(this).apply { text = label; setTextColor(Color.WHITE); textSize = 12f; width = 120 }
        val seek = SeekBar(this).apply {
            max = 100; progress = 50
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, b: Boolean) { onChange(p / 100f) }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        layout.addView(tv); layout.addView(seek)
        return layout
    }

    private fun copyToClipboard() {
        val rle = editorView.getSelectionAsRle()
        if (rle.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Nebula RLE", rle))
            Toast.makeText(this, "Copied to Clipboard", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(this, "Nothing selected", Toast.LENGTH_SHORT).show()
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (!text.isNullOrEmpty()) {
            val grid = RleHelper.decodeRle(text)
            if (grid != null) {
                editorView.startPaste(grid)
                Toast.makeText(this, "Drag to position, release to Paste", Toast.LENGTH_LONG).show()
            } else Toast.makeText(this, "Invalid RLE data", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() { super.onPause(); editorView.saveToDisk() }
}

enum class EditorMode { PEN, SELECT, PASTE }

class EditorView(context: Context) : View(context) {

    companion object {
        fun getShaderColor(x: Float, y: Float, alive: Boolean): Int {
            val z = if (alive) 1.0f else 0.0f
            var r = 0.239f + 0.833f * x - 0.498f * y
            var g = 0.239f - 0.031f * x + 0.510f * y
            var b = 0.311f - 0.031f * x + 1.014f * y
            r += 0.1f * z; g += 0.1f * z; b += 0.1f * z
            if (x < 0.001f && y < 0.001f) { r = 0f; g = 0f; b = 0f }
            val ir = (r.coerceIn(0f, 1f) * 255).toInt()
            val ig = (g.coerceIn(0f, 1f) * 255).toInt()
            val ib = (b.coerceIn(0f, 1f) * 255).toInt()
            return Color.rgb(ir, ig, ib)
        }
    }

    private val SAVE_FILE_NAME = "nebula_save.bin"
    private var gridW = 100; private var gridH = 100

    private var simData = ByteArray(0)
    private var bitmapPixels = IntArray(0)
    private lateinit var bitmap: Bitmap

    private val undoStack = Stack<ByteArray>()
    private val redoStack = Stack<ByteArray>()

    private var mode = EditorMode.PEN
    private var drawColorX = 0.5f; private var drawColorY = 0.5f
    private var isDrawingAlive = true

    private var selStartX = -1; private var selStartY = -1
    private var selEndX = -1; private var selEndY = -1
    private var isSelecting = false

    private var pasteGrid: Array<BooleanArray>? = null
    private var pasteX = 0; private var pasteY = 0

    private val transformMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val scaleDetector: ScaleGestureDetector

    // Pan Fix tracking
    private var lastTouchX = 0f; private var lastTouchY = 0f
    private var isPanning = false

    private val paintBitmap = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val paintSelect = Paint().apply { color = Color.parseColor("#4400FFFF"); style = Paint.Style.FILL }

    init {
        loadFromDisk()
        saveHistoryState()

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                transformMatrix.postScale(scale, scale, detector.focusX, detector.focusY)
                invalidate()
                return true
            }
        })
    }

    fun setDrawingColor(x: Float, y: Float) { drawColorX = x; drawColorY = y }
    fun setMode(m: EditorMode) { mode = m; isSelecting = false; if (m != EditorMode.PASTE) pasteGrid = null; invalidate() }

    private fun saveHistoryState() { undoStack.push(simData.clone()); redoStack.clear() }

    fun undo() {
        if (undoStack.size > 1) {
            redoStack.push(undoStack.pop())
            simData = undoStack.peek().clone()
            rebuildBitmap()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val state = redoStack.pop()
            undoStack.push(state.clone())
            simData = state
            rebuildBitmap()
        }
    }

    fun getSelectionAsRle(): String {
        val xMin = minOf(selStartX, selEndX).coerceIn(0, gridW - 1)
        val xMax = maxOf(selStartX, selEndX).coerceIn(0, gridW - 1)
        val yMin = minOf(selStartY, selEndY).coerceIn(0, gridH - 1)
        val yMax = maxOf(selStartY, selEndY).coerceIn(0, gridH - 1)
        if (xMin < 0 || yMin < 0) return ""

        val subGrid = Array(yMax - yMin + 1) { BooleanArray(xMax - xMin + 1) }
        for (y in yMin..yMax) {
            for (x in xMin..xMax) {
                val z = simData[((y * gridW + x) * 4) + 2].toInt() and 0xFF
                subGrid[y - yMin][x - xMin] = (z > 127)
            }
        }
        val currentRule = NebulaPreferences.getCurrentRule(context).rule
        return RleHelper.encodeRle(subGrid, currentRule)
    }

    fun startPaste(pGrid: Array<BooleanArray>) {
        pasteGrid = pGrid
        pasteX = gridW / 2 - pGrid[0].size / 2
        pasteY = gridH / 2 - pGrid.size / 2
        setMode(EditorMode.PASTE)
    }

    private fun commitPaste() {
        val pGrid = pasteGrid ?: return
        saveHistoryState()
        for (y in pGrid.indices) {
            for (x in pGrid[0].indices) {
                val gx = pasteX + x; val gy = pasteY + y
                if (gx in 0 until gridW && gy in 0 until gridH && pGrid[y][x]) {
                    setPixelData(gx, gy, drawColorX, drawColorY, true)
                }
            }
        }
        setMode(EditorMode.PEN)
        syncBitmap()
    }

    fun flipHorizontal() { applyTransform { x, y, xMin, xMax, _, _ -> Pair(xMax - (x - xMin), y) } }
    fun flipVertical() { applyTransform { x, y, _, _, yMin, yMax -> Pair(x, yMax - (y - yMin)) } }
    fun invert() {
        saveHistoryState()
        val bounds = getSelectionBounds()
        for (y in bounds.yMin..bounds.yMax) {
            for (x in bounds.xMin..bounds.xMax) {
                val idx = ((y * gridW + x) * 4) + 2
                val currentZ = simData[idx].toInt() and 0xFF
                val alive = currentZ <= 127
                val cx = (simData[idx-2].toInt() and 0xFF) / 255f
                val cy = (simData[idx-1].toInt() and 0xFF) / 255f
                setPixelData(x, y, cx, cy, alive)
            }
        }
        syncBitmap()
    }

    private data class Bounds(val xMin: Int, val xMax: Int, val yMin: Int, val yMax: Int)
    private fun getSelectionBounds(): Bounds {
        return if (selStartX >= 0) {
            Bounds(minOf(selStartX, selEndX), maxOf(selStartX, selEndX), minOf(selStartY, selEndY), maxOf(selStartY, selEndY))
        } else Bounds(0, gridW - 1, 0, gridH - 1)
    }

    private fun applyTransform(mapper: (x: Int, y: Int, xMin: Int, xMax: Int, yMin: Int, yMax: Int) -> Pair<Int, Int>) {
        saveHistoryState()
        val bounds = getSelectionBounds()
        val newData = simData.clone()
        for (y in bounds.yMin..bounds.yMax) {
            for (x in bounds.xMin..bounds.xMax) {
                val (srcX, srcY) = mapper(x, y, bounds.xMin, bounds.xMax, bounds.yMin, bounds.yMax)
                val srcIdx = (srcY * gridW + srcX) * 4
                val dstIdx = (y * gridW + x) * 4
                newData[dstIdx] = simData[srcIdx]
                newData[dstIdx + 1] = simData[srcIdx + 1]
                newData[dstIdx + 2] = simData[srcIdx + 2]
                newData[dstIdx + 3] = simData[srcIdx + 3]
            }
        }
        simData = newData
        rebuildBitmap()
    }

    private fun setPixelData(x: Int, y: Int, cx: Float, cy: Float, alive: Boolean) {
        val idx = y * gridW + x
        val byteIdx = idx * 4
        simData[byteIdx] = (cx * 255).toInt().toByte()
        simData[byteIdx + 1] = (cy * 255).toInt().toByte()
        simData[byteIdx + 2] = (if(alive) 255 else 0).toByte()
        simData[byteIdx + 3] = 255.toByte()
        bitmapPixels[idx] = getShaderColor(cx, cy, alive)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val pts = floatArrayOf(event.x, event.y)
        transformMatrix.invert(inverseMatrix)
        inverseMatrix.mapPoints(pts)
        val gx = pts[0].toInt(); val gy = pts[1].toInt()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x; lastTouchY = event.y
                isPanning = false
                if (event.pointerCount == 1) {
                    when (mode) {
                        EditorMode.PEN -> {
                            if (gx in 0 until gridW && gy in 0 until gridH) {
                                saveHistoryState()
                                val isCurrentlyAlive = (simData[((gy * gridW + gx) * 4) + 2].toInt() and 0xFF) > 127
                                isDrawingAlive = !isCurrentlyAlive
                                setPixelData(gx, gy, drawColorX, drawColorY, isDrawingAlive)
                                syncBitmap()
                            }
                        }
                        EditorMode.SELECT -> {
                            selStartX = gx.coerceIn(0, gridW - 1); selStartY = gy.coerceIn(0, gridH - 1)
                            selEndX = selStartX; selEndY = selStartY
                            isSelecting = true
                        }
                        EditorMode.PASTE -> { pasteX = gx; pasteY = gy }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                lastTouchX = event.x; lastTouchY = event.y
                isPanning = true // Prevent drawing logic while zooming
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1 || isPanning) {
                    transformMatrix.postTranslate(event.x - lastTouchX, event.y - lastTouchY)
                } else if (!isPanning) {
                    when (mode) {
                        EditorMode.PEN -> {
                            if (gx in 0 until gridW && gy in 0 until gridH) {
                                setPixelData(gx, gy, drawColorX, drawColorY, isDrawingAlive)
                                syncBitmap()
                            }
                        }
                        EditorMode.SELECT -> {
                            if (isSelecting) { selEndX = gx.coerceIn(0, gridW - 1); selEndY = gy.coerceIn(0, gridH - 1) }
                        }
                        EditorMode.PASTE -> { pasteX = gx; pasteY = gy }
                    }
                }
                lastTouchX = event.x; lastTouchY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val activeIndex = if (event.actionIndex == 0) 1 else 0
                if (activeIndex < event.pointerCount) {
                    lastTouchX = event.getX(activeIndex); lastTouchY = event.getY(activeIndex)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mode == EditorMode.PASTE && !isPanning) commitPaste()
                isSelecting = false; isPanning = false
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#111111"))
        canvas.concat(transformMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paintBitmap)

        if (selStartX >= 0 && mode == EditorMode.SELECT) {
            canvas.drawRect(
                minOf(selStartX, selEndX).toFloat(), minOf(selStartY, selEndY).toFloat(),
                maxOf(selStartX, selEndX) + 1f, maxOf(selStartY, selEndY) + 1f, paintSelect
            )
        }

        if (mode == EditorMode.PASTE && pasteGrid != null) {
            val pGrid = pasteGrid!!
            val previewColor = getShaderColor(drawColorX, drawColorY, true)
            val paintPaste = Paint().apply { color = previewColor; alpha = 180 }

            for (y in pGrid.indices) {
                for (x in pGrid[0].indices) {
                    if (pGrid[y][x]) {
                        canvas.drawRect((pasteX + x).toFloat(), (pasteY + y).toFloat(),
                            (pasteX + x + 1).toFloat(), (pasteY + y + 1).toFloat(), paintPaste)
                    }
                }
            }
        }
    }

    private fun rebuildBitmap() {
        for (y in 0 until gridH) {
            for (x in 0 until gridW) {
                val idx = (y * gridW + x) * 4
                val r = (simData[idx].toInt() and 0xFF) / 255f
                val g = (simData[idx + 1].toInt() and 0xFF) / 255f
                val b = (simData[idx + 2].toInt() and 0xFF)
                bitmapPixels[y * gridW + x] = getShaderColor(r, g, b > 127)
            }
        }
        syncBitmap()
    }

    private fun syncBitmap() {
        bitmap.setPixels(bitmapPixels, 0, gridW, 0, 0, gridW, gridH)
        invalidate()
    }

    fun saveToDisk() {
        try {
            val file = File(context.filesDir, SAVE_FILE_NAME)
            FileOutputStream(file).use { fos ->
                val header = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
                header.putInt(gridW).putInt(gridH)
                fos.write(header.array())
                val flippedData = ByteArray(gridW * gridH * 4)
                for (y in 0 until gridH) {
                    val destY = gridH - 1 - y
                    System.arraycopy(simData, (y * gridW) * 4, flippedData, (destY * gridW) * 4, gridW * 4)
                }
                fos.write(flippedData)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun loadFromDisk() {
        val file = File(context.filesDir, SAVE_FILE_NAME)
        if (!file.exists()) return

        try {
            FileInputStream(file).use { fis ->
                val headerBytes = ByteArray(8)
                if (fis.read(headerBytes) != 8) return
                val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.nativeOrder())
                gridW = header.int; gridH = header.int
                if (gridW <= 0 || gridH <= 0) return

                val dataSize = gridW * gridH * 4
                val rawBytes = ByteArray(dataSize)
                if (fis.read(rawBytes) == dataSize) {
                    simData = ByteArray(dataSize)
                    bitmapPixels = IntArray(gridW * gridH)
                    bitmap = Bitmap.createBitmap(gridW, gridH, Bitmap.Config.ARGB_8888)

                    for (y in 0 until gridH) {
                        val srcY = gridH - 1 - y
                        System.arraycopy(rawBytes, (srcY * gridW) * 4, simData, (y * gridW) * 4, gridW * 4)
                    }
                    rebuildBitmap()
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        val scale = 20f
        transformMatrix.setScale(scale, scale)
    }
}