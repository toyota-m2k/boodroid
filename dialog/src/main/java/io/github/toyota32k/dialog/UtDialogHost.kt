@file:Suppress("unused")

package io.github.toyota32k.dialog

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import java.lang.ref.WeakReference

/**
 * ダイアログの処理が終わったときに、その結果（ダイアログインスタンス）を返すためのi/f
 * Activity / Fragment / ViewModel などで継承・実装する。
 */
interface IUtDialogResultReceptor {
    fun onDialogResult(caller: IUtDialog)
}

/**
 * タグ(Fragment#tag)をキーに、IUtDialogResultReceptor を返すための i/f
 * Activity / Fragment で継承・実装する
 */
interface IUtDialogHost {
    fun queryDialogResultReceptor(tag:String): IUtDialogResultReceptor?
}

/**
 * IUtDialogHostの実装
 * - Activity / Fragment(Dialog) / ViewModel などのフィールドとしてインスタンス化する。
 * - ViewModel.uiDialogHostManager は、UtDialogから直接参照できないので、ActivityまたはFragmentから参照できるようにしておく。
 * - 集中管理（１つのインスタンスで管理）する場合
 *   - 最もライフサイクルの長い ViewModelに配置
 *   - 必要なら、Activity/FragmentのonCreateあたりでaddReceptor, onDestroyあたりでremoveReceptorする。
 *   - Activityまたは、FragmentでIUtDialogHostを継承し、queryDialogResultReceptor()で、ViewModel.uiDialogHostManager.queryDialogResultReceptor() を返すようにする。
 * - 分散管理（Activity/Fragment/ViewModelにそれぞれインスタンスを配置）する場合
 *  - それぞれのライフサイクルでそれぞれのインスタンスを管理
 *  - ViewModelにUtDialogHostManagerを配置するときは、Activity/FragmentのUtDialogHostManagerを addChildHost()しておき、Activity#queryDialogResultRecepterから、これを参照するようにするのがよい。
 *  - この場合、onDestroyでremoveChildHost()しておかないとActivityインスタンスがリークするので注意
 */
class UtDialogHostManager: IUtDialogHost {
    /**
     * ラムダで与えられたReceptorを　IUtDialogResultReceptor に変換するためのラッパークラス
     * Java6からも使えるように、内部ではKFunctionではなく、IUtDialogResultReceptor i/f として保持しておく。
     */
    data class ReceptorWrapper(val fn:(caller: IUtDialog)->Unit): IUtDialogResultReceptor {
        override fun onDialogResult(caller: IUtDialog) {
            fn(caller)
        }
    }

    /**
     * 名前 --> Receptor マップ
     */
    private val receptorMap = mutableMapOf<String, IUtDialogResultReceptor>()

    /**
     * IUtDialogHostのリスト
     * addChildHost(), removeChildHost()メソッドを使うことで、UtDialogHostManagerは、階層構造を持つことができる。
     * ViewModel に親ホストを持たせて、ActivityやFragmentの子ホストを追加する、とか。
     */
    private val hostList = mutableListOf<IUtDialogHost>()

    /**
     * IUtDialogHost のi/f
     * 通常は、ActivityかFragmentをIUtDialogHost i/f 継承とし、その queryDialogResultReceptor から、このメソッドを呼び出すようにする。
     */
    override fun queryDialogResultReceptor(tag: String): IUtDialogResultReceptor? {
        var r = receptorMap[tag]
        if(r!=null) {
            return r
        }
        for(h in hostList) {
            r = h.queryDialogResultReceptor(tag)
            if(r!=null) {
                return r
            }
        }
        return null
    }

    operator fun set(tag:String, r: IUtDialogResultReceptor?) {
        if(r!=null) {
            receptorMap[tag] = r
        } else {
            receptorMap.remove(tag)
        }
    }

    /**
     * タグに対する receptor を登録する。（インデクサ）
     */
    operator fun set(tag:String, fn:(IUtDialog)->Unit) {
        receptorMap[tag] = ReceptorWrapper(fn)
    }

