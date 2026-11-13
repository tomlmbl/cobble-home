package io.github.tomlmbl.launcher.home

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.UserHandle
import android.util.Log
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.tomlmbl.launcher.ui.Tokens

/** What the return surface draws: a favorite's dot or a dock app's icon. */
sealed interface ReturnLook {
    class Dot(val color: Int) : ReturnLook
    class Icon(val image: ImageBitmap) : ReturnLook
}

/** Home element an app can return to: frame in window coordinates and appearance. */
class ReturnTarget(val bounds: Rect, val look: ReturnLook)

/** Visible Home elements by app identity, and the one hidden during a return animation. */
class ReturnTargets {
    /** Target and the node that set it: the same app may appear in two docks during a switch. */
    private val targets = HashMap<String, Pair<Any, ReturnTarget>>()

    /** App whose original element is hidden: the return surface replaces it. */
    var hidden by mutableStateOf<String?>(null)

    /** Called whenever an element is placed; serves a reply that waits while Home is not redrawn yet. */
    var onPut: ((String) -> Unit)? = null

    operator fun get(appId: String): ReturnTarget? = targets[appId]?.second

    fun put(appId: String, target: ReturnTarget, owner: Any) {
        targets[appId] = owner to target
        onPut?.invoke(appId)
    }

    /** Removes the target only if [owner] set it: a leaving node does not clear an arriving node's target. */
    fun remove(appId: String, owner: Any) {
        if (targets[appId]?.first === owner) targets.remove(appId)
    }
}

val LocalReturnTargets = staticCompositionLocalOf<ReturnTargets?> { null }

/** Registers the element as a gesture return target and hides it during the animation. */
fun Modifier.returnTarget(appId: String, look: ReturnLook?): Modifier = composed {
    val targets = LocalReturnTargets.current
    if (targets == null || look == null) return@composed this
    val owner = remember { Any() }
    DisposableEffect(targets, appId) { onDispose { targets.remove(appId, owner) } }
    onGloballyPositioned { c ->
        targets.put(appId, ReturnTarget(c.windowRect(), look), owner)
    }.graphicsLayer { alpha = if (targets.hidden == appId) 0f else 1f }
}

/**
 * Gesture return to Home: Launcher3's `GestureNavContract`, not a public API.
 * The system launcher attaches the app being left and a callback to the Home intent; we send back the frame of its
 * element and a surface that draws it. The system converges the window onto that frame while moving the surface,
 * then signals the end: the surface is removed and the original element reappears. A Home left from the catalog is
 * only redrawn on return: the reply then waits until the element is placed, which the contract allows. Without a
 * target within the delay, no reply: the system's default animation.
 * Never logs an app name.
 */
class GestureReturn(private val activity: Activity, private val targets: ReturnTargets) {

    private val main = Handler(Looper.getMainLooper())
    private var surface: SurfaceView? = null
    // Contract received before the app's element is placed: identity and callback.
    private var pending: Pair<String, Message>? = null
    private val finish = Messenger(Handler(Looper.getMainLooper()) { clear(); true })
    // Safety net if the end signal never comes (gesture cancelled, system launcher restarted).
    private val timeout = Runnable { clear() }
    /** Display posted by [placed], cancelled by [clear]: nothing appears after a stop. */
    private var showing: Runnable? = null

