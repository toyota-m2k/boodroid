package io.github.toyota32k.dialog

/**
 * アプリ内で共通のダイアログ動作に関する設定をここにまとめます。
 */
object UtDialogConfig {
    /**
     * UtDialogのisDialog引数を省略したときに、isDialogをtrueにするかどうか？
     * true: ダイアログモード (新しいwindowを生成して配置）
     * false: フラグメントモード (ActivityのWindow上に配置）
     */
    var showInDialogModeAsDefault = false

    /**
     * UtDialog.show()の動作指定フラグ
     * true: UtDialog#show()で、FragmentManager#executePendingTransactions()を呼ぶ
     * false: FragmentManagerのスケジューリングに任せる。
     */
    var showDialogImmediately:Boolean = true

    /**
     * Phone の場合、全画面を灰色で塗りつぶす（背景のビューを隠す）
     * サブダイアログに切り替わるときに、一瞬、後ろが透けて見えるのがブサイク、という意見があるので。
     * true にすると、UtDialog.isPhone==true のとき、ダイアログの背景をGuardColor.SOLID_GRAY にする。
     */
    var solidBackgroundOnPhone:Boolean = true

    /**
     * ダイアログのスタイル
     */
    var dialogTheme: Int = R.style.UtDialogTheme

    var fadeInDuration:Long = 300L
    var fadeOutDuraton:Long = 400L
}