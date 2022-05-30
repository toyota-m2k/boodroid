package io.github.toyota32k.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import io.github.toyota32k.utils.dp2px
import io.github.toyota32k.utils.setLayoutHeight
import io.github.toyota32k.utils.setLayoutWidth
import io.github.toyota32k.utils.setMargin
import kotlin.math.max
import kotlin.math.min

@Suppress("unused", "MemberVisibilityCanBePrivate")
/**
 * @param isDialog  ダイアログモード or フラグメントモード
 *  true: ダイアログモード (新しいwindowを生成して配置）
 *  false: フラグメントモード (ActivityのWindow上に配置）
 */
abstract class UtDialog(isDialog:Boolean=UtDialogConfig.showInDialogModeAsDefault) : UtDialogBase(isDialog) {
    // region 動作/操作モード

    /**
     * bodyViewをスクロール可能にするかどうか。
     * trueにセットする場合は、sizingOptionを　COMPACT 以外にセットする。AUTO_SCROLLを推奨
     * createBodyView()より前（コンストラクタか、preCreateBodyView()）にセットする。
     */
    var scrollable:Boolean by bundle.booleanFalse

    /**
     * ダイアログ外をタップしてキャンセル可能にするか？
     * true:キャンセル可能（デフォルト）
     * false:キャンセル不可
     */
    private var lightCancelable:Boolean by bundle.booleanTrue
    var cancellable:Boolean
        get() = lightCancelable
        set(c) { updateCancelable(c) }

    /**
     * 画面外をタップしてダイアログを閉じるとき、Positive()扱いにするか？
     * true: positive扱い
     * false: negative扱い（デフォルト）
     */
    protected var positiveCancellable:Boolean by bundle.booleanFalse

    fun updateCancelable(c:Boolean) {
        if(lightCancelable!=c) {
            lightCancelable = c
            if(this::rootView.isInitialized) {
//                if (c) {
//                    rootView.setOnClickListener(this::onBackgroundTapped)
////                dialogView.setOnClickListener(this::onBackgroundTapped)
//                } else {
//                    rootView.setOnClickListener(null)
////                dialogView.setOnClickListener(null)
//                }
                applyGuardColor()
            }
        }
    }

    /**
     * DialogFragmentが isCancelable というプロパティを持っていていることに気づいた。
     * ダイアログの場合、あっちのは使わないから、そっとオーバーライドしておく。
     */
    override fun setCancelable(cancelable: Boolean) {
//        super.setCancelable(cancelable)
        updateCancelable(cancelable)
    }

    override fun isCancelable(): Boolean {
        return lightCancelable
    }


    /**
     * Drag&Dropによるダイアログ移動を許可するか？
     * true:許可する
     * false:許可しない（デフォルト）
     * createBodyView()より前（コンストラクタか、preCreateBodyView()）にセットする。
     */
    var draggable:Boolean by bundle.booleanFalse

    /**
     * ドラッグ中に上下方向の位置を画面内にクリップするか？
     * true: クリップする（デフォルト）
     * false:クリップしない
     * createBodyView()より前（コンストラクタか、preCreateBodyView()）にセットする。
     */
    var clipVerticalOnDrag:Boolean by bundle.booleanTrue

    /**
     * ドラッグ中に左右方向の位置を画面内にクリップするか？
     * true: クリップする（デフォルト）
     * false:クリップしない（とはいえ、操作できる程度にはクリップするよ。）
     * createBodyView()より前（コンストラクタか、preCreateBodyView()）にセットする。
     */
    var clipHorizontalOnDrag:Boolean by bundle.booleanTrue

    /**
     * ダイアログを開く/閉じるの動作にアニメション効果をつけるか？
     * true: つける
     * false:つけない
     */
    var animationEffect:Boolean by bundle.booleanTrue

    /**
     * ヘッダ（ok/cancelボタンやタイトル）無しにするか？
     * true: ヘッダなし
     * false: ヘッダあり（デフォルト）
     */
    var noHeader:Boolean by bundle.booleanFalse

    /**
     * bodyContainerのマージン
     * ボディ部分だけのメッセージ的ダイアログを作る場合に、noHeader=true と組み合わせて使うことを想定
     * 上下左右を個別にカスタマイズするときは、onViewCreated()で、bodyContainerのマージンを直接操作する。
     * -1: デフォルト（8dp)
     * >=0: カスタムマージン(dp)
     */
    var bodyContainerMargin:Int by bundle.intMinusOne

    // endregion

    // region ダイアログサイズ

    /**
     * 幅指定フラグ
     */
    @Suppress("unused")
    enum class WidthOption(val param:Int, val isDynamicSizing:Boolean) {
        COMPACT(WRAP_CONTENT,false),        // WRAP_CONTENT
        FULL(MATCH_PARENT,false),           // フルスクリーンに対して、MATCH_PARENT
        FIXED(WRAP_CONTENT,false),          // bodyの幅を、widthHint で与えられる値に固定
        LIMIT(WRAP_CONTENT,true),          // FULLと同じだが、widthHintで与えられるサイズでクリップされる。
    }