    /** Replies to the contract if attached to the intent; [idOf] gives the app identity of a component and a profile. */
    fun answer(intent: Intent, idOf: (ComponentName, UserHandle) -> String) {
        val extras = intent.getBundleExtra(EXTRA_CONTRACT) ?: return
        intent.removeExtra(EXTRA_CONTRACT)
        val component = extras.getParcelable(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        val user = extras.getParcelable(Intent.EXTRA_USER, UserHandle::class.java)
        val callback = extras.getParcelable(EXTRA_REMOTE_CALLBACK, Message::class.java)
        if (component == null || user == null || callback?.replyTo == null) return
        val appId = idOf(component, user)
        clear(immediate = true)
        val target = targets[appId]
        if (target != null) {
            show(appId, target, callback)
        } else {
            pending = appId to callback
            targets.onPut = { id -> if (id == appId) placed(id) }
            main.postDelayed(timeout, Tokens.gestureReturnTimeoutMs)
        }
    }

    private fun placed(appId: String) {
        val (id, callback) = pending ?: return
        val target = targets[id] ?: return
        pending = null
        targets.onPut = null
        main.removeCallbacks(timeout)
        // Called during Home's layout: the view is added afterwards, to be laid out in the next pass.
        showing = Runnable { showing = null; show(appId, target, callback) }.also { main.post(it) }
    }

    private fun show(appId: String, target: ReturnTarget, callback: Message) {
        onTarget?.invoke(target.bounds)
        val view = SurfaceView(activity)
        view.setZOrderOnTop(true)
        view.holder.setFormat(PixelFormat.TRANSLUCENT)
        view.holder.addCallback(object : SurfaceHolder.Callback {
            private var sent = false
            override fun surfaceCreated(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                draw(holder, target.look, width, height)
                if (sent) return
                sent = true
                if (send(target, view, callback)) targets.hidden = appId else clear()
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
        activity.addContentView(view, FrameLayout.LayoutParams(target.bounds.width(), target.bounds.height()).apply {
            leftMargin = target.bounds.left
            topMargin = target.bounds.top
        })
        surface = view
        main.postDelayed(timeout, Tokens.gestureReturnTimeoutMs)
    }

    private fun draw(holder: SurfaceHolder, look: ReturnLook, width: Int, height: Int) {
        val canvas = holder.lockCanvas() ?: return
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        when (look) {
            is ReturnLook.Dot -> canvas.drawOval(
                0f, 0f, width.toFloat(), height.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.color },
            )
            is ReturnLook.Icon -> canvas.drawBitmap(look.image.asAndroidBitmap(), null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        holder.unlockCanvasAndPost(canvas)
    }

    private fun send(target: ReturnTarget, view: SurfaceView, callback: Message): Boolean {
        val result = Bundle()
        result.putParcelable(EXTRA_ICON_POSITION, RectF(target.bounds))
        result.putParcelable(EXTRA_ICON_SURFACE, view.surfaceControl)
        result.putParcelable(EXTRA_ON_FINISH_CALLBACK, Message.obtain().apply { replyTo = finish })
        val reply = Message.obtain().apply {
            copyFrom(callback)
            data = result
        }
        return try {
            callback.replyTo.send(reply)
            true
        } catch (e: RemoteException) {
            Log.w(TAG, "gesture contract reply failed: ${e.javaClass.simpleName}")
            false
        }
    }

    /** Called with the target element's frame, in window coordinates, when the return is sent to it. */
    var onTarget: ((Rect) -> Unit)? = null

    /** Removes the surface and restores the original element; [immediate] when not displayed (stop, new contract). */
    fun clear(immediate: Boolean = false) {
        main.removeCallbacks(timeout)
        showing?.let { main.removeCallbacks(it) }
        showing = null
        pending = null
        targets.onPut = null
        // The surface disappears in the same frame the original element reappears: a gap misses a frame, an overlap
        // doubles the dock's translucent disc (flicker either way). The element's opacity is read at draw time, so it
        // is redrawn in the next frame; the transaction is applied with it.
        surface?.let { view ->
            val root = activity.window.rootSurfaceControl
            val control = view.surfaceControl
            if (immediate || root == null || control == null || !control.isValid) {
                (view.parent as? ViewGroup)?.removeView(view)
                return@let
            }
            root.applyTransactionOnDraw(SurfaceControl.Transaction().setVisibility(control, false))
            val decor = activity.window.decorView
            decor.viewTreeObserver.registerFrameCommitCallback { (view.parent as? ViewGroup)?.removeView(view) }
            decor.invalidate()
        }
        surface = null
        targets.hidden = null
    }

    companion object {
        /** True if the Home intent comes from a gesture return from an app (contract attached). */
        fun isGesture(intent: Intent): Boolean = intent.hasExtra(EXTRA_CONTRACT)

        private const val TAG = "GestureReturn"
        // GestureNavContract keys (Launcher3, android-16.0.0_r1).
        private const val EXTRA_CONTRACT = "gesture_nav_contract_v1"
        private const val EXTRA_ICON_POSITION = "gesture_nav_contract_icon_position"
        private const val EXTRA_ICON_SURFACE = "gesture_nav_contract_surface_control"
        private const val EXTRA_REMOTE_CALLBACK = "android.intent.extra.REMOTE_CALLBACK"
        private const val EXTRA_ON_FINISH_CALLBACK = "gesture_nav_contract_finish_callback"
    }
}

/** Element frame in window coordinates, in whole pixels: an app's launch origin and its return target. */
fun LayoutCoordinates.windowRect(): Rect = boundsInWindow().let { Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