    /**
     * タグに対する receptor を登録する。（インデクサ）
     */
    operator fun get(tag:String): IUtDialogResultReceptor? {
        return queryDialogResultReceptor(tag)
    }

    /**
     * タグに対する receptor を登録する。
     */
    fun setReceptor(tag:String, r: IUtDialogResultReceptor) {
        this[tag] = r
    }

    /**
     * タグに対する receptor を登録する。
     */
    fun setReceptor(tag:String, fn:(IUtDialog)->Unit) {
        this[tag] = fn
    }

    // uuidを使ってTagを自動発行とするのは、実装をシンプルにするよいアイデアだと思われたが、
    // 発行されたtagをどこに覚えておくか、ActivityやFragmentでは当然ダメで、
    // ViewModelさえ再作成される可能性があり、なら、savedStateHandlerを使うのか？となっていくと、逆に複雑になってしまう。
    // 却下。
    // 代わりに、ReceptorDelegate を使って プロパティ名をタグにする作戦で攻めてみよう。

//    private fun generateTag() : String {
//        var uuid:String
//        do {
//            uuid = UUID.randomUUID().toString()
//        } while(receptorMap.containsKey(uuid))
//        return uuid
//    }
//
//    fun addReceptor(r: IUtDialogResultReceptor) : String {
//        return generateTag().also {
//            this[it] = r
//        }
//    }
//
//    fun addReceptor(fn:(IUtDialog)->Unit):String {
//        return addReceptor(ReceptorWrapper(fn))
//    }


    /**
     * タグに対する receptor を削除する。
     */
    fun removeReceptor(tag:String) {
        receptorMap.remove(tag)
    }

    /**
     * 子ホストを追加する。
     */
    fun addChildHost(host: IUtDialogHost) {
        hostList.add(host)
    }

    /**
     * 子ホストを削除する
     */
    fun removeChildHost(host: IUtDialogHost) {
        hostList.remove(host)
    }

    /**
     * すべての receptor, 子ホストを削除する。、
     */
    fun clear() {
        receptorMap.clear()
        hostList.clear()
    }

    // region setReceptor の細かい動作を（少し隠蔽して）、形式化するためのユーティリティ実装

    /**
     * ReceptorImpl からコールバックする情報をカプセル化するための i/f
     */
    interface ISubmission<D> where D: IUtDialog {
        val dialog:D        // completeしたダイアログ
        val clientData:Any?         // IUtDialogのbundleに退避しておいた任意のデータ（任意といっても、Bundleに覚えられる型に限る）
    }