    /**
     * 高さ指定フラグ
     */
    @Suppress("unused")
    enum class HeightOption(val param:Int, val isDynamicSizing:Boolean) {
        COMPACT(WRAP_CONTENT,false),        // WRAP_CONTENT
        FULL(MATCH_PARENT,false),           // フルスクリーンに対して、MATCH_PARENT
        FIXED(WRAP_CONTENT,false),          // bodyの高さを、heightHint で与えられる値に固定
        LIMIT(WRAP_CONTENT,true),           // FULLと同じだが、heightHintで与えられるサイズでクリップされる。
        AUTO_SCROLL(WRAP_CONTENT,true),    // MATCH_PARENTを最大値として、コンテントが収まる高さに自動調整。収まらない場合はスクロールする。（bodyには MATCH_PARENTを指定)
        CUSTOM(WRAP_CONTENT,true),         // AUTO_SCROLL 的な配置をサブクラスで実装する。その場合、calcCustomContainerHeight() をオーバーライドすること。
    }

    /**
     * 幅の決定方法を指定
     * createBodyView()より前（コンストラクタか、preCreateBodyView()）にセットする。
     */
    var widthOption: WidthOption by bundle.enum(WidthOption.COMPACT)

    /**
     * 高さの決定方法を指定
     * createBodyView()より前（コンストラクタか、preCreateBodyView()）にセットする。
     */
    var heightOption: HeightOption by bundle.enum(HeightOption.COMPACT)

    /**
     * widthOption = FIXED or LIMIT を指定したときに、ダイアログ幅(dp)を指定
     * setFixedWidth() or setLimitWidth() メソッドの使用を推奨
     */
    var widthHint:Int by bundle.intZero

    /**
     * heightOption = FIXED の場合の、ダイアログ高さ(dp)を指定
     * setFixedHeight()メソッドの使用を推奨。
     */
    var heightHint:Int by bundle.intZero

    /**
     * ダイアログの高さを指定して、高さ固定モードにする。
     * createBodyView()より前（コンストラクタか、preCreateBodyView()）にセットする。
     */
    @Suppress("unused")
    fun setFixedHeight(height:Int) {
        if(isViewInitialized) {
            throw IllegalStateException("dialog rendering information must be set before preCreateBodyView")
        }
        heightOption = HeightOption.FIXED
        heightHint = height
    }

    /**
     * ダイアログの高さを指定して、最大高さ指定付き可変高さモードにする。
     * createBodyView()より前（コンストラクタか、preCreateBodyView()）にセットする。
     */
    fun setLimitHeight(height:Int) {
        if(isViewInitialized) {
            throw IllegalStateException("dialog rendering information must be set before preCreateBodyView")
        }
        heightOption = HeightOption.LIMIT
        heightHint = height
    }

    /**
     * ダイアログの幅を指定して、幅固定モードにする。
     * createBodyView()より前（コンストラクタか、preCreateBodyView()）にセットする。
     */
    @Suppress("unused")
    fun setFixedWidth(width:Int) {
        if(isViewInitialized) {
            throw IllegalStateException("dialog rendering information must be set before preCreateBodyView")
        }
        widthOption = WidthOption.FIXED
        widthHint = width
    }

    /**
     * ダイアログの幅を指定して、最大幅指定付き可変幅モードにする。
     * createBodyView()より前（コンストラクタか、preCreateBodyView()）にセットする。
     */
    fun setLimitWidth(width:Int) {
        if(isViewInitialized) {
            throw IllegalStateException("dialog rendering information must be set before preCreateBodyView")
        }
        widthOption = WidthOption.LIMIT
        widthHint = width
    }

    /**
     * heightOption = CUSTOM を設定する場合は、このメソッドをオーバーライドすること。
     * @param currentBodyHeight         現在のBodyビュー（createBodyViewが返したビュー）の高さ
     * @param currentContainerHeight    現在のコンテナ（Bodyの親）の高さ。マージンとか弄ってなければ currentBodyHeightと一致するはず。
     * @param maxContainerHeight        コンテナの高さの最大値（このサイズを超えないよう、Bodyの高さを更新すること）
     * @return コンテナの高さ（bodyではなく、containerの高さを返すこと）
     */
    protected open fun calcCustomContainerHeight(currentBodyHeight:Int, currentContainerHeight:Int, maxContainerHeight:Int):Int {
        error("calcCustomContainerHeight() must be overridden in subclass on setting 'heightOption==CUSTOM'")
    }

    /**
     * heightOption = CUSTOM の場合、bodyビュー (createBodyViewが返したビュー）の高さが変化したときに、
     * ダイアログサイズを再計算・更新するために、このメソッドを呼び出してください。
     */
    protected fun updateCustomHeight() {
        onRootViewSizeChanged()
    }

    // endregion

    // region ダイアログの表示位置

    /**
     * ダイアログ位置の指定フラグ
     */
    @Suppress("unused")
    enum class GravityOption(val gravity:Int) {
        RIGHT_TOP(Gravity.END or Gravity.TOP),          // 右上（デフォルト）
        CENTER(Gravity.CENTER),                                // 画面中央（メッセージボックス的）
        LEFT_TOP(Gravity.START or Gravity.TOP),         // 左上...ほかの組み合わせも作ろうと思えば作れるが、俺は使わん。
        CUSTOM(Gravity.START or Gravity.TOP),           // customPositionX/customPositionY で座標（rootViewに対するローカル座標）を指定
    }

    /**
     * ダイアログの表示位置を指定
     */
    var gravityOption: GravityOption by bundle.enum(GravityOption.RIGHT_TOP)

    /**
     * gravityOption == CUSTOM の場合のX座標（rootViewローカル座標）
     * D&D(draggable==true)中の座標保存にも使用。
     */
    var customPositionX: Float? by bundle.floatNullable

    /**
     * gravityOption == CUSTOM の場合のY座標（rootViewローカル座標）
     * D&D(draggable==true)中の座標保存にも使用。
     */
    var customPositionY: Float? by bundle.floatNullable

