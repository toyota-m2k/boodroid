package io.github.toyota32k.dialog

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * アニメーション完了監視付きフェードイン/フェードアウト
 * フェードイン/フェードアウトアニメションを伴ってVISIBLE<->INVISIBLEの切り替えを行う。
 * @param show  true:フェードイン / false:フェードアウト
 * @param duration in milliseconds
 */
class UtFadeAnimation(val show:Boolean, duration:Long) : Animation.AnimationListener{
    private var view:View? = null
    private var completed:(()->Unit)? = null
//    private var who:Any? = null

    override fun onAnimationStart(animation: Animation?) {
//        UtDialogBase.logger.debug("$who")
        val view = this.view ?: return
        if(show) {
            view.visibility = View.VISIBLE
        } else {
            view.alpha = 1f
        }
    }

    override fun onAnimationEnd(animation: Animation?) {
//        UtDialogBase.logger.debug("$who")
        val view = this.view ?: return
        if(!show) {
            view.visibility = View.INVISIBLE
        } else {
            view.alpha = 1f
        }
        completed?.invoke()
        completed = null
        this.view = null
    }

    override fun onAnimationRepeat(animation: Animation?) {}

    private val animation = (if(show) AlphaAnimation(0f,1f) else AlphaAnimation(1f,0f)).also { anim->
    //        anim.isFillEnabled = true
    //        anim.fillBefore = true
        anim.fillAfter = true
        anim.duration = duration
        anim.setAnimationListener(this@UtFadeAnimation)
    }

    /**
     * fade in/out を開始
     * @param view  VISIBLE/INVISIBLEを切り替えるビュー
     * @param completed 完了ハンドラ
     */
    fun start(view:View, /*who:Any,*/ completed:(()->Unit)? = null) {
//        this.who = who
        this.completed = completed
        this.view = view
        view.startAnimation(animation)
    }

    /**
     * サスペンド関数版
     */
//    suspend fun startAsync(view:View, who:Any) {
//        suspendCoroutine<Unit> { cont->
//            start(view,who) {
//                cont.resume(Unit)
//            }
//        }
//    }
}