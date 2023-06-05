package media.uqab.libPdfReader

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.Exception
import kotlin.math.abs

class PdfReader private constructor(
    private val activity: ComponentActivity,
    private val recyclerView: RecyclerView,
    private val filePath: String,
    private val onClickCallback: PageClickCallback?,
    private val onChangeCallback: PageChangeCallback?,
    private val onScrollCallback: PageScrollCallback?
) {
    private var readingMode: ReadingMode = ReadingMode.Continuous
    private var readerAdapter: PdfReaderAdapter
    private var layoutManager: LinearLayoutManager
    private var rendererHelper: PdfRendererHelper? = null
    private val snapHelper: PagerSnapHelper = PagerSnapHelper()

    init {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                // try with uri first
                if (rendererHelper == null) {
                    rendererHelper = try {
                        PdfRendererHelper.getInstance(activity, Uri.parse(filePath))
                    } catch (e: Exception) {
                        PdfRendererHelper.getInstance(activity, filePath)
                    }
                } else {
                    try {
                        rendererHelper?.openRenderer(activity, Uri.parse(filePath))
                    } catch (e: Exception) {
                        rendererHelper?.openRenderer(filePath)
                    }
                }
            }

            if (event == Lifecycle.Event.ON_STOP) {
                rendererHelper?.closeRenderer()
            }
        }

        activity.lifecycle.addObserver(observer)
        layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)

        readerAdapter = PdfReaderAdapter(
            totalPages = { rendererHelper?.pageCount ?: 0 },
            getPage = { p -> rendererHelper?.getBitmap(p) },
            onItemClick = { p -> onClickCallback?.onClick(p) }
        )
        recyclerView.adapter = readerAdapter
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.setHasFixedSize(true)

        // set onScroll listener
        onScrollCallback?.let {
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    layoutManager.let {
                        val pos = it.findFirstCompletelyVisibleItemPosition()
                        if (pos < 0) return
                        onScrollCallback.onScroll(pos + 1)
                    }
                }
            })
        }
    }

    val currentPageNo get() = rendererHelper?.currentPageNumber ?: 0
    val totalPageCount get() = rendererHelper?.pageCount ?: 0

    fun jumpTo(page: Int) = if (abs(currentPageNo - page) < 5) {
        recyclerView.smoothScrollToPosition(page)
    } else {
        if (page < currentPageNo) { // moving backward
            recyclerView.scrollToPosition((page + 10).coerceAtMost(currentPageNo))
            recyclerView.smoothScrollToPosition(page)
        } else if (page > currentPageNo) { // moving forward
            recyclerView.scrollToPosition((page - 10).coerceAtLeast(currentPageNo))
            recyclerView.smoothScrollToPosition(page)
        } else {
            recyclerView.scrollToPosition(page)
        }
    }

    fun changeReadingMode(readingMode: ReadingMode) {
        this.readingMode = readingMode

        val currentPage = currentPageNo

        this.layoutManager = if (readingMode == ReadingMode.SinglePageHorizontal) {
            LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        } else {
            LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
        }

        recyclerView.layoutManager = layoutManager
        recyclerView.scrollToPosition(currentPage)

        snapHelper.attachToRecyclerView(null)
        if (readingMode != ReadingMode.Continuous) {
            snapHelper.attachToRecyclerView(recyclerView)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setDarkMode(dark: Boolean) {
        rendererHelper?.setDarkMode(dark)
        readerAdapter.notifyDataSetChanged()

        if (dark) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                recyclerView.backgroundTintList = ColorStateList.valueOf(Color.BLACK)
            } else {
                recyclerView.background = ColorDrawable(Color.BLACK)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                recyclerView.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            } else {
                recyclerView.background = ColorDrawable(Color.WHITE)
            }
        }
    }

    class Builder(private val activity: ComponentActivity) {
        private var recyclerView: RecyclerView? = null
        private var filePath: String? = null
        private var fileUri: Uri? = null
        private var readingMode: ReadingMode = ReadingMode.Continuous
        private var pageChangeCallback: PageChangeCallback? = null
        private var onClickCallback: media.uqab.libPdfReader.PageClickCallback? = null
        private var onScrollCallback: PageScrollCallback? = null

        fun attachToRecyclerView(recyclerView: RecyclerView): Builder {
            this.recyclerView = recyclerView
            return this
        }

        fun readFrom(filePath: String): Builder {
            this.filePath = filePath
            return this
        }

        fun readFrom(uri: Uri): Builder {
            this.fileUri = uri
            return this
        }

        fun setReadingMode(readingMode: ReadingMode): Builder {
            this.readingMode = readingMode
            return this
        }

        fun setOnClickListener(onClickCallback: media.uqab.libPdfReader.PageClickCallback): Builder {
            this.onClickCallback = onClickCallback
            return this
        }

        fun setOnPageChangeListener(onChangeCallback: PageChangeCallback): Builder {
            this.pageChangeCallback = onChangeCallback
            return this
        }

        fun setOnPageScrollListener(onScrollCallback: PageScrollCallback): Builder {
            this.onScrollCallback = onScrollCallback
            return this
        }

        fun build(): PdfReader {
            if (recyclerView == null) throw IllegalStateException("No recycler view attached")
            if (filePath == null && fileUri == null) throw IllegalArgumentException("No filepath set!")

            val srcPath = filePath ?: fileUri?.toString() ?: throw IllegalArgumentException("No file set!")

            val pdfReader = PdfReader(
                activity,
                recyclerView!!,
                srcPath,
                onClickCallback,
                pageChangeCallback,
                onScrollCallback
            )
            pdfReader.changeReadingMode(readingMode)

            return pdfReader
        }
    }

    enum class ReadingMode {
        Continuous,
        SinglePageHorizontal,
        SinglePageVertical
    }
}