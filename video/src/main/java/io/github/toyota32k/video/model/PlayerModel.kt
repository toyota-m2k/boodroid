package io.github.toyota32k.video.model

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Size
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.video.VideoSize
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.player.model.Range
import io.github.toyota32k.video.common.AmvSettings
import io.github.toyota32k.video.common.AmvStringPool
import io.github.toyota32k.video.common.IAmvSource
import io.github.toyota32k.utils.SuspendableEvent
import io.github.toyota32k.video.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Closeable
import java.lang.Runnable
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

/**
 * 動画プレーヤーと、それに関するプロパティを保持するビューモデル
 * ExoPlayerは（何と！）Viewではなく、ActivityやViewのライフサイクルから独立しているので、ビューモデルに持たせておくのが一番しっくりくるのだ。
 * しかも、ダイアログのような一時的な画面で使うのでなく、PinPや全画面表示などを有効にするなら、このビューモデルはApplicationスコープのようなライフサイクルオブジェクトに持たせるのがよい。
 * @param context   Application Context
 */
class PlayerModel(
    context: Context,                   // application context が必要
) : Closeable {
    enum class PlayerState {
        None,       // 初期状態
        Loading,
        Error,
        Playing,
        Paused
    }


    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)          // dispose()まで有効なコルーチンスコープ
    val context: Context = context.applicationContext                                        // ApplicationContextならViewModelが持っていても大丈夫だと思う。
    private val listener =  PlayerListener()                                        // ExoPlayerのリスナー

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(listener)
    }

    /**
     * 動画のソース
     * collectできるようにpublicにしているけど、直接valueを弄らず、setVideoSource()メソッドで設定すること。
     */
//    val source: StateFlow<IAmvSource?> = MutableStateFlow<IAmvSource?>(null)

    /**
     * ソースのクリッピング指定
     * setVideoSource()の第２引数で指定すると、内部で、ExoPlayerのClippingMediaSourceを生成する。
     */
//    private var sourceClipping: AmvClipping? = null

    /**
     * 動画再生範囲を無理やりクリッピングする指定
     * Trimmingで、全体の再生範囲を有効にした状態で、再生範囲を限定するときに使用。
     */
//    var pseudoClipping: AmvClipping? = null

