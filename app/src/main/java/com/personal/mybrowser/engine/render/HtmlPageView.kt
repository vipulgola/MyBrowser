package com.personal.mybrowser.engine.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.personal.mybrowser.engine.html.HtmlNode
import com.personal.mybrowser.engine.layout.HtmlLayoutEngine
import com.personal.mybrowser.engine.layout.PageLayout
import com.personal.mybrowser.engine.style.StyleResolver
import com.personal.mybrowser.engine.style.StyledNode
import com.personal.mybrowser.engine.style.TextColor
import com.personal.mybrowser.network.HttpsAddress
import com.personal.mybrowser.network.HttpsImageLoader
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A canvas painter whose measured height lets its ScrollView parent scroll the page. */
class HtmlPageView(context: Context) : View(context) {
    private val measurer = AndroidTextMeasurer()
    private val engine = HtmlLayoutEngine(measurer)
    private var document = HtmlNode.Document()
    private var page = PageLayout(emptyList(), 0f)
    private var layoutWidth = -1
    private var layoutDirty = true
    private var styledDocument: StyledNode? = null
    private var layoutFontScale = Float.NaN
    private var foreground = Color.BLACK
    private var linkColor = Color.BLUE
    private var pageUrl: String? = null
    private val imageLoader = HttpsImageLoader()
    private var imageJob: Job? = null
    private var requestedImages = false
    private val bitmaps = mutableMapOf<String, Bitmap>()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imageRect = RectF()
    private val destinationRect = RectF()
    private var onLinkClick: (String) -> Unit = {}
    private var tappedLink: String? = null
    private data class LinkAction(val id: Int, val label: String, val target: String)
    private var linkActions = emptyList<LinkAction>()
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            if (layoutDirty) return false
            tappedLink = page.linkAt(event.x, event.y) ?: return false
            return true
        }
    })

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
    }

    /** The caller supplies a new tree when content changes; in-place mutation is not observed. */
    fun show(
        document: HtmlNode.Document,
        pageUrl: String?,
        textColor: Int,
        backgroundColor: Int,
        linkColor: Int,
        onLinkClick: (String) -> Unit,
    ): Boolean {
        val changed = this.document !== document || this.pageUrl != pageUrl
        if (changed) {
            this.document = document
            this.pageUrl = pageUrl
            imageJob?.cancel()
            imageJob = null
            requestedImages = false
            bitmaps.clear()
            layoutDirty = true
            tappedLink = null
            requestLayout()
        }
        foreground = textColor
        this.linkColor = linkColor
        this.onLinkClick = onLinkClick
        setBackgroundColor(backgroundColor)
        invalidate()
        return changed
    }

    // Rebuilding cached styles/layout on content or size changes necessarily allocates;
    // unchanged measure passes reuse them, and onDraw never rebuilds either tree.
    @SuppressLint("DrawAllocation")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            (320 * resources.displayMetrics.density).toInt()
        } else {
            MeasureSpec.getSize(widthMeasureSpec)
        }
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        // Font zoom policy: a CSS px in a font size maps to one Android sp.
        val fontPixelScale = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)
        if (layoutDirty || layoutWidth != width || layoutFontScale != fontPixelScale) {
            if (layoutDirty || layoutFontScale != fontPixelScale) {
                styledDocument = StyleResolver(16f * fontPixelScale, cssPixelScale = fontPixelScale).resolve(document)
            }
            val pagePadding = 20 * resources.displayMetrics.density
            page = engine.layout(checkNotNull(styledDocument), width.toFloat(), pagePadding)
            layoutWidth = width
            layoutFontScale = fontPixelScale
            layoutDirty = false
            // Basic read-aloud access. Per-node semantics and selection come later.
            contentDescription = buildString {
                var previousBaseline: Float? = null
                for (command in page.commands) {
                    if (previousBaseline != null && previousBaseline != command.baselineY) append('\n')
                    append(command.text)
                    previousBaseline = command.baselineY
                }
                page.images.forEach { image ->
                    if (image.alt.isNotBlank()) append("\nImage: ${image.alt}")
                }
            }
            linkActions = page.commands.filter { it.linkTarget != null }
                .groupBy { it.linkTarget!! }.entries.mapIndexed { index, (target, fragments) ->
                    val label = fragments.filter { it.text.isNotBlank() }.joinToString(" ") { it.text }
                    LinkAction(0x01000000 + index, label, target)
                }
            val imageLinks = page.images.filter { it.linkTarget != null }
            linkActions += imageLinks.mapIndexed { index, image ->
                LinkAction(0x02000000 + index, image.alt.ifBlank { "Image link" }, image.linkTarget!!)
            }
            if (!requestedImages) startImageLoads()
        }
        setMeasuredDimension(width, resolveSize(ceil(page.contentHeight).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (command in page.commands) {
            val paint = measurer.paintFor(command.style)
            paint.color = when (val color = command.style.color) {
                TextColor.Foreground -> foreground
                TextColor.Link -> linkColor
                is TextColor.Argb -> color.value
            }
            paint.isUnderlineText = command.linkTarget != null
            canvas.drawText(command.text, command.x, command.baselineY, paint)
        }
        for (image in page.images) {
            val bounds = image.bounds
            val rect = imageRect.apply { set(bounds.left, bounds.top, bounds.right, bounds.bottom) }
            val bitmap = bitmaps[image.source]
            if (bitmap != null) {
                val scale = min(rect.width() / bitmap.width, rect.height() / bitmap.height)
                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale
                val destination = destinationRect.apply {
                    set(rect.left, rect.top, rect.left + drawWidth, rect.top + drawHeight)
                }
                canvas.drawBitmap(bitmap, null, destination, imagePaint)
            } else {
                placeholderPaint.color = Color.LTGRAY
                canvas.drawRect(rect, placeholderPaint)
                placeholderPaint.color = foreground
                placeholderPaint.textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
                canvas.drawText(image.alt.take(40).ifBlank { "Image" }, rect.left + 4f,
                    rect.top + placeholderPaint.textSize + 4f, placeholderPaint)
            }
        }
    }

    private fun startImageLoads() {
        imageJob?.cancel()
        val base = pageUrl?.let { runCatching { HttpsAddress.parseInput(it) }.getOrNull() } ?: return
        val sources = page.images.map { it.source }.distinct().take(8)
        if (sources.isEmpty()) return
        requestedImages = true
        imageJob = CoroutineScope(Dispatchers.Main).launch {
            for (source in sources) {
                try {
                    val bytes = imageLoader.load(base, source) ?: continue
                    val bitmap = withContext(Dispatchers.IO) { decodeBounded(bytes) } ?: continue
                    bitmaps[source] = bitmap
                    invalidate()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IOException) {
                    // Keep the accessible alt text when an image fails.
                } catch (_: RuntimeException) {
                    // Invalid bitmap data cannot break the page.
                }
            }
        }
    }

    private fun decodeBounded(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth.toLong() / sample > 1024 || bounds.outHeight.toLong() / sample > 1024 ||
            bounds.outWidth.toLong() * bounds.outHeight / sample / sample > 1_000_000) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
    }

    override fun onDetachedFromWindow() {
        imageJob?.cancel()
        imageJob = null
        requestedImages = false
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!layoutDirty && !requestedImages) startImageLoads()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // ScrollView transforms events into child/document coordinates and cancels this
        // gesture when it intercepts a drag. Do not add the parent's scrollY again.
        val handled = gestures.onTouchEvent(event)
        if (tappedLink != null) return performClick()
        return handled || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        val target = tappedLink ?: return false
        tappedLink = null
        onLinkClick(target)
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (!layoutDirty) linkActions.forEach {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(it.id, it.label))
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val link = linkActions.firstOrNull { it.id == action }
        if (link != null && !layoutDirty) {
            onLinkClick(link.target)
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }
}
