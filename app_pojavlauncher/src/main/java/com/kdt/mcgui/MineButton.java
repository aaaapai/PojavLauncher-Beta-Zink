package com.kdt.mcgui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;

public class MineButton extends androidx.appcompat.widget.AppCompatButton {

    public MineButton(Context ctx) {
        this(ctx, null);
    }

    public MineButton(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init();
    }

    public void init() {
        setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.mine_button_background, null));
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._13ssp));
    }

}