    // endregion

    // region ガードビュー（ダイアログの「画面外」）

    /**
     * ガードビュー（ダイアログの「画面外」）の背景の描画方法フラグ
     */
    enum class GuardColor(@ColorInt val color:Int) {
        INVALID(Color.argb(0,0,0,0)),                       // 透明（無効値）
        TRANSPARENT(Color.argb(0,0xFF,0xFF,0xFF)),          // 透明（通常、 cancellable == true のとき用）
        DIM(Color.argb(0x40,0,0,0)),                        // 黒っぽいやつ　（cancellable == false のとき用）
        @Suppress("unused")
        SEE_THROUGH(Color.argb(0x40,0xFF, 0xFF, 0xFF)),     // 白っぽいやつ　（好みで）
        SOLID_GRAY(Color.rgb(0xc1,0xc1,0xc1)),
    }

    /**
     * ガードビューの色
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var guardColor:Int by bundle.intNonnull(GuardColor.INVALID.color)

    /**
     * ガードビューに色は設定されているか？
     */
    private val hasGuardColor:Boolean
        get() = guardColor!= GuardColor.INVALID.color


    /**
     * 実際に描画するガードビューの背景色を取得
     * 優先度
     * １．明示的に設定されている色
     * ２．画面外タップで閉じない（!cancellable)場合は、DIM
     * ３．画面外タップで閉じる(cancellable)なら、無色透明
     */
    @ColorInt
    private fun managedGuardColor():Int {
        return when {
            UtDialogConfig.solidBackgroundOnPhone && isPhone -> GuardColor.SOLID_GRAY.color
            hasGuardColor -> guardColor
            !lightCancelable-> GuardColor.DIM.color
            else-> GuardColor.TRANSPARENT.color
        }
    }

    /**
     * 親ダイアログの状態を考慮したガードビューの背景色を設定する
     */
    protected fun applyGuardColor() {
        val color = managedGuardColor()
        rootView.background = ColorDrawable(color)
    }

    // endregion

    // region ダイアログの親子関係、表示/非表示

    /**
     * 子ダイアログを開くときに親ダイアログを隠すかどうかのフラグ
     */
    enum class ParentVisibilityOption {
        NONE,                       // 何もしない：表示しっぱなし
        HIDE_AND_SHOW,              // このダイアログを開くときに非表示にして、閉じるときに表示する
        HIDE_AND_SHOW_ON_NEGATIVE,  // onNegativeで閉じるときには、親を表示する。Positiveのときは非表示のまま
        HIDE_AND_SHOW_ON_POSITIVE,  // onPositiveで閉じるときには、親を表示する。Negativeのときは非表示のまま
        HIDE_AND_LEAVE_IT,          // このダイアログを開くときに非表示にして、あとは知らん
    }
    /**
     * 子ダイアログを開くときに親ダイアログの隠し方
     */
    var parentVisibilityOption by bundle.enum(ParentVisibilityOption.HIDE_AND_SHOW)

    /**
     * ダイアログの表示/非表示
     */
    var visible:Boolean
        get() = rootView.visibility == View.VISIBLE
        set(v) { rootView.visibility = if(v) View.VISIBLE else View.INVISIBLE }

    private val fadeInAnimation = UtFadeAnimation(true,UtDialogConfig.fadeInDuration)
    private val fadeOutAnimation = UtFadeAnimation(false, UtDialogConfig.fadeOutDuraton)

    fun fadeIn(completed:(()->Unit)?=null) {
        if(!this::rootView.isInitialized) {
            completed?.invoke()         // onCreateViewでnullを返す（開かないでcancelされる）ダイアログの場合、ここに入ってくる
        } else if(animationEffect) {
            fadeInAnimation.start(rootView) {
                completed?.invoke()
            }
        } else {
            visible = true
            completed?.invoke()
        }
    }

    fun fadeOut(completed:(()->Unit)?=null) {
        if(!this::rootView.isInitialized) {
            completed?.invoke()
        } else if(animationEffect) {
            fadeOutAnimation.start(rootView) {
                visible = false
                completed?.invoke()
            }
        } else {
            visible = false
            completed?.invoke()
        }
    }

    /**
     * ルートダイアログ（ダイアログチェーンの先頭）を取得
     */
    val rootDialog : UtDialog?
        get() = UtDialogHelper.rootDialog(requireActivity())

    /**
     * 親ダイアログを取得
     * 自身がルートならnullを返す。
     */
    val parentDialog : UtDialog?
        get() = UtDialogHelper.parentDialog(this)

    // endregion

    // region タイトルバー

    /**
     * タイトル
     */
    private var privateTitle:String? by bundle.stringNullable
    var title:String?
        get() = privateTitle
        set(v) = replaceTitle(v)

    /**
     * ダイアログ構築後に（動的に）ダイアログタイトルを変更する。
     */
    open fun replaceTitle(title:String?) {
        this.privateTitle = title
        if(this::titleView.isInitialized) {
            titleView.text = title
        }
    }

    /**
     * タイトルバーに表示するボタンのタイプ
     * 標準ボタンのポリシー
     * - タイトルの左はNegativeボタン、 右はPositiveボタンを配置する。
     * - 「閉じる」ボタンは、右（CLOSE：Positive）にも、左（CLOSE_LEFT:Negative）にも配置可能。
     * setLeftButton(), setRightButton()で、これら標準以外のボタンを作成することは可能だが、あまりポリシーから逸脱しないように。
     */
    @Suppress("unused")
    enum class BuiltInButtonType(val string:UtStandardString, val positive:Boolean, val blueColor:Boolean) {
        OK(UtStandardString.OK, true, true),                // OK
        DONE(UtStandardString.DONE, true, true),            // 完了
        CLOSE(UtStandardString.CLOSE, true, true),          // 閉じる

