package com.rutinitas.islamic;

import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.view.View;

/** Draws the scroll area behind the floating bar, without blurring the controls on top. */
@TargetApi(31)
final class NativeGlassBackdrop extends View {
    private final View source;
    private final RenderNode blurred = new RenderNode("RutinitasNavBlur");
    private final int[] sourcePosition = new int[2];
    private final int[] glassPosition = new int[2];
    private final int bleed;

    NativeGlassBackdrop(View source, float radius) {
        super(source.getContext());
        this.source = source;
        bleed = (int) Math.ceil(radius * 2);
        blurred.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (!canvas.isHardwareAccelerated() || !source.isLaidOut() || getWidth() == 0 || getHeight() == 0) return;
        source.getLocationInWindow(sourcePosition);
        getLocationInWindow(glassPosition);
        blurred.setPosition(-bleed, -bleed, getWidth() + bleed, getHeight() + bleed);
        Canvas snapshot = blurred.beginRecording();
        try {
            snapshot.translate(sourcePosition[0] - glassPosition[0] + bleed - source.getScrollX(),
                    sourcePosition[1] - glassPosition[1] + bleed - source.getScrollY());
            source.draw(snapshot);
        } finally {
            blurred.endRecording();
        }
        canvas.drawRenderNode(blurred);
    }
}
