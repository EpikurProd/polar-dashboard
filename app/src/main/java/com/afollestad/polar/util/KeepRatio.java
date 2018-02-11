package com.afollestad.polar.util;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.FitCenter;

public class KeepRatio extends FitCenter {

  public KeepRatio() {
    super();
  }

  @Override
  protected Bitmap transform(
      @NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
    if (toTransform.getWidth() > toTransform.getHeight()) {
      outWidth = Math.round(((float) toTransform.getHeight() / outHeight) * toTransform.getWidth());
    } else {
      outHeight = Math.round(((float) toTransform.getWidth() / outWidth) * toTransform.getHeight());
    }

    return super.transform(pool, toTransform, outWidth, outHeight);
  }
}