        CANCEL(UtStandardString.CANCEL, false, false),      // キャンセル
        BACK(UtStandardString.BACK, false, false),          // 戻る
        CLOSE_LEFT(UtStandardString.CLOSE, false, false),   // 閉じる

        NONE(UtStandardString.NONE, false, false),          // ボタンなし
    }

    // 左ボタンのプロパティ (setLeftButton()で設定）
    private var leftButtonText:Int by bundle.intZero
    private var leftButtonPositive:Boolean by bundle.booleanFalse
    private var leftButtonBlue:Boolean by bundle.booleanFalse
    @Suppress("unused")
    val hasLeftButton:Boolean
        get() = leftButtonText > 0

    // 右ボタンのプロパティ（setRightButton()で設定）
    private var rightButtonText:Int by bundle.intZero
    private var rightButtonPositive:Boolean by bundle.booleanFalse
    private var rightButtonBlue:Boolean by bundle.booleanFalse
    @Suppress("unused")
    val hasRightButton:Boolean
        get() = rightButtonText > 0

    /**
     * 左ボタンのプロパティを細かく指定
     * @param id        ボタンキャプションの文字列リソースID
     * @param positive  true:Positiveボタン(タップしてonPositiveを発行）
     *                  false:Negativeボタン（タップしてonNegativeを発行）
     * @param blue      true:Blueボタン（通常、Positiveボタンに使用）
     *                  false:Whiteボタン（通常、Negativeボタンに使用）
     */
    fun setLeftButton(@StringRes id:Int, positive: Boolean=false, blue:Boolean=positive) {
        leftButtonText = id
        leftButtonPositive = positive
        leftButtonBlue = blue
        if(isViewInitialized) {
            updateLeftButton()
        }
    }

    /**
     * 左ボタンのプロパティをタイプで指定
     */
    fun setLeftButton(type: BuiltInButtonType) {
        setLeftButton(type.string.id, type.positive, type.blueColor)
    }

    /**
     * 右ボタンのプロパティを細かく指定
     * @param id        ボタンキャプションの文字列リソースID
     * @param positive  true:Positiveボタン(タップしてonPositiveを発行）
     *                  false:Negativeボタン（タップしてonNegativeを発行）
     * @param blue      true:Blueボタン（通常、Positiveボタンに使用）
     *                  false:Whiteボタン（通常、Negativeボタンに使用）
     */
    fun setRightButton(@StringRes id:Int, positive: Boolean=false, blue:Boolean=positive) {
        rightButtonText = id
        rightButtonPositive = positive
        rightButtonBlue = blue
        if(isViewInitialized) {
            updateRightButton()
        }
    }

    /**
     * 右ボタンのプロパティをタイプで指定
     */
    fun setRightButton(type: BuiltInButtonType) {
        setRightButton(type.string.id, type.positive)
    }

    private val themedContext:Context by lazy { ContextThemeWrapper(super.getContext(), UtDialogConfig.dialogTheme) }
    override fun getContext(): Context {
        return themedContext
    }

    /**
     * ボタンプロパティを、ビュー(Button)に反映する
     */
    private fun updateButton(button:Button, @StringRes id:Int, blue:Boolean) {
        activity?.apply {
            button.text = getText(id)
            if(blue) {
                button.background = ContextCompat.getDrawable(context, R.drawable.dlg_button_bg_blue)
                button.setTextColor(context.getColorStateList(R.color.dlg_button_fg_blue))
            } else {
                button.background = ContextCompat.getDrawable(context, R.drawable.dlg_button_bg_white)
                button.setTextColor(context.getColorStateList(R.color.dlg_button_fg_white))
            }

        }
    }

    /**
     * 左ボタンのプロパティをビュー(Button)に反映
     */
    private fun updateLeftButton() {
        val id = leftButtonText
        if(id!=0) {
            updateButton(leftButton, id, leftButtonBlue)
        } else {
            leftButton.visibility = View.GONE
        }
    }
    /**
     * 右ボタンのプロパティをビュー(Button)に反映
     */
    private fun updateRightButton() {
        val id = rightButtonText
        if(id!=0) {
            updateButton(rightButton, id, rightButtonBlue)
        } else {
            rightButton.visibility = View.GONE
        }
    }

    // endregion

    // region デバイス情報

    /**
     * デバイスの向き
     */
    val orientation:Int
        get() = resources.configuration.orientation

    /**
     * デバイスは横置き（ランドスケープか）？
     */
    val isLandscape:Boolean
        get() = orientation == Configuration.ORIENTATION_LANDSCAPE

    /**
     * デバイスは縦置き（ポートレート）か？
     */
    val isPortrait:Boolean
        get() = orientation == Configuration.ORIENTATION_PORTRAIT

    /**
     * デバイスはPhoneか？（600dp以下をPhoneと判定）
     */
    val isPhone:Boolean
        get() = resources.getBoolean(R.bool.under600dp)

    /**
     * デバイスはタブレットか？
     */
    val isTablet:Boolean
        get() = !isPhone

    // endregion

    // region ドラッグ＆ドロップ