//    val playlists:

    val currentSource = MutableStateFlow<IAmvSource?>(null)
    val chapters:Flow<IChapterList?> = currentSource.map { it?.chapterList }
    val disabledRanges:Flow<List<Range>> = currentSource.map {
        it?.chapterList?.disabledRanges(it.trimming)?.toList() ?: emptyList()
    }
    val hasChapters:Flow<Boolean> = chapters.map {!it?.chapters.isNullOrEmpty()

    }
    val hasNext = MutableStateFlow(false)
    val hasPrevious = MutableStateFlow(false)

    val commandNext = Command { player.seekToNextMediaItem() }
    val commandPrev = Command { player.seekToPreviousMediaItem() }

    private fun MediaItem.getAmvSource(): IAmvSource {
        return this.localConfiguration!!.tag as IAmvSource
    }

    fun setSources(sources:Iterable<IAmvSource>, startIndex:Int=0) {
        reset()

        val list = sources.map { src->
            ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(MediaItem.Builder().setUri(src.uri).setTag(src).build())
        }.toList()
        val si = min(startIndex, list.size)
        player.setMediaSources(list, si, list[si].mediaItem.getAmvSource().trimming.start )
        player.prepare()
        seekManager.reset()
    }

    /**
     * 動画の画面サイズ情報
     * ExoPlayerの動画読み込みが成功したとき onVideoSizeChanged()イベントから設定される。
     */
    val videoSize: StateFlow<VideoSize?> = MutableStateFlow<VideoSize?>(null)

    /**
     * 動画プレーヤーを配置するルートビューのサイズ
     * AmvExoVideoPlayerビュークラスのonSizeChanged()からonRootViewSizeChanged()経由で設定される。
     * このルートビューの中に収まるよう、動画プレーヤーのサイズが調整される。
     */
    private val rootViewSize: StateFlow<Size?> = MutableStateFlow<Size?>(null)

    /**
     * ルートビューサイズ変更のお知らせ
     */
    fun onRootViewSizeChanged(size: Size) {
        rootViewSize.mutable.value = size
    }

    /**
     * プレーヤーの状態
     */
    val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState.None)

    /**
     * エラーメッセージ
     */
    val errorMessage: StateFlow<String?> = MutableStateFlow<String?>(null)

    /**
     * （外部から）エラーメッセージを設定する
     */
    fun setErrorMessage(msg:String?) {
        errorMessage.mutable.value = msg
    }

    /**
     * 動画の全再生時間
     */
    val naturalDuration: StateFlow<Long> = MutableStateFlow(0L)


    /**
     * ルートビューに動画プレーヤーを配置する方法を指定
     *  true: ルートビューにぴったりフィット（Aspectは無視）
     *  false: ルートビューの中に収まるサイズ（Aspect維持）
     */
    // var stretchVideoToView = false
    val stretchVideoToView = MutableStateFlow(false)

    private val mFitter = AmvFitterEx(FitMode.Inside)
    val playerSize = combine(videoSize.filterNotNull(),rootViewSize.filterNotNull()) { videoSize, rootViewSize->
        logger.debug("videoSize=(${videoSize.height} x ${videoSize.height}), rootViewSize=(${rootViewSize.width} x ${rootViewSize.height})")
        mFitter
            .setLayoutWidth(rootViewSize.width)
            .setLayoutHeight(rootViewSize.height)
            .fit(videoSize.width, videoSize.height)
            .resultSize
    }.stateIn(scope, SharingStarted.Eagerly, Size(100,100))

    val isLoading = state.map { it== PlayerState.Loading }.stateIn(scope, SharingStarted.Eagerly, false)
    val isReady = state.map { it== PlayerState.Playing || it== PlayerState.Paused }.stateIn(scope, SharingStarted.Eagerly, false)
    val isPlaying = state.map { it== PlayerState.Playing }.stateIn(scope, SharingStarted.Eagerly, false)
    val isError = errorMessage.map { !it.isNullOrBlank() }.stateIn(scope, SharingStarted.Lazily, false)

    /**
     * プレーヤー内の再生位置
     * 動画再生中は、タイマーで再生位置(player.currentPosition)を監視して、このFlowにセットする。
     * スライダーは、これをcollectして、シーク位置を同期する。
     */
    val playerSeekPosition: StateFlow<Long> =  MutableStateFlow(0L)

    private val watchPositionEvent = SuspendableEvent(signal = false, autoReset = false)    // スライダー位置監視を止めたり、再開したりするためのイベント
    private var ended = false                   // 次回再生開始時に先頭に戻すため、最後まで再生したことを覚えておくフラグ
    var isDisposed:Boolean = false      // close済みフラグ
        private set

    init {
        isPlaying.onEach {
            if(it) {
                watchPositionEvent.set()
            }
        }.launchIn(scope)

        scope.launch {
            while(!isDisposed) {
                watchPositionEvent.waitOne()
                val pos = player.currentPosition
                if(!seekManager.isSeeking) {
                    playerSeekPosition.mutable.value = pos
                }
                if(!isPlaying.value) {
                    watchPositionEvent.reset()
                }
                delay(50)
            }
        }
    }

    /**
     * 解放
     */
    override fun close() {
        player.removeListener(listener)
        player.release()
        scope.cancel()
        isDisposed = true
    }

    /**
     * 再初期化
     */
    fun reset() {
        currentSource.value = null
        hasPrevious.value = false
        hasNext.value = false
    }

    /**
     * 0-durationで　引数 pos をクリップして返す。
     */
    fun clipPosition(pos:Long):Long {
        return max(0, min(pos, naturalDuration.value))
    }

    /**
     * pseudoClippingを考慮したシーク
     */
    private fun clippingSeekTo(pos:Long) {
        val clippedPos = clipPosition(pos)
        val end = naturalDuration.value

        player.seekTo(clippedPos)
        playerSeekPosition.mutable.value = clippedPos
        ended = if(clippedPos == end) {
            pause()
            true
        } else {
            false
        }
    }

    /**
     * Play / Pauseをトグル
     */
    fun togglePlay() {
        if(player.playWhenReady) {
            pause()
        } else {
            play()
        }
    }

    /**
     * （再生中でなければ）再生を開始する
     */
    fun play() {
        if(isDisposed) return
        if(ended) {
            // 動画ファイルの最後まで再生して止まっている場合は、先頭にシークしてから再生を開始する
            ended = false
            seekManager.exactSeek(0L)
        }
        player.playWhenReady = true
    }

    /**
     * 再生を中断する
     */
    fun pause() {
        if(isDisposed) return
        player.playWhenReady = false
    }

    /**
     * （SeekManager経由で）シークする
     */
    fun seekTo(pos:Long) {
        if(isDisposed) return
        if(ended) {
            // 動画ファイルの最後まで再生して止まっているとき、Playerの内部状態は、playWhenReady == true のままになっている。
            // そのまま、シークしてしまうと、シーク後に勝手に再生が再開されてしまう。
            // これを回避するため、シークのタイミングで、mEndedフラグが立っていれば、再生を終了してからシークすることにする。
            ended = false
            player.playWhenReady = false
        }
        seekManager.request(pos)
    }

    /**
     * ExoPlayerのイベントリスナークラス
     */
    inner class PlayerListener :  Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            this@PlayerModel.videoSize.mutable.value = videoSize
        }

        override fun onPlayerError(error: PlaybackException) {
            logger.stackTrace(error)
            if(!isReady.value) {
                state.mutable.value = PlayerState.Error
                errorMessage.mutable.value = AmvStringPool[R.string.error] ?: context.getString(R.string.error)
            } else {
                logger.warn("ignoring exo error.")
            }
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            logger.debug("loading = $isLoading")
            if (isLoading && player.playbackState == Player.STATE_BUFFERING) {
                if(state.value== PlayerState.None) {
                    state.mutable.value = PlayerState.Loading
                } else {
                    scope.launch {
                        for(i in 0..20) {
                            delay(100)
                            if (player.playbackState != Player.STATE_BUFFERING) {
                                break
                            }
                        }
                        if (player.playbackState == Player.STATE_BUFFERING) {
                            // ２秒以上bufferingならロード中に戻す
                            state.mutable.value = PlayerState.Loading
                        }
                    }
                }
            }
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            logger.debug {
                val ppn = {s:Int->
                    when(s) {
                        Player.STATE_IDLE -> "Idle"
                        Player.STATE_BUFFERING -> "Buffering"
                        Player.STATE_READY -> "Ready"
                        Player.STATE_ENDED -> "Ended"
                        else -> "Unknown"
                    }
                }
                "status = ${ppn(playbackState)} / playWhenReady = $playWhenReady"
            }
            when(playbackState) {
                Player.STATE_READY ->  {
                    state.mutable.value = if(playWhenReady) PlayerState.Playing else PlayerState.Paused
                    naturalDuration.mutable.value = player.duration
                }
                Player.STATE_ENDED -> {
                    player.playWhenReady = false
                    ended = true
                    state.mutable.value = PlayerState.Paused
                }
                else -> {}
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            ended = false
            state.mutable.value = PlayerState.None
            videoSize.mutable.value = null
            errorMessage.mutable.value = null
            naturalDuration.mutable.value = 0L

            currentSource.value = mediaItem?.getAmvSource()
            hasNext.value = player.hasNextMediaItem()
            hasPrevious.value = player.hasPreviousMediaItem()
        }
    }

    /**
     * 高速シークモードを開始（スライダー用）
     */
    fun beginFastSeekMode() {
        val duration = naturalDuration.value
        if(duration==0L) return
        seekManager.begin(duration)
    }

    /**
     * 高速シークモード終了（スライダー用）
     */
    fun endFastSeekMode() {
        seekManager.end()
    }

    /**
     * 絶望的にどんくさいシークを、少し改善するクラス
     *
     * VideoView をやめて、ExoPlayerを使うようにしたことにより、KeyFrame以外へのシークが可能になった。
     * しかし、KeyFrame以外へのシークはかなり遅く、ExoPlayerのステートが、頻繁に、Loading に変化し、
     * シークバーから指を放すまで、プレーヤー画面の表示が更新されない。
     *
     * 実際、デフォルトのコントローラーのスライダーを操作したときも、同じ動作になる。
     *
     * seekモードをCLOSEST_SYNCにると、キーフレームにしかシークしないが、途中の画面も描画されるので、
     * 激しくスライダーを操作しているときは、CLOSEST_SYNCでシークし、止まっているか、ゆっくり操作すると
     * EXACTでシークするようにしてみる。
     */
    inner class SeekManager {
        private val mInterval = 100L        // スライダーの動きを監視するためのタイマーインターバル
        private val mWaitCount = 5          // 上のインターバルで何回チェックし、動きがないことが確認されたらEXACTシークするか？　mInterval*mWaitCount (ms)
        private val mPercent = 1            // 微動（移動していない）とみなす移動量・・・全Durationに対するパーセント
        private var mSeekTarget: Long = -1L // 目標シーク位置
        private var mSeeking = false        // スライダーによるシーク中はtrue / それ以外は false
        private var mCheckCounter = 0       // チェックカウンタ （この値がmWaitCountを超えたら、EXACTシークする）
        private var mThreshold = 0L         // 微動とみなす移動量の閾値・・・naturalDuration * mPercent/100 (ms)
        private var mFastMode = false       // 現在、ExoPlayerに設定しているシークモード（true: CLOSEST_SYNC / false: EXACT）
        private val mHandler = Handler(Looper.getMainLooper())

        // mInterval毎に実行する処理
        private val mLoop = Runnable {
            mCheckCounter++
            checkAndSeek()
        }

        fun reset() {
            mFastMode = false
            mSeekTarget -1L         // 目標シーク位置
            mSeeking = false        // スライダーによるシーク中はtrue / それ以外は false
            mCheckCounter = 0       // チェックカウンタ （この値がmWaitCountを超えたら、EXACTシークする）
            mThreshold = 0L         // 微動とみなす移動量の閾値・・・naturalDuration * mPercent/100 (ms)
            mFastMode = false       // 現在、ExoPlayerに設定しているシークモード（true: CLOSEST_SYNC / false: EXACT）
            player.setSeekParameters(SeekParameters.EXACT)
        }

        /**
         * Loopの中の人
         */
        private fun checkAndSeek() {
            if(mSeeking) {
                if(mCheckCounter>=mWaitCount && mSeekTarget>=0 ) {
                    if(isLoading.value) {
                        logger.debug("seek: checked ok, but loading now")
                    } else {
                        logger.debug("seek: checked ok")
                        exactSeek(mSeekTarget)
                        mCheckCounter = 0
                    }
                }
                mHandler.postDelayed(mLoop, mInterval)
            }
        }

        /***
         * スライダーによるシークを開始する
         */
        fun begin(duration:Long) {
            logger.debug("seek begin")
            if(!mSeeking) {
                mSeeking = true
                mFastMode = true
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                mSeekTarget = -1L
                mThreshold = (duration * mPercent) / 100
                mHandler.postDelayed(mLoop, 0)
            }
        }

        /***
         * スライダーによるシークを終了する
         */
        fun end() {
            logger.debug("seek end")
            if(mSeeking) {
                mSeeking = false
                if(mSeekTarget>=0) {
                    exactSeek(mSeekTarget)
                    mSeekTarget = -1
                }
            }
        }

        /***
         * シークを要求する
         */
        fun request(pos:Long) {
            logger.debug("seek request - $pos")
            if(mSeeking) {
                if (mSeekTarget < 0 || (pos - mSeekTarget).absoluteValue > mThreshold) {
                    logger.debug("reset check count - $pos ($mCheckCounter)")
                    mCheckCounter = 0
                }
                fastSeek(pos)
                mSeekTarget = pos
            } else {
                exactSeek(pos)
            }
        }

        /**
         * 高速な（テキトーな）シーク
         */
        private fun fastSeek(pos:Long) {
            logger.debug("fast seek - $pos")
            if(isLoading.value) {
                return
            }
            if(!mFastMode) {
                logger.debug("switch to fast seek")
                mFastMode = true
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
            clippingSeekTo(pos)
        }

        /**
         * 正確なシーク
         */
        fun exactSeek(pos:Long) {
            logger.debug("exact seek - $pos")
            if(mFastMode) {
                logger.debug("switch to exact seek")
                mFastMode = false
                player.setSeekParameters(SeekParameters.EXACT)
            }
            clippingSeekTo(pos)
        }

        /**
         * シーク中か？
         */
        val isSeeking:Boolean
            get() = mSeeking
    }
    private var seekManager = SeekManager()

    /**
     * 外部に対して、ImmutableなStateFlowとして公開したプロパティを更新するために、MutableStateFlowにキャストする秘密のメソッド
     */
    private val <T> StateFlow<T>.mutable: MutableStateFlow<T>
        get() = this as MutableStateFlow<T>

    companion object {
        val logger = AmvSettings.logger
    }
}