    /**
     * 名前（タグ）付きReceptorの内部実装
     * - register()メソッドで生成される。--> ViewModelクラスなどのメンバーとして作成しておく。
     * - 名前をタグとしてダイアログを開く showDialog()メソッドを公開。
     * @param tag   名前（タグ）
     * @param submit ダイアログがcompleteしたときに呼び出されるコールバックi/f。
     */
    @Suppress("UNCHECKED_CAST")
    inner class NamedReceptor<D>(private val tag:String, val submit:(ISubmission<D>)->Unit) : IUtDialogResultReceptor, ISubmission<D> where D:IUtDialog {
        init {
            setReceptor(this.tag, this)
        }

        private var dialogRef:WeakReference<IUtDialog>? = null
        override val dialog: D
            get() = dialogRef?.get()!! as D
        override var clientData:Any?
            get() = dialogRef?.get()?.asFragment?.arguments?.get("$tag.clientData")
            set(v) { dialogRef?.get()?.ensureArguments()?.put("$tag.clientData",v)}

        /**
         * IUtDialogResultReceptor i/f 実装
         */
        override fun onDialogResult(caller: IUtDialog) {
            dialogRef = WeakReference(caller)
            submit(this)
        }

//        fun showDialog(activity: FragmentActivity, creator:(ReceptorImpl)->IUtDialog) {
//            if(UtDialogHelper.findChildDialog(activity, tag)!=null) return
//            creator(this).apply{
//                attachDialog(this, null)
//                show(activity, tag)
//            }
//        }
//        fun showDialog(fragment: Fragment, creator:(ReceptorImpl)->IUtDialog) {
//            if(UtDialogHelper.findChildDialog(fragment, tag)!=null) return
//            creator(this).apply{
//                attachDialog(this, null)
//                show(fragment, tag)
//            }
//        }

        /**
         * NamedReceptor を使って、ダイアログを表示する。
         * 結果は、NamedReceptorのコンストラクタに渡した submit コールバックによって通知する。
         */
        @JvmOverloads
        fun showDialog(activity: FragmentActivity, clientData:Any?=null, creator:(NamedReceptor<D>)->D) {
            if(UtDialogHelper.findDialog(activity, tag) !=null) return
            creator(this).apply{
                attachDialog(this, clientData)
                show(activity, tag)
            }
        }
        @JvmOverloads
        fun showDialog(fragment: Fragment, clientData:Any?=null, creator:(NamedReceptor<D>)->D)
            = showDialog(fragment.requireActivity(), clientData, creator)

        private fun attachDialog(dlg: IUtDialog, clientData:Any?) {
            dialogRef = WeakReference(dlg)
            if(clientData!=null) {
                this.clientData = clientData
            }
        }

        /**
         * Receptorを破棄 (＝登録解除）
         */
        fun dispose() {
            removeReceptor(tag)
        }
    }

    // 委譲プロパティを使って、プロパティ名をタグにもつReceptorを生成する作戦
    // ... うまくいった！と思ったが、なんと、プロパティにアクセスされるまで、getValue()が呼ばれない。。。当たり前といえば当たり前。
    // getValue()が呼ばれないと、dialogHostManager に登録されないので、ダイアログ再構築後、ダイアログのcompleteから呼ばれるときに、登録がない、ということが起きる。
    // 企画倒れ。

//    inner class ReceptorDelegate(val submit: (ISubmission) -> Unit):ReadOnlyProperty<Any,ReceptorImpl> {
//        override operator fun getValue(thisRef: Any, property: KProperty<*>): ReceptorImpl {
//            return ReceptorImpl(property.name, submit)
//        }
//    }
//
//    fun delegate(submit: (ISubmission) -> Unit):ReadOnlyProperty<Any,ReceptorImpl> {
//        return ReceptorDelegate(submit)
//    }

//    fun createReceptor(onComplete:(IUtDialog, clientData:Any?)->Unit) : ReceptorImpl {
//        return ReceptorImpl(onComplete)
//    }
//    fun createReceptor(onComplete:(IUtDialog)->Unit) : ReceptorImpl {
//        return ReceptorImpl { dlg, _-> onComplete(dlg) }
//    }

    // ブサイクだが、これしかないか。。。


    /**
     * NamedReceptorを作成してテーブルに登録する。
     * 登録解除するときは、NamedReceptor.dispose()を呼ぶ。
     * register が返す NamedReceptorを保持するインスタンス と、UtDialogHostManagerインスタンスの生存期間が同じなら、dispose()は不要。
     * つまり、UtDialogHostManagerインスタンスを持っているオブジェクト（通常はViewModel）が、register()が返したNamedReceptorもメンバーとして持っておけばよい。
     * 逆に、この生存期間が異なる場合は、ライフサイクルの短い側で、register()とdispose()をうまい具合にやる必要があって、たぶん絶望する。
     */
    fun <D> register(tag:String, submit: (ISubmission<D>) -> Unit) : NamedReceptor<D> where D: IUtDialog {
        return NamedReceptor(tag,submit)
    }

    /**
     * すでに作成済みのNamedReceptorを取得する。
     */
    @Suppress("UNCHECKED_CAST")
    fun <D> find(tag:String): NamedReceptor<D>? where D: IUtDialog {
        return receptorMap[tag] as? NamedReceptor<D>
    }
    // endregion
}