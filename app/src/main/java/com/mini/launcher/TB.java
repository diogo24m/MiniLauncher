package com.mini.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * AlertDialog.Builder que traduce títulos, mensajes, botones y listas al idioma elegido (ver I18n) y usa el tamaño
 * de letra elegido en Ajustes → Apariencia → Texto, solo en sus propias vistas.
 */
public class TB extends AlertDialog.Builder {
    private View customView;
    private View customTitle;

    public TB(Context c, int theme) { super(c, theme); }

    /**
     * Ajusta los TextView del diálogo sin modificar Configuration ni los Resources del contexto de Android.
     * El contenido personalizado ya usa textPx() o field(): se excluye para no aplicar la escala dos veces.
     */
    @Override public AlertDialog create() {
        final AlertDialog dialog = super.create();
        final float scale = Main.TS;
        if (scale == 1f) return dialog;
        final View custom = customView;
        final View title = customTitle;
        // Conserva el tamaño original también al reciclar filas de listas o volver a mostrar el mismo diálogo.
        final Map<TextView, Float> sizes = new WeakHashMap<TextView, Float>();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                final View decor = dialog.getWindow().getDecorView();
                final ViewTreeObserver.OnGlobalLayoutListener layout = new ViewTreeObserver.OnGlobalLayoutListener() {
                    public void onGlobalLayout() { scaleText(decor, custom, title, scale, sizes); }
                };
                decor.getViewTreeObserver().addOnGlobalLayoutListener(layout);
                decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    public void onViewAttachedToWindow(View v) { }
                    public void onViewDetachedFromWindow(View v) {
                        if (v.getViewTreeObserver().isAlive()) v.getViewTreeObserver().removeOnGlobalLayoutListener(layout);
                        v.removeOnAttachStateChangeListener(this);
                    }
                });
                layout.onGlobalLayout();
            }
        });
        return dialog;
    }

    private static void scaleText(View view, View custom, View title, float scale, Map<TextView, Float> sizes) {
        if (view == custom || view == title || view instanceof Main.FitText) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            Float base = sizes.get(text);
            if (base == null) { base = text.getTextSize(); sizes.put(text, base); }
            float target = base * scale;
            if (Math.abs(text.getTextSize() - target) > 0.1f) text.setTextSize(TypedValue.COMPLEX_UNIT_PX, target);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scaleText(group.getChildAt(i), custom, title, scale, sizes);
        }
    }

    @Override public AlertDialog.Builder setView(View view) { customView = view; return super.setView(view); }
    @Override public AlertDialog.Builder setCustomTitle(View view) { customTitle = view; return super.setCustomTitle(view); }

    @Override public AlertDialog.Builder setTitle(CharSequence t) { return super.setTitle(I18n.t(t)); }
    @Override public AlertDialog.Builder setMessage(CharSequence m) { return super.setMessage(I18n.t(m)); }
    @Override public AlertDialog.Builder setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) { return super.setPositiveButton(I18n.t(t), l); }
    @Override public AlertDialog.Builder setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) { return super.setNegativeButton(I18n.t(t), l); }
    @Override public AlertDialog.Builder setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) { return super.setNeutralButton(I18n.t(t), l); }
    @Override public AlertDialog.Builder setItems(CharSequence[] items, DialogInterface.OnClickListener l) {
        return super.setItems(translateItems(items), l);
    }
    @Override public AlertDialog.Builder setSingleChoiceItems(CharSequence[] items, int checked, DialogInterface.OnClickListener l) {
        return super.setSingleChoiceItems(translateItems(items), checked, l);
    }

    private static CharSequence[] translateItems(CharSequence[] items) {
        CharSequence[] out = new CharSequence[items.length];
        for (int i = 0; i < items.length; i++) out[i] = I18n.t(items[i]);
        return out;
    }
}
