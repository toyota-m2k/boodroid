package io.github.toyota32k.boodroid

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import io.github.toyota32k.binder.BaseBinding
import io.github.toyota32k.binder.Binder
import io.github.toyota32k.binder.BindingMode
import io.github.toyota32k.binder.BoolConvert
import io.github.toyota32k.binder.command.LiteCommand
import io.github.toyota32k.binder.command.LiteUnitCommand
import io.github.toyota32k.binder.command.bindCommand
import io.github.toyota32k.binder.visibilityBinding
import io.github.toyota32k.boodroid.databinding.ActivityWallpaperBinding
import io.github.toyota32k.boodroid.dialog.WallpaperDialog
import io.github.toyota32k.boodroid.viewmodel.AppViewModel
import io.github.toyota32k.dialog.mortal.UtMortalActivity
import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.lib.player.view.VideoPlayerView.SimpleManipulationTarget
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.utils.android.FitMode
import io.github.toyota32k.utils.android.UtFitter
import io.github.toyota32k.utils.android.hideActionBar
import io.github.toyota32k.utils.android.hideStatusBar
import io.github.toyota32k.utils.gesture.UtScaleGestureManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WallpaperActivity : UtMortalActivity() {
    class WallpaperViewModel : ViewModel() {
        companion object {
            fun instanceFor(owner: WallpaperActivity): WallpaperViewModel {
                return ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())[WallpaperViewModel::class.java]
            }
        }

        val toggleToolbarCommand = LiteUnitCommand {
            showToolbar.value = !showToolbar.value
        }
        val rotateCommand = LiteCommand<Boolean>()

        val okCommand = LiteUnitCommand()
        val cancelCommand = LiteUnitCommand()

        class RecyclingBitmapFlow private constructor(val flow:MutableStateFlow<Bitmap?>) : Flow<Bitmap?> by flow {
            constructor(bitmap: Bitmap?) : this(MutableStateFlow(bitmap))
            var value: Bitmap?
                get() = flow.value
                set(v) {
                    flow.value?.recycle()
                    flow.value = v
                }
        }

        val sourceBitmap = RecyclingBitmapFlow(null)
        val rotatedBitmap = RecyclingBitmapFlow(null)
//        val croppedBitmap = RecyclingBitmapFlow(null)
        val currentBitmap: Flow<Bitmap?> = combine(sourceBitmap, rotatedBitmap) {s,r-> r ?: s }
        val currentBitmapValue get() = rotatedBitmap.value ?: sourceBitmap.value

        var rotation: Int = 0   // degree
        val showToolbar = MutableStateFlow(true)

        private fun rotate(degree:Int) {
            val source = sourceBitmap.value ?: throw IllegalStateException("no source bitmap")
            if(rotation!=degree) {
                rotation = degree
                if(degree == 0) {
                    rotatedBitmap.value = null
                    return
                }
                rotatedBitmap.value = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(degree.toFloat()) }, true)
            }
        }

        fun rotateRight() {
            var degree = rotation + 90
            if(degree>=360) degree -= 360
            rotate(degree)
        }
        fun rotateLeft() {
            var degree = rotation - 90
            if(degree<0) degree += 360
            rotate(degree)
        }

        override fun onCleared() {
            super.onCleared()
            rotation = 0
            sourceBitmap.value = null
            rotatedBitmap.value = null
            showToolbar.value = false
        }
    }

    private lateinit var controls: ActivityWallpaperBinding
    private lateinit var gestureManager: UtScaleGestureManager
    private val viewModel: WallpaperViewModel by lazy { WallpaperViewModel.instanceFor(this) }
    private val binder = Binder()

    private fun prepareSourceBitmap():Boolean {
        if(viewModel.sourceBitmap.value!=null) return true
        val sourceBitmap = if (intent?.action == Intent.ACTION_SEND) {
            // 外部アプリから「送る」られた
            if (intent.type?.startsWith("image/") == false) return false
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java) ?: return false
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            } ?: return false

            try {
                // URIから画像を読み込む
                val inputStream = contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(inputStream)
            } catch (e: Throwable) {
                logger.error(e)
                return false
            }
        } else {
            AppViewModel.instance.wallpaperSourceBitmap ?: return false
        }
        AppViewModel.instance.wallpaperSourceBitmap = null
        viewModel.sourceBitmap.value = sourceBitmap
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(!prepareSourceBitmap()) {
            logger.error("cannot start WallpaperActivity")
            finish()
            return
        }

        enableEdgeToEdge()
        controls = ActivityWallpaperBinding.inflate(layoutInflater)
        setContentView(controls.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        hideStatusBar()
        hideActionBar()
        gestureManager = UtScaleGestureManager(applicationContext, enableDoubleTap=false, SimpleManipulationTarget(controls.bitmapContainer, controls.imageView), minScale=1f)
            .setup(this) {
                onTap {
                    viewModel.toggleToolbarCommand.invoke()
                    true
                }
            }
        binder.owner(this)
            .bindCommand(viewModel.rotateCommand, controls.imageRotateRightButton, true)
            .bindCommand(viewModel.rotateCommand, controls.imageRotateLeftButton, false)
            .bindCommand(viewModel.rotateCommand) {
                if(it) {
                    viewModel.rotateRight()
                } else {
                    viewModel.rotateLeft()
                }
            }
            .bindCommand(viewModel.toggleToolbarCommand, controls.foldToolbarButton, controls.unfoldToolbarButton)
            .visibilityBinding(controls.toolbar, viewModel.showToolbar)
            .visibilityBinding(controls.unfoldToolbarButton, viewModel.showToolbar, BoolConvert.Inverse)
            .imageBinding(controls.imageView, viewModel.currentBitmap)
            .bindCommand(viewModel.okCommand, controls.okButton) {
                val source = viewModel.currentBitmapValue?:return@bindCommand
                UtImmortalTask.launchTask("Set Wallpaper") {
                    val vm = createViewModel<WallpaperDialog.WallpaperViewModel>()
                    if (showDialog(taskName) { WallpaperDialog() }.status.ok) {
                        cropBitmap(source, !vm.useCropHint.value)?.use { cropInfo->
                            if(cropInfo.croppedBitmap!=null) {
                                setWallpaper(cropInfo.croppedBitmap, vm.lockScreen.value, vm.homeScreen.value, null)
                            } else {
                                setWallpaper(source, vm.lockScreen.value, vm.homeScreen.value, cropInfo.rect)
                            }
                        }
                        finish()
                    }
                }
            }
            .bindCommand(viewModel.cancelCommand, controls.cancelButton) {
                finish()
            }
    }

    fun setWallpaper(bitmap: Bitmap, setLockScreen: Boolean, setHomeScreen: Boolean, hintRect:Rect?) {
        val wallpaperManager = WallpaperManager.getInstance(this)
        try {
            val flags = (if (setLockScreen) WallpaperManager.FLAG_LOCK else 0) or
                    (if (setHomeScreen) WallpaperManager.FLAG_SYSTEM else 0)
            if(flags == 0) return
            wallpaperManager.setBitmap(bitmap, hintRect, true, flags)
        } catch (e: Throwable) {
            logger.error(e)
        }
    }

    data class CropInfo(val rect:Rect, val croppedBitmap:Bitmap?) : AutoCloseable {
        override fun close() {
            croppedBitmap?.recycle()
        }
    }
    private fun cropBitmap(bitmap: Bitmap, generateCroppedBitmap:Boolean) : CropInfo? {
        val scale = controls.imageView.scaleX               // x,y 方向のscaleは同じ
        val rtx = controls.imageView.translationX
        val rty = controls.imageView.translationY
        if (scale ==1f && rtx==0f && rty==0f) return null
        //viewModel.cropParams = PlayerViewModel.CropParams(rtx, rty, scale)
        val tx = rtx / scale
        val ty = rty / scale

        val bw = bitmap.width                               // bitmap のサイズ
        val bh = bitmap.height
        val vw = controls.imageView.width                   // imageView のサイズ
        val vh = controls.imageView.height
        val fitter = UtFitter(FitMode.Inside, vw, vh)
        fitter.fit(bw, bh)
        val iw = fitter.resultWidth                         // imageView内での bitmapの表示サイズ
        val ih = fitter.resultHeight
        val mx = (vw-iw)/2                                  // imageView と bitmap のマージン
        val my = (vh-ih)/2

        // mask
        val maskHeight = 0f // controls.topMask.height/scale
        val maskWidth = 0f // controls.leftMask.width/scale

        // scale: 画面中央をピボットとする拡大率
        // translation：中心座標の移動距離 x scale
        val sw = vw / scale                                 // scaleを補正した表示サイズ
        val sh = vh / scale
        val cx = vw/2f - tx                                 // 現在表示されている画面の中央の座標（scale前の元の座標系）
        val cy = vh/2f - ty
        val sx = max(cx - sw/2 - mx + maskWidth, 0f)              // 表示されている画像の座標（表示画像内の座標系）
        val sy = max(cy - sh/2 - my + maskHeight, 0f)
        val ex = min(cx + sw/2 - mx - maskWidth, iw)
        val ey = min(cy + sh/2 - my - maskHeight, ih)

        val bs = bw.toFloat()/iw                            // 画像の拡大率を補正して、元画像座標系に変換
        val x = sx * bs
        val y = sy * bs
        val w = (ex - sx) * bs
        val h = (ey - sy) * bs

        val newBitmap = if(generateCroppedBitmap) Bitmap.createBitmap(bitmap, x.roundToInt(), y.roundToInt(), w.roundToInt(), h.roundToInt()) else null

//        val screenSize = getScreenSize()
//        logger.debug("bitmap ${newBitmap.width}x${newBitmap.height} / screen ${screenSize.x}x${screenSize.y}")

        return CropInfo(Rect(x.roundToInt(), y.roundToInt(), (x+w).roundToInt(), (y+h).roundToInt()), newBitmap )
    }

    override val logger = UtLog("WP")

//    fun getScreenSize(): Point {
//        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
//        val point = Point()
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            // Android 11以降
//            val metrics = windowManager.currentWindowMetrics
//            val bounds = metrics.bounds
//            point.x = bounds.width()
//            point.y = bounds.height()
//        } else {
//            // Android 10以前
//            @Suppress("DEPRECATION")
//            val display = windowManager.defaultDisplay
//            @Suppress("DEPRECATION")
//            display.getRealSize(point)
//        }
//
//        return point
//    }
}

class ImageViewBinding(override val data: LiveData<Bitmap?>) : BaseBinding<Bitmap?>(BindingMode.OneWay) {
    val imageView get() = view as ImageView
    override fun onDataChanged(v: Bitmap?) {
        if(v!=null) {
            imageView.setImageBitmap(v)
        }
    }
    companion object {
        fun create(owner: LifecycleOwner, view: ImageView, data:LiveData<Bitmap?>): ImageViewBinding {
            return ImageViewBinding(data).apply { connect(owner, view) }
        }
    }
}
fun Binder.imageBinding(view: ImageView, data: Flow<Bitmap?>): Binder
    = add( ImageViewBinding.create(this.requireOwner, view, data.asLiveData()))
