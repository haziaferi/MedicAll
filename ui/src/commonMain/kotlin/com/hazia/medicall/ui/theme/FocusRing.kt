package com.hazia.medicall.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo

/** The ring around a control with 10 dp corners, its corners grown by the ring's offset so the two stay concentric. */
val FocusRingShape: Shape = RoundedCornerShape(CORNER + Space.focusRingGap + Space.focusRing / 2)

/**
 * SPEC:R122's focus ring: while the control after this modifier has focus from the keyboard (or from code), a
 * [Space.focusRing] ring in the focus colour drawn [Space.focusRingGap] off it, so it is judged against the card; a
 * mouse click or tap that focuses it shows no ring, as a browser's `:focus-visible`. [ringShape] is the ring's own outline
 * ([FocusRingShape] around 10 dp corners, [PillShape] around a pill). The ring reaches 5 dp beyond the control, so
 * leave at least that much space around it. Put it before `clickable` or `focusable` on a control of MedicAll's own;
 * a Material control takes it through [FocusRingTarget]. Text fields show focus with their own outline and take none.
 */
fun Modifier.focusRing(ringShape: Shape = FocusRingShape): Modifier = this then FocusRingElement(ringShape)

/**
 * A Material [control] with SPEC:R122's focus ring round the control itself. Material pads a control smaller than
 * 48 dp (a 40 dp button, a 32 dp chip) to its target inside the control's own modifier, so [focusRing] passed to it
 * would go round the target. Here this box keeps the 48 dp target ([Space.target], SPEC:R116) and the control is
 * drawn at its own size; a touch beside it still reaches it, as in Material's padding, since Compose widens a small
 * control's touch area to the minimum touch target. Pass the given modifier to the control as its `modifier`.
 */
@Composable
fun FocusRingTarget(modifier: Modifier = Modifier, ringShape: Shape = FocusRingShape, control: @Composable (Modifier) -> Unit) {
    Box(modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            control(Modifier.focusRing(ringShape))
        }
    }
}

private data class FocusRingElement(val ringShape: Shape) : ModifierNodeElement<FocusRingNode>() {
    override fun create() = FocusRingNode(ringShape)

    override fun update(node: FocusRingNode) {
        node.ringShape = ringShape
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "focusRing"
        properties["ringShape"] = ringShape
    }
}

/**
 * Shows the ring for focus that came from the keyboard or from code, as a browser's `:focus-visible` does: a mouse
 * click or a tap that focuses the control shows none, and a key pressed while it has focus brings the ring back.
 */
private class FocusRingNode(var ringShape: Shape) :
    Modifier.Node(), FocusEventModifierNode, DrawModifierNode, CompositionLocalConsumerModifierNode,
    PointerInputModifierNode, KeyInputModifierNode {
    private var focused = false
    private var pressed = false
    private var shown = false

    private fun show(value: Boolean) {
        if (shown != value) {
            shown = value
            invalidateDraw()
        }
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused == focused) return
        focused = focusState.isFocused
        show(focused && !pressed)
        if (!focused) pressed = false
    }

    // The press reaches this node before the control inside it takes focus from it.
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        when {
            pass == PointerEventPass.Initial && pointerEvent.type == PointerEventType.Press -> {
                pressed = true
                show(false)
            }
            // Once the press is over, focus that comes later (a Tab into the control) shows the ring again.
            pass == PointerEventPass.Final && pointerEvent.type == PointerEventType.Release -> pressed = false
        }
    }

    override fun onCancelPointerInput() = Unit

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        if (focused) show(true)
        return false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!shown) return
        val width = Space.focusRing.toPx()
        val inset = Space.focusRingGap.toPx() + width / 2
        val outline = ringShape.createOutline(Size(size.width + 2 * inset, size.height + 2 * inset), layoutDirection, this)
        translate(-inset, -inset) { drawOutline(outline, currentValueOf(LocalMedicAllColours).focus, style = Stroke(width)) }
    }
}