    /**
     * 軸方向毎のドラッグ情報を保持するクラス
     */
    private class DragParam {
        private var dialogSize:Float = 0f
        private var screenSize:Float = 0f
        private var clip:Boolean = false
        private var minPos:Float = 0f
        private var maxPos:Float = 0f

        private var orgDialogPos:Float = 0f
        private var dragStartPos:Float = 0f

        fun setup(dialogSize:Float, screenSize:Float, clip:Boolean, minPos:Float, maxPos:Float) {
            this.dialogSize = dialogSize
            this.screenSize = screenSize
            this.clip = clip
            this.minPos = minPos
            this.maxPos = maxPos
        }

        fun start(dialogPos:Float, dragPos:Float) {
            dragStartPos = dragPos
            orgDialogPos = dialogPos
        }

        /**
         * ドラッグ後の位置を取得
         */
        fun getPosition(dragPos:Float):Float {
            val newPos = orgDialogPos + (dragPos-dragStartPos)
            return clipPosition(newPos)
        }

        /**
         * ダイアログの位置を画面内にクリップする
         */
        fun clipPosition(pos:Float):Float {
            return if (clip) {
                max(0f, min(pos, screenSize - dialogSize))
            } else {
                max(minPos, min(pos, screenSize - maxPos))
            }
        }
    }

    /**
     * ドラッグ情報クラス
     */
    inner class DragInfo {
        private var dragging:Boolean = false
        private val x = DragParam()
        private val y = DragParam()

        /**
         * サイズ情報を初期化
         */
        private fun setup() {
            val w = dialogView.width.toFloat()
            x.setup(w, rootView.width.toFloat(),clipHorizontalOnDrag, -w/2f, w/2f)
            y.setup(dialogView.height.toFloat(), rootView.height.toFloat(), clipVerticalOnDrag, 0f, requireContext().dp2px(50).toFloat())
        }

        /**
         * D&Dまたは、GravityOption.CUSTOMの場合に、ダイアログが画面（rootView)内に収まるよう、座標位置を補正する。
         */
        fun adjustPosition(xp: Float?, yp: Float?) {
            setup()
            if(xp!=null) {
                dialogView.x = x.clipPosition(xp)
            }
            if(yp!=null) {
                dialogView.y = y.clipPosition(yp)
            }
        }

        /**
         * ドラッグ開始
         */
        fun start(ev: MotionEvent) {
            setup()
            x.start(dialogView.x, ev.rawX)
            y.start(dialogView.y, ev.rawY)
            dragging = true
        }

        /**
         * ドラッグによる位置移動
         */
        fun move(ev:MotionEvent) {
            if(!dragging) return
            val x = x.getPosition(ev.rawX)
            dialogView.x = x
            customPositionX = x
            val y = y.getPosition(ev.rawY)
            dialogView.y = y
            customPositionY = y
        }

        /**
         * ドラッグ終了
         */
        fun cancel() {
            dragging = false
        }
    }

    /**
     * D&Dで移動したダイアログの位置を元に戻す。
     */
    private fun resetDialogPosition() {
        dialogView.translationX = 0f
        dialogView.translationY = 0f
        customPositionX = null
        customPositionY = null
    }

    /**
     * ドラッグ情報
     */
    private val dragInfo:DragInfo by lazy { DragInfo() }

    /**
     * ドラッグによるダイアログ移動に必要なタッチイベントのハンドラを登録する。
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun enableDrag() {
        if(!draggable) return
        val gestureDetector = GestureDetector(context, object:GestureDetector.SimpleOnGestureListener(){
            override fun onDoubleTap(e: MotionEvent?): Boolean {
                // スペシャルサービス仕様：ダブルタップで元の位置に戻してあげよう。
                resetDialogPosition()
                dragInfo.cancel()   // double tap の２回目タップで移動しないように。
                return true
            }
        })
        rootView.findViewById<FrameLayout>(R.id.header).setOnTouchListener { _, ev->
            if(!gestureDetector.onTouchEvent(ev)) {
                if (ev.action == MotionEvent.ACTION_DOWN) {
                    dragInfo.start(ev)
                } else if (ev.action == MotionEvent.ACTION_MOVE) {
                    dragInfo.move(ev)
                }
            }
            true
        }
    }

    // endregion

    // region ビルトインビュー

    lateinit var titleView:TextView
        private set
    lateinit var leftButton: Button
        private set
    lateinit var rightButton: Button
        private set
    lateinit var progressRingOnTitleBar: ProgressBar
        private set

    lateinit var rootView: ViewGroup              // 全画面を覆う透過の背景となるダイアログのルート：
        protected set
    lateinit var dialogView:ViewGroup        // ダイアログ画面としてユーザーに見えるビュー。rootView上で位置、サイズを調整する。
        protected set
    lateinit var bodyContainer:ViewGroup          // bodyViewの入れ物
        private set
    lateinit var bodyView:View                      // UtDialogを継承するサブクラス毎に作成されるダイアログの中身  (createBodyView()で構築されたビュー）
        private set
    lateinit var refContainerView:View              // コンテナ領域（ダイアログ領域ーヘッダー領域）にフィットするダミービュー
        private set
    lateinit var bodyGuardView:View                 // dialogContentへの操作をブロックするためのガードビュー
        private set

    // endregion

    // region レンダリング

    /**
     * widthOption/heightOption/gravityOptionに従って、 bodyContainerのLayoutParamを設定する。
     * 構築時：onCreateDialog()から実行
     */
    private fun setupLayout() {
        dialogView.layoutParams = FrameLayout.LayoutParams(widthOption.param, heightOption.param, gravityOption.gravity)
        if(heightOption== HeightOption.FULL) {
            bodyContainer.setLayoutHeight(0)
        }
        if(widthOption== WidthOption.FULL) {
            bodyContainer.setLayoutWidth(0)
        }
        setupFixedSize()
        setupDynamicSize()
    }

