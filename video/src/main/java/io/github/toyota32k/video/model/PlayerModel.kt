package io.github.toyota32k.video.model

import android.content.Context
import android.util.Size
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerNotificationManager
import androidx.media3.ui.PlayerView
import io.github.toyota32k.binder.command.ICommand
import io.github.toyota32k.binder.command.ReliableCommand
import io.github.toyota32k.binder.list.ObservableList
import io.github.toyota32k.player.model.Range
import io.github.toyota32k.utils.SuspendableEvent
import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.video.R
import io.github.toyota32k.video.common.AmvSettings
import io.github.toyota32k.video.common.AmvStringPool
import io.github.toyota32k.video.common.IAmvSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.Closeable
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
        Ready,
        Error,
    }


    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)          // dispose()まで有効なコルーチンスコープ
    val context: Context = context.applicationContext                                        // ApplicationContextならViewModelが持っていても大丈夫だと思う。
    private val listener =  PlayerListener()                                        // ExoPlayerのリスナー

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(listener)
    }

    fun associatePlayerView(view: PlayerView) {
        view.player = player
    }

    fun associateNotificationManager(manager: PlayerNotificationManager) {
        manager.setPlayer(player)
    }

    /**
     * 現在再生中の動画のソース
     */
    val currentSource:StateFlow<IAmvSource?> = MutableStateFlow<IAmvSource?>(null)

    val chapterList:StateFlow<IChapterList?> = MutableStateFlow(null)
    val hasChapters:StateFlow<Boolean> = chapterList.map {
        val r = it?.chapters?.isNotEmpty() ?: false
        logger.debug("hasChapters=$r")
        r
    }.stateIn(scope, SharingStarted.Eagerly,false)
    var disabledRanges:List<Range>? = null
        private set

    fun nextChapter() {
        val c = chapterList.value?.run {
            next(player.currentPosition)
        } ?: return
        seekTo(c.position)
    }
    fun prevChapter() {
        val c = chapterList.value?.run {
            prev(player.currentPosition)
        } ?: return
        seekTo(c.position)
    }

    suspend fun onUpdateCurrentSource(src:IAmvSource?) {
        chapterList.mutable.value = null
        val list = src?.getChapterList()
        if(list==null) return
        disabledRanges = src.disabledRanges(list)
        chapterList.mutable.value = list
    }

    val hasNext = MutableStateFlow(false)
    val hasPrevious = MutableStateFlow(false)
    val videoSources = ObservableList<IAmvSource>()

    val useExoPlayList = false

    fun next() {
        if(useExoPlayList) {
            player.seekToNextMediaItem()
        } else {
            val index = videoSources.indexOf(currentSource.value)
            if(index<0) return
            playAt(index+1)
        }
    }
    fun previous() {
        if(useExoPlayList) {
            player.seekToPreviousMediaItem()
        } else {
            val index = videoSources.indexOf(currentSource.value)
            if(index<0) return
            playAt(index-1)
        }
    }
    fun seekRelative(seek:Long) {
        if(!isReady.value) return
        clippingSeekTo(player.currentPosition + seek, true )
    }

    fun seekTo(pos:Long) {
        if(!isReady.value) return
        clippingSeekTo(pos, true)
    }



    private fun MediaItem.getAmvSource(): IAmvSource {
        return this.localConfiguration!!.tag as IAmvSource
    }

    private fun makeMediaSource(item:IAmvSource) : MediaSource {
        return ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(MediaItem.Builder().setUri(item.uri).setTag(item).build())
    }

    fun setSources(sources:List<IAmvSource>, startIndex:Int=0, position:Long=0L) {
        reset()
        videoSources.replace(sources)

        if (sources.isNotEmpty()) {
            val si = max(0, min(startIndex, sources.size))
            val pos = max(sources[si].trimming.start, position)
            if (useExoPlayList) {
                val list = sources.map { src -> makeMediaSource(src) }.toList()
                player.setMediaSources(list, si, pos)
            } else {
                player.setMediaSource(makeMediaSource(sources[si]), pos)
                currentSource.mutable.value = sources[si]
                hasNext.mutable.value = si<sources.size-1
                hasPrevious.mutable.value = 0<si
            }
            player.prepare()
            play()
        }
    }

    fun playAt(index:Int, position:Long=0L) {
        val current = currentSource.value
        if(current!=null && videoSources.indexOf(current)==index) {
            if(!isPlaying.value) {
                play()
            }
        }

        if(useExoPlayList) {
            if (0 <= index && index < player.mediaItemCount) {
                player.seekTo(index, max(videoSources[index].trimming.start, position))
                play()
            }
        } else {
            if (0 <= index && index < videoSources.size) {
                player.setMediaSource(makeMediaSource(videoSources[index]), max(videoSources[index].trimming.start, position))
                currentSource.mutable.value = videoSources[index]
                hasNext.mutable.value = index<videoSources.size-1
                hasPrevious.mutable.value = 0<index
                player.prepare()
                play()
            }
        }
    }

    fun playAt(item:IAmvSource, position: Long=0L) {
        playAt(videoSources.indexOf(item), position)
    }

    /**
     * 動画の画面サイズ情報
     * ExoPlayerの動画読み込みが成功したとき onVideoSizeChanged()イベントから設定される。
     */
    val videoSize: StateFlow<VideoSize?> = MutableStateFlow<VideoSize?>(null)

    /**
     * VideoSizeはExoPlayerの持ち物なので、ライブラリ利用者が明示的にexoplayerをリンクしていないとアクセスできない。
     * そのような不憫な人のために中身を開示してあげる。
     */
    val videoWidth:Int? get() = videoSize.value?.width
    val videoHeight:Int? get() = videoSize.value?.height

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
     * 認証要求
     */
    data class Retry(val source:IAmvSource, val position: Long)
    val requestAuthentication: ICommand<Retry> = ReliableCommand<Retry>()

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
    }.stateIn(scope, SharingStarted.Eagerly, Size(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

//    val isDisturbing:StateFlow<Boolean> = MutableStateFlow(false)
    val isLoading = state.map { it == PlayerState.Loading }.stateIn(scope, SharingStarted.Eagerly, false)
    val isReady = state.map { it== PlayerState.Ready }.stateIn(scope, SharingStarted.Eagerly, false)
    val isPlaying = MutableStateFlow<Boolean>(false)
    val isError = errorMessage.map { !it.isNullOrBlank() }.stateIn(scope, SharingStarted.Lazily, false)

    /**
     * プレーヤー内の再生位置
     * 動画再生中は、タイマーで再生位置(player.currentPosition)を監視して、このFlowにセットする。
     * スライダーは、これをcollectして、シーク位置を同期する。
     */
    val playerSeekPosition: StateFlow<Long> =  MutableStateFlow(0L)

    private val watchPositionEvent = SuspendableEvent(signal = false, autoReset = false)    // スライダー位置監視を止めたり、再開したりするためのイベント
    private val ended = MutableStateFlow(false)                   // 次回再生開始時に先頭に戻すため、最後まで再生したことを覚えておくフラグ
    var isDisposed:Boolean = false      // close済みフラグ
        private set

    init {
        isPlaying.onEach {
            if(it) {
                watchPositionEvent.set()
            }
        }.launchIn(scope)

        ended.onEach {
            if(it) {
                onEnd()
            }
        }.launchIn(scope)

        currentSource.onEach(this::onUpdateCurrentSource).launchIn(scope)

        scope.launch {
            while(!isDisposed) {
                watchPositionEvent.waitOne()
                if(isPlaying.value) {
                    val pos = player.currentPosition
                    playerSeekPosition.mutable.value = pos
                    // 無効区間、トリミングによる再生スキップの処理
                    val dr =disabledRanges
                    if(dr!=null) {
                        val hit = dr.firstOrNull { it.contains(pos) }
                        if (hit != null) {
                            if (hit.end == 0L || hit.end >= naturalDuration.value) {
                                ended.value = true
                            } else {
                                player.seekTo(hit.end)
                            }
                        }
                    }
                } else {
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
        logger.debug()
        player.removeListener(listener)
        player.release()
        scope.cancel()
        isDisposed = true
    }

    /**
     * 再初期化
     */
    fun reset() {
        logger.debug()
        pause()
        currentSource.mutable.value = null
        hasPrevious.value = false
        hasNext.value = false
        videoSources.clear()
        seekManager.reset()
    }

    /**
     * 0-durationで　引数 pos をクリップして返す。
     */
    fun clipPosition(pos:Long, trimming:Range?):Long {
        val duration = naturalDuration.value
        val s:Long
        val e:Long
        if(trimming==null) {
            s = 0L
            e = duration
        } else {
            s = max(0, trimming.start)
            e = if(trimming.end in (s + 1) until duration) trimming.end else duration
        }
        return max(s,min(pos,e))
    }

    /**
     * pseudoClippingを考慮したシーク
     */
    private fun clippingSeekTo(pos:Long, awareTrimming:Boolean) {
        val clippedPos = clipPosition(pos, if(awareTrimming) currentSource.value?.trimming else null )
//        logger.debug("XX1: $pos / ${naturalDuration.value}")
        player.seekTo(clippedPos)
        playerSeekPosition.mutable.value = clippedPos
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
        logger.debug()
        if(isDisposed) return
        errorMessage.mutable.value = null
        player.playWhenReady = true
    }

    /**
     * 再生を中断する
     */
    fun pause() {
        logger.debug()
        if(isDisposed) return
        player.playWhenReady = false
    }

    private fun onEnd() {
        logger.debug()
        if(hasNext.value) {
            next()
        } else {
            pause()
        }
    }
    /**
     * （SeekManager経由で）シークする
     */
//    fun seekTo(pos:Long) {
//        if(isDisposed) return
//        if(ended) {
//            // 動画ファイルの最後まで再生して止まっているとき、Playerの内部状態は、playWhenReady == true のままになっている。
//            // そのまま、シークしてしまうと、シーク後に勝手に再生が再開されてしまう。
//            // これを回避するため、シークのタイミングで、mEndedフラグが立っていれば、再生を終了してからシークすることにする。
//            ended = false
//            player.playWhenReady = false
//        }
//        seekManager.request(pos)
//    }

    /**
     * ExoPlayerのイベントリスナークラス
     */
    inner class PlayerListener :  Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            this@PlayerModel.videoSize.mutable.value = videoSize
        }

        override fun onPlayerError(error: PlaybackException) {
            logger.stackTrace(error)
            val cause = error.cause as HttpDataSource.InvalidResponseCodeException
            if(cause.responseCode == 401) {
                // 認証が必要（Secure Archive Server)
                val current = currentSource.value
                if (current != null) {
                    val retry = Retry(current, player.currentPosition)
                    currentSource.mutable.value = null
                    CoroutineScope(Dispatchers.Main).launch {
                        requestAuthentication.invoke(retry)
                    }
                }
            }
            else if(!isReady.value) {
                state.mutable.value = PlayerState.Error
                errorMessage.mutable.value = AmvStringPool[R.string.error] ?: context.getString(R.string.error)
            } else {
                logger.warn("ignoring exo error.")
            }
        }

//        override fun onIsLoadingChanged(isLoading: Boolean) {
//            logger.debug("loading = $isLoading")
//            isDisturbing.mutable.value = false
//            if (isLoading && player.playbackState == Player.STATE_BUFFERING) {
//                if(state.value== PlayerState.None) {
//                    state.mutable.value = PlayerState.Loading
//                } else {
//                    scope.launch {
//                        for(i in 0..20) {
//                            delay(100)
//                            if (player.playbackState != Player.STATE_BUFFERING) {
//                                break
//                            }
//                        }
//                        if (player.playbackState == Player.STATE_BUFFERING) {
//                            // ２秒以上bufferingならロード中に戻す
////                            state.mutable.value = PlayerState.Loading
//                            logger.debug("buffering more than 2 sec")
//                            isDisturbing.mutable.value = true
//                        }
//                    }
//                }
//            }
//        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            isPlaying.value = playWhenReady
        }

        override fun onPlaybackStateChanged(playbackState:Int) {
            super.onPlaybackStateChanged(playbackState)
            when(playbackState) {
                Player.STATE_IDLE -> {
                    state.mutable.value = PlayerState.None
                }
                Player.STATE_BUFFERING -> {
                    if(state.value == PlayerState.None) {
                        state.mutable.value = PlayerState.Loading
                    } else {
                        scope.launch {
                            for (i in 0..20) {
                                delay(100)
                                if (player.playbackState != Player.STATE_BUFFERING) {
                                    break
                                }
                            }
                            if (player.playbackState == Player.STATE_BUFFERING) {
                                // ２秒以上bufferingならロード中に戻す
                                logger.debug("buffering more than 2 sec")
                                state.mutable.value = PlayerState.Loading
                            }
                        }
                    }

                }
                Player.STATE_READY ->  {
                    ended.value = false
                    state.mutable.value = PlayerState.Ready
                    naturalDuration.mutable.value = player.duration
                }
                Player.STATE_ENDED -> {
//                    player.playWhenReady = false
                    ended.value = true
                }
                else -> {}
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if(useExoPlayList) {
                state.mutable.value = PlayerState.None
                videoSize.mutable.value = null
                errorMessage.mutable.value = null
                naturalDuration.mutable.value = 0L

                currentSource.mutable.value = mediaItem?.getAmvSource()
                hasNext.value = player.hasNextMediaItem()
                hasPrevious.value = player.hasPreviousMediaItem()
            }
        }
    }

//    /**
//     * 高速シークモードを開始（スライダー用）
//     */
//    fun beginFastSeekMode() {
//        val duration = naturalDuration.value
//        if(duration==0L) return
//        seekManager.begin(duration)
//    }
//
//    /**
//     * 高速シークモード終了（スライダー用）
//     */
//    fun endFastSeekMode() {
//        seekManager.end()
//    }
//

    inner class SeekManagerEx {
        val requestedPositionFromSlider = MutableStateFlow<Long>(-1L)
        var lastOperationTick:Long = 0L
        var fastSync = false
        init {
            requestedPositionFromSlider.onEach {
                val tick = System.currentTimeMillis()
                if(0<=it && it<=naturalDuration.value) {
                    if(tick-lastOperationTick<500) {
                        setFastSeek()
                    } else {
                        setExactSync()
                    }
                    clippingSeekTo(it, false)
                }
                delay(200L)
            }.launchIn(scope)
        }

        fun setFastSeek() {
            if(!fastSync) {
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                fastSync = true
            }
        }
        fun setExactSync() {
            if(fastSync) {
                player.setSeekParameters(SeekParameters.EXACT)
                fastSync = false
            }
        }
        fun reset() {
            setExactSync()
            lastOperationTick = 0L
            requestedPositionFromSlider.value = -1L
        }
    }
    val seekManager = SeekManagerEx()

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
//    inner class SeekManager {
//        private val mInterval = 100L        // スライダーの動きを監視するためのタイマーインターバル
//        private val mWaitCount = 5          // 上のインターバルで何回チェックし、動きがないことが確認されたらEXACTシークするか？　mInterval*mWaitCount (ms)
//        private val mPercent = 1            // 微動（移動していない）とみなす移動量・・・全Durationに対するパーセント
//        private var mSeekTarget: Long = -1L // 目標シーク位置
//        private var mSeeking = false        // スライダーによるシーク中はtrue / それ以外は false
//        private var mCheckCounter = 0       // チェックカウンタ （この値がmWaitCountを超えたら、EXACTシークする）
//        private var mThreshold = 0L         // 微動とみなす移動量の閾値・・・naturalDuration * mPercent/100 (ms)
//        private var mFastMode = false       // 現在、ExoPlayerに設定しているシークモード（true: CLOSEST_SYNC / false: EXACT）
//        private val mHandler = Handler(Looper.getMainLooper())
//
//        // mInterval毎に実行する処理
//        private val mLoop = Runnable {
//            mCheckCounter++
//            checkAndSeek()
//        }
//
//        fun reset() {
//            mFastMode = false
//            mSeekTarget -1L         // 目標シーク位置
//            mSeeking = false        // スライダーによるシーク中はtrue / それ以外は false
//            mCheckCounter = 0       // チェックカウンタ （この値がmWaitCountを超えたら、EXACTシークする）
//            mThreshold = 0L         // 微動とみなす移動量の閾値・・・naturalDuration * mPercent/100 (ms)
//            mFastMode = false       // 現在、ExoPlayerに設定しているシークモード（true: CLOSEST_SYNC / false: EXACT）
//            player.setSeekParameters(SeekParameters.EXACT)
//        }
//
//        /**
//         * Loopの中の人
//         */
//        private fun checkAndSeek() {
//            if(mSeeking) {
//                if(mCheckCounter>=mWaitCount && mSeekTarget>=0 ) {
//                    if(isLoading.value) {
//                        logger.debug("seek: checked ok, but loading now")
//                    } else {
//                        logger.debug("seek: checked ok")
//                        exactSeek(mSeekTarget)
//                        mCheckCounter = 0
//                    }
//                }
//                mHandler.postDelayed(mLoop, mInterval)
//            }
//        }
//
//        /***
//         * スライダーによるシークを開始する
//         */
//        fun begin(duration:Long) {
//            logger.debug("seek begin")
//            if(!mSeeking) {
//                mSeeking = true
//                mFastMode = true
//                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
//                mSeekTarget = -1L
//                mThreshold = (duration * mPercent) / 100
//                mHandler.postDelayed(mLoop, 0)
//            }
//        }
//
//        /***
//         * スライダーによるシークを終了する
//         */
//        fun end() {
//            logger.debug("seek end")
//            if(mSeeking) {
//                mSeeking = false
//                if(mSeekTarget>=0) {
//                    exactSeek(mSeekTarget)
//                    mSeekTarget = -1
//                }
//            }
//        }
//
//        /***
//         * シークを要求する
//         */
//        fun request(pos:Long) {
//            logger.debug("seek request - $pos")
//            if(mSeeking) {
//                if (mSeekTarget < 0 || (pos - mSeekTarget).absoluteValue > mThreshold) {
//                    logger.debug("reset check count - $pos ($mCheckCounter)")
//                    mCheckCounter = 0
//                }
//                fastSeek(pos)
//                mSeekTarget = pos
//            } else {
//                exactSeek(pos)
//            }
//        }
//
//        /**
//         * 高速な（テキトーな）シーク
//         */
//        private fun fastSeek(pos:Long) {
//            logger.debug("fast seek - $pos")
//            if(isLoading.value) {
//                return
//            }
//            if(!mFastMode) {
//                logger.debug("switch to fast seek")
//                mFastMode = true
//                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
//            }
//            clippingSeekTo(pos)
//        }
//
//        /**
//         * 正確なシーク
//         */
//        fun exactSeek(pos:Long) {
//            logger.debug("exact seek - $pos")
//            if(mFastMode) {
//                logger.debug("switch to exact seek")
//                mFastMode = false
//                player.setSeekParameters(SeekParameters.EXACT)
//            }
//            clippingSeekTo(pos)
//        }
//
//        /**
//         * シーク中か？
//         */
//        val isSeeking:Boolean
//            get() = mSeeking
//    }
//    private var seekManager = SeekManager()

    /**
     * 外部に対して、ImmutableなStateFlowとして公開したプロパティを更新するために、MutableStateFlowにキャストする秘密のメソッド
     */
    private val <T> StateFlow<T>.mutable: MutableStateFlow<T>
        get() = this as MutableStateFlow<T>

    companion object {
        val logger by lazy { UtLog("PM", AmvSettings.logger) }
    }
}