    /**
     * widthOption/heightOptionで FIXEDが指定されているときに、
     * widthHint/heightHintで与えられたサイズに従ってLayoutParamsを設定する。
     */
    private fun setupFixedSize() {
        val fw = if(widthOption== WidthOption.FIXED) widthHint else null
        val fh = if(heightOption== HeightOption.FIXED) heightHint else null
        if(fw==null && fh==null) return

        val lp = bodyContainer.layoutParams ?: return
        if(fw!=null) {
            lp.width = requireContext().dp2px(fw)
        }
        if(fh!=null) {
            lp.height = requireContext().dp2px(fh)
        }
        bodyContainer.layoutParams = lp
    }

    /**
     * widthOption == LIMITまたは、heightOption==AUTO_SCROLL/CUSTOMの場合に、レイアウト更新のため、また、
     * draggable==true または、gravityOption == CUSTOM の場合は、位置補正（画面内にクリップ）のために、それぞれサイズ変更イベントをフックする。
     */
    private fun setupDynamicSize() {
        if(widthOption.isDynamicSizing || heightOption.isDynamicSizing || draggable || gravityOption == GravityOption.CUSTOM) {
            // デバイス回転などによるスクリーンサイズ変更を検出するため、ルートビューのサイズ変更を監視する。
            rootView.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                if (or - ol != r - l || ob - ot != b - t) {
                    onRootViewSizeChanged()
                }
                adjustDialogPosition(customPositionX, customPositionY)
            }
            refContainerView.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ ->
                logger.debug("x:org ${dialogView.x} layoutChanged")
                if (or - ol != r - l) {
                    onContainerHeightChanged()
                }
            }
        }
        if(heightOption== HeightOption.AUTO_SCROLL) {
            // コンテンツ（bodyViewの中身）の増減によるbodyViewサイズの変更を監視する。
            bodyView.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                if (or - ol != r - l || ob - ot != b - t) {
                    onBodyViewSizeChanged()
                }
            }
        }
    }

    /**
     * リサイズ時の高さ調整
     * （HeightOption.AUTO_SCROLL, HeightOption.CUSTOMのための処理）
     */
    private fun updateDynamicHeight(lp:ConstraintLayout.LayoutParams) : Boolean {
        if(heightOption.isDynamicSizing) {
            val winHeight = rootView.height
            if(winHeight==0) return false
            val containerHeight = refContainerView.height
            val dlgHeight = dialogView.height
            val bodyHeight = bodyView.height
            val maxContainerHeight = winHeight - (dlgHeight - containerHeight)

            val newContainerHeight = when(heightOption) {
                HeightOption.AUTO_SCROLL -> min(bodyHeight, maxContainerHeight)
                HeightOption.LIMIT -> min(maxContainerHeight, requireContext().dp2px(heightHint))
                HeightOption.CUSTOM-> calcCustomContainerHeight(bodyHeight,containerHeight,maxContainerHeight)
                else-> return false
            }

//            logger.info("window:${winHeight}, scroller:$scrHeight, dialogView:$dlgHeight, bodyHeight:$bodyHeight, maxScrHeight=$maxScrHeight, newScrHeight=$newScrHeight")
            if(lp.height != newContainerHeight) {
                lp.height = newContainerHeight
                return true
            }
        }
        return false
    }

    /**
     * リサイズ時の幅調整
     *　（WidthOption.LIMIT用の処理）
     */
    private fun updateDynamicWidth(lp:ConstraintLayout.LayoutParams) : Boolean {
        if(widthOption== WidthOption.LIMIT) {
            val winWidth = rootView.width
            if(winWidth==0) return false
            val dlgWidth = dialogView.width
            val cntWidth = dlgWidth - lp.marginStart - lp.marginEnd // bodyContainer.width
            val maxCntWidth = winWidth - (dlgWidth-cntWidth)
            val newCntWidth = min(maxCntWidth, requireContext().dp2px(widthHint))
            if(lp.width!=newCntWidth) {
                lp.width = newCntWidth
                return true
            }
        }
        return false
    }

    /**
     * 画面（rootView)内に収まるよう補正して、ダイアログ位置を設定
     */
    private fun adjustDialogPosition(x:Float?,y:Float?) {
        if(x!=null||y!=null) {
            dragInfo.adjustPosition(x,y)
        }
    }

    /**
     * rootViewのサイズが変化したとき（≒デバイス回転）にレンダリング処理
     */
    private fun onRootViewSizeChanged() {
        val lp = bodyContainer.layoutParams as ConstraintLayout.LayoutParams
        val h = updateDynamicHeight(lp)
        val w = updateDynamicWidth(lp)
        if(h||w) {
            bodyContainer.layoutParams = lp
        }
    }

    private fun onContainerHeightChanged() {
        val lp = bodyContainer.layoutParams as ConstraintLayout.LayoutParams
        if(updateDynamicHeight(lp)) {
            bodyContainer.layoutParams = lp
        }
    }

    /**
     * bodyViewのサイズが変更したときの処理。
     * bodyViewの高さに合わせて、bodyContainerの高さを更新する。
     */
    private fun onBodyViewSizeChanged() {
        if (heightOption == HeightOption.AUTO_SCROLL) {
            val lp = bodyContainer.layoutParams as ConstraintLayout.LayoutParams
            if(updateDynamicHeight(lp)) {
                // bodyView のOnLayoutChangeListenerの中から、コンテナのサイズを変更しても、なんか１回ずつ無視されるので、ちょっと遅延する。
                Handler(Looper.getMainLooper()).post {
                    bodyContainer.layoutParams = lp
                }
            }
        }
    }

    // endregion

    // region ダイアログの構築

    val isViewInitialized:Boolean
       get() = this::rootView.isInitialized

    /**
     * ダイアログ構築前の処理
     * titleViewなど、UtDialog側のビューを構築するまえに行うべき初期化処理を行う。
     */
    open fun preCreateBodyView() {
    }

    /**
     * ダイアログビュー専用インフレーター
     * createBodyView()の中で、BodyViewを構築するとき、必ず Dialog#layoutInflater（this.dialog.layoutInflater)を使い、
     * LayoutInflater.layoutInflater()の第２引数(root)に、bodyContainer、第３引数（attachToRoot）にfalse を渡さないと正しく動作しない。
     * これを「規約」として利用者の責任に帰するのは酷なので、これらの引数を隠ぺいした専用インフレーターを渡すAPIとし、そのためのi/fを定義する。
     *
     */

    interface IViewInflater {
        fun inflate(@LayoutRes id:Int):View
    }

    /**
     * ダイアログのビューを構築する
     * @param savedInstanceState    FragmentDialog.onCreateView に渡された状態復元用パラメータ
     * @param inflater              専用インフレーター
     */
    protected abstract fun createBodyView(savedInstanceState:Bundle?, inflater: IViewInflater): View

    /**
     * IViewInflaterの実装クラス
     */
    private data class ViewInflater(val layoutInflater:LayoutInflater, val bodyContainer:ViewGroup): IViewInflater {
        override fun inflate(id: Int): View {
            return layoutInflater.inflate(id, bodyContainer, false)
        }
    }

    /**
     * isDialog == true の場合に呼ばれる。
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), R.style.dlg_style).apply {
            window?.setBackgroundDrawable(ColorDrawable(GuardColor.TRANSPARENT.color))
        }
    }

    /**
     * コンテントビュー生成処理
     */
    override fun onCreateView(orgInflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        try {
            val inflater = orgInflater.cloneInContext(context)
            if(UtDialogConfig.solidBackgroundOnPhone && isPhone) {
                animationEffect = false
            }
            preCreateBodyView()
            rootView = inflater.inflate(R.layout.dialog_frame, container, false) as FrameLayout
            if(noHeader) {
                rootView.findViewById<View>(R.id.header).visibility = View.GONE
                rootView.findViewById<View>(R.id.separator).visibility = View.GONE
            }
            leftButton = rootView.findViewById(R.id.left_button)
            rightButton = rootView.findViewById(R.id.right_button)
            titleView = rootView.findViewById(R.id.dialog_title)
            progressRingOnTitleBar = rootView.findViewById(R.id.progress_on_title_bar)
            dialogView = rootView.findViewById(R.id.dialog_view)
            refContainerView = rootView.findViewById(R.id.ref_container_view)
            bodyGuardView = rootView.findViewById(R.id.body_guard_view)
            dialogView.isClickable = true   // これをセットしておかないと、ヘッダーなどのクリックで rootViewのonClickが呼ばれて、ダイアログが閉じてしまう。
            title?.let { titleView.text = it }
            if (heightOption == HeightOption.AUTO_SCROLL) {
                scrollable = true
            } else if (heightOption == HeightOption.CUSTOM) {
                scrollable = false
            }
            bodyContainer = if (scrollable) {
                rootView.findViewById(R.id.body_scroller)
            } else {
                rootView.findViewById(R.id.body_container)
            }
            val margin = bodyContainerMargin
            if(margin>=0) {
                bodyContainer.setMargin(margin, margin, margin, margin)
            }
            bodyContainer.visibility = View.VISIBLE
            leftButton.setOnClickListener(this::onLeftButtonTapped)
            rightButton.setOnClickListener(this::onRightButtonTapped)

            rootView.setOnClickListener(this@UtDialog::onBackgroundTapped)
//            rootView.isFocusableInTouchMode = true
//            rootView.setOnKeyListener(this@UtDialog::onBackgroundKeyListener)
//          画面外タップで閉じるかどうかにかかわらず、リスナーをセットする。そうしないと、ダイアログ外のビューで操作できてしまう。
//            if (lightCancelable) {
//                rootView.setOnClickListener(this@UtDialog::onBackgroundTapped)
//            }
            updateLeftButton()
            updateRightButton()
            bodyView = createBodyView(savedInstanceState, ViewInflater(inflater, bodyContainer))
            bodyContainer.addView(bodyView)
            setupLayout()
//            dlg?.setContentView(rootView)
            if (draggable) {
                enableDrag()
            }
            applyGuardColor()
            if(savedInstanceState==null) {
                // 新しくダイアログを開く
                // アニメーションして開くときは、初期状態を非表示にしておく。
                if (animationEffect) {
                    this.visible = false
                }
            } else {
                // 回転などによる状態復元
                if (parentVisibilityOption != ParentVisibilityOption.NONE) {
                    parentDialog?.visible = false
                }
            }
            return rootView
        } catch (e: Throwable) {
            // View作り中のエラーは、デフォルトでログに出る間もなく死んでしまうようなので、キャッチして出力する。throwし直すから死ぬけど。
            logger.stackTrace(e)
            throw e
        }
    }

    // endregion

    // region イベント

    override fun internalCloseDialog() {
        fadeOut {
            dismiss()
        }
    }

    /**
     * ダイアログが表示されるときのイベントハンドラ
     */
    override fun onDialogOpening() {
        fadeIn()
        parentDialog?.let { parent ->
            if (!parent.status.finished) {   // parentのcompleteハンドラの中から別のダイアログを開く場合、parentのfadeOutが完了する前に、ここからfadeOutの追撃が行われ、completeハンドラがクリアされて親ダイアログが閉じられなくなってしまう
                // 子ダイアログが開いた後、親ダイアログが開いたソフトウェアキーボードが残ってしまうと嫌なので、明示的に閉じておく
                parent.hideSoftwareKeyboard()
                if (parentVisibilityOption != ParentVisibilityOption.NONE) {
                    parent.fadeOut()
                }
            }
        }
    }

    /**
     * InputMethodManagerインスタンスの取得
     */

    val immService: InputMethodManager?
        get() = try { requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        } catch(_:Throwable) { null }

    /**
     * ソフトウェアキーボードを非表示にする。
     */
    fun hideSoftwareKeyboard() {
       immService?.hideSoftInputFromWindow(rootView.windowToken, 0)
    }
    /**
     * ダイアログが閉じる前のイベントハンドラ
     */
    override fun onDialogClosing() {
        if(!isViewInitialized) {
            // onDialogOpening (≒ onViewCreated)が呼ばれずに onDialogClosing()が呼ばれた
            return
        }

        // Android7/8 でダイアログが閉じてもSoftware Keyboardが閉じない事例あり
        hideSoftwareKeyboard()

        // Chromebookで、HWキーボードの候補ウィンドウが残ってしまうのを防止
        // ここで requestFocusFromTouchを呼ぶと、ダイアログが閉じてから、へんなビューのレイヤー（ゾンビ的なやつ？）が親ダイアログの上に出現してしまう。
        // Layout Inspector で見たら、親ダイアログの FrameLayout が、子ビューの上に飛び出しているように見える。。。気持ち悪い。
        // これが原因であることを突き止めるのに、丸二日かかったぞ。
//        rootView.requestFocusFromTouch()

        // 親ダイアログの表示状態を復元
        val parent = parentDialog ?: return
        if(  parentVisibilityOption==ParentVisibilityOption.HIDE_AND_SHOW ||
            (parentVisibilityOption==ParentVisibilityOption.HIDE_AND_SHOW_ON_NEGATIVE && status.negative) ||
            (parentVisibilityOption==ParentVisibilityOption.HIDE_AND_SHOW_ON_POSITIVE && status.positive)) {
            parent.fadeIn()
        }
    }

    override fun onDialogClosed() {
        super.onDialogClosed()
        // Chromebookで、HWキーボードの候補ウィンドウが残ってしまうのを防止
        rootView.requestFocusFromTouch()
    }

    /**
     * 背景（ガードビュー）がタップされたときのハンドラ
     */
    protected open fun onBackgroundTapped(view:View) {
        if(view==rootView && lightCancelable) {
            if(positiveCancellable) {
                onPositive()
            } else {
                onNegative()
            }
        }
    }

    /**
     * キーイベントハンドラ
     * これは、FragmentやDialogFragmentのメソッドではなく、オリジナルのやつ。UtMortalActivity#onKeyDown()ががんばって呼び出している。
     * デフォルトでは、BACK, CANCELでダイアログを閉じる。
     * @return  true:イベントを消費した / false:消費しなかった
     */
    open fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!isDialog && keyCode==KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            cancel()
            return true
        }
        return false
    }

    // rootViewで、Backキーによるダイアログキャンセルを行い、
    // かつ、ダイアログ表示中に、親Activityがキーイベントを拾ってしまうのを防止できれば、と考えたが、
    // フォーカスがない状態では、onKeyListenerが呼ばれないので、役に立たなかった。残念。
//    protected open fun onBackgroundKeyListener(view:View, keyCode: Int, keyEvent: KeyEvent?): Boolean {
//        if(keyCode==KeyEvent.KEYCODE_BACK) {
//            if(!status.finished) {
//                cancel()
//            }
//        }
//        return true
//    }


    /**
     * 左ボタンがタップされたときのハンドラ
     */
    protected open fun onLeftButtonTapped(view:View) {
        if(leftButtonPositive) {
            onPositive()
        } else {
            onNegative()
        }
    }

    /**
     * 右ボタンがタップされたときのハンドラ
     */
    protected open fun onRightButtonTapped(view:View) {
        if(rightButtonPositive) {
            onPositive()
        } else {
            onNegative()
        }
    }

    /**
     * ネガティブボタンがタップされた。
     */
    protected open fun onNegative() {
        if(confirmToCompleteNegative()) {
            cancel()
        }
    }

    /**
     * ポジティブボタンがタップされた。
     */
    protected open fun onPositive() {
        if(confirmToCompletePositive()) {
            complete(IUtDialog.Status.POSITIVE)
        }
    }

    /**
     * OK/Done でダイアログを閉じてもよいか（必要な情報は揃ったか）？
     */
    protected open fun confirmToCompletePositive():Boolean {
        return true
    }

    /**
     * キャンセルしてもよいか？（普通はオーバーライド不要・・・通信中に閉じないようにするとか。。。)
     */
    protected open fun confirmToCompleteNegative():Boolean {
        return true
    }

    // endregion

}
