////////////////////////////////////////////////////////////////////////////////
// ByakuGallery is an open source Android library that allows the visualization
//     of large images with gesture capabilities.
//     This lib is based on AOSP Camera2.
//     Copyright 2013 Diego Carlos Lima
//
//     Licensed under the Apache License, Version 2.0 (the "License");
//     you may not use this file except in compliance with the License.
//     You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//     Unless required by applicable law or agreed to in writing, software
//     distributed under the License is distributed on an "AS IS" BASIS,
//     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//     See the License for the specific language governing permissions and
//     limitations under the License.
////////////////////////////////////////////////////////////////////////////////

package com.diegocarloslima.byakugallery;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;
import android.widget.ImageView;

public class TouchImageView extends ImageView {
    private static final String TAG = "TouchImageView";

    private static int ANIMATION_DURATION = 0;

    private Drawable mDrawable;
    private int mDrawableIntrinsicWidth;
    private int mDrawableIntrinsicHeight;

    private final TouchGestureDetector mTouchGestureDetector;

    private final Matrix mMatrix = new Matrix();
    private final float[] mMatrixValues = new float[9];

    private float mScale;
    private float mTranslationX;
    private float mTranslationY;

    private Float mLastFocusX;
    private Float mLastFocusY;

    private final FlingScroller mFlingScroller = new FlingScroller();
    private boolean mIsAnimatingBack;

    public TouchImageView(Context context) {
        this(context, null);
    }

    public TouchImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TouchImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (ANIMATION_DURATION == 0)
            ANIMATION_DURATION = context.getResources().getInteger(android.R.integer.config_shortAnimTime);

        final TouchGestureDetector.OnTouchGestureListener listener = new TouchGestureDetector.OnTouchGestureListener() {

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return performClick();
            }

            @Override
            public void onLongPress(MotionEvent e) {
                performLongClick();
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                loadMatrixValues();

                // 3 stage scaling
                float targetScale = mCropScale;
                if (mScale == mMaxScale)
                    targetScale = mMinScale;
                else if (mScale >= mCropScale)
                    targetScale = mMaxScale;

                // First, we try to keep the focused point in the same position when the animation ends
                final float desiredTranslationX = e.getX() - (e.getX() - mTranslationX) * (targetScale / mScale);
                final float desiredTranslationY = e.getY() - (e.getY() - mTranslationY) * (targetScale / mScale);

                // Here, we apply a correction to avoid unwanted blank spaces
                final float targetTranslationX = desiredTranslationX + computeTranslation(getMeasuredWidth(), mDrawableIntrinsicWidth * targetScale, desiredTranslationX, 0);
                final float targetTranslationY = desiredTranslationY + computeTranslation(getMeasuredHeight(), mDrawableIntrinsicHeight * targetScale, desiredTranslationY, 0);

                clearAnimation();
                final Animation animation = new TouchAnimation(targetScale, targetTranslationX, targetTranslationY);
                animation.setDuration(ANIMATION_DURATION);
                startAnimation(animation);

                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // Sometimes, this method is called just after an onScaleEnd event. In this case, we want to wait until we animate back our image
                if(mIsAnimatingBack) {
                    return false;
                }

                loadMatrixValues();

                final float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
                final float currentDrawableHeight = mDrawableIntrinsicHeight * mScale;

                final float dx = computeTranslation(getMeasuredWidth(), currentDrawableWidth, mTranslationX, -distanceX);
                final float dy = computeTranslation(getMeasuredHeight(), currentDrawableHeight, mTranslationY, -distanceY);
                mMatrix.postTranslate(dx, dy);

                clearAnimation();
                ViewCompat.postInvalidateOnAnimation(TouchImageView.this);

                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // Sometimes, this method is called just after an onScaleEnd event. In this case, we want to wait until we animate back our image
                if(mIsAnimatingBack) {
                    return false;
                }

                loadMatrixValues();

                final float horizontalSideFreeSpace = (getMeasuredWidth() - mDrawableIntrinsicWidth * mScale) / 2F;
                final float minTranslationX = horizontalSideFreeSpace > 0 ? horizontalSideFreeSpace : getMeasuredWidth() - mDrawableIntrinsicWidth * mScale;
                final float maxTranslationX = horizontalSideFreeSpace > 0 ? horizontalSideFreeSpace : 0;

                final float verticalSideFreeSpace = (getMeasuredHeight() - mDrawableIntrinsicHeight * mScale) / 2F;
                final float minTranslationY = verticalSideFreeSpace > 0 ? verticalSideFreeSpace : getMeasuredHeight() - mDrawableIntrinsicHeight * mScale;
                final float maxTranslationY = verticalSideFreeSpace > 0 ? verticalSideFreeSpace : 0;

                // Using FlingScroller here. The results were better than the Scroller class
                // https://android.googlesource.com/platform/packages/apps/Gallery2/+/master/src/com/android/gallery3d/ui/FlingScroller.java
                mFlingScroller.fling(Math.round(mTranslationX), Math.round(mTranslationY), Math.round(velocityX), Math.round(velocityY), Math.round(minTranslationX), Math.round(maxTranslationX), Math.round(minTranslationY), Math.round(maxTranslationY));

                clearAnimation();
                final Animation animation = new FlingAnimation();
                animation.setDuration(mFlingScroller.getDuration());
                animation.setInterpolator(new LinearInterpolator());
                startAnimation(animation);

                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                mLastFocusX = null;
                mLastFocusY = null;

                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                loadMatrixValues();

                float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
                float currentDrawableHeight = mDrawableIntrinsicHeight * mScale;

                final float focusX = computeFocus(getMeasuredWidth(), currentDrawableWidth, mTranslationX, detector.getFocusX());
                final float focusY = computeFocus(getMeasuredHeight(), currentDrawableHeight, mTranslationY, detector.getFocusY());

                // Here, we provide the ability to scroll while scaling
                if(mLastFocusX != null && mLastFocusY != null) {
                    final float dx = computeScaleTranslation(getMeasuredWidth(), currentDrawableWidth, mTranslationX, focusX - mLastFocusX);
                    final float dy = computeScaleTranslation(getMeasuredHeight(), currentDrawableHeight, mTranslationY, focusY - mLastFocusY);

                    if(dx != 0 || dy != 0) {
                        mMatrix.postTranslate(dx, dy);
                    }
                }

                final float scale = computeScale(mMinScale, mMaxScale, mScale, detector.getScaleFactor());
                mMatrix.postScale(scale, scale, focusX, focusY);

                mLastFocusX = focusX;
                mLastFocusY = focusY;

                clearAnimation();
                ViewCompat.postInvalidateOnAnimation(TouchImageView.this);

                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                loadMatrixValues();

                final float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
                final float currentDrawableHeight = mDrawableIntrinsicHeight * mScale;

                final float dx = computeTranslation(getMeasuredWidth(), currentDrawableWidth, mTranslationX, 0);
                final float dy = computeTranslation(getMeasuredHeight(), currentDrawableHeight, mTranslationY, 0);

                if(Math.abs(dx) < 1 && Math.abs(dy) < 1) {
                    return;
                }

                final float targetTranslationX = mTranslationX + dx;
                final float targetTranslationY = mTranslationY + dy;

                float targetScale = MathUtils.clamp(mScale, mMinScale, mMaxScale);

                clearAnimation();
                final Animation animation = new TouchAnimation(targetScale, targetTranslationX, targetTranslationY);
                animation.setDuration(ANIMATION_DURATION);
                startAnimation(animation);

                mIsAnimatingBack = true;
            }
        };

        mTouchGestureDetector = new TouchGestureDetector(context, listener);

        super.setScaleType(ScaleType.MATRIX);
    }

    private float mMinScale;
    private float mCropScale;
    private float mMaxScale;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int oldMeasuredWidth = getMeasuredWidth();
        final int oldMeasuredHeight = getMeasuredHeight();

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if(oldMeasuredWidth != getMeasuredWidth() || oldMeasuredHeight != getMeasuredHeight()) {
            resetToInitialState();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.setImageMatrix(mMatrix);
        super.onDraw(canvas);
    }

    @Override
    public void setImageMatrix(Matrix matrix) {
        if(matrix == null) {
            matrix = new Matrix();
        }

        if(!mMatrix.equals(matrix)) {
            mMatrix.set(matrix);
            invalidate();
        }
    }

    @Override
    public Matrix getImageMatrix() {
        return mMatrix;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mTouchGestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public void clearAnimation() {
        super.clearAnimation();
        mIsAnimatingBack = false;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        if(mDrawable != drawable) {
            mDrawable = drawable;
            if(drawable != null) {
                mDrawableIntrinsicWidth = drawable.getIntrinsicWidth();
                mDrawableIntrinsicHeight = drawable.getIntrinsicHeight();
                resetToInitialState();
            } else {
                mDrawableIntrinsicWidth = 0;
                mDrawableIntrinsicHeight = 0;
            }
        }
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        loadMatrixValues();

        if(direction > 0) {
            return Math.round(mTranslationX) < 0;
        } else if(direction < 0) {
            final float currentDrawableWidth = mDrawableIntrinsicWidth * mScale;
            return Math.round(mTranslationX) > getMeasuredWidth() - Math.round(currentDrawableWidth);
        }
        return false;
    }

    private void resetToInitialState() {
        mMinScale = getMeasuredWidth() / (float) mDrawableIntrinsicWidth;
        mCropScale = getMeasuredHeight() / (float) mDrawableIntrinsicHeight;
        if (mMinScale > mCropScale)
        {
            float temp = mCropScale;
            mCropScale = mMinScale;
            mMinScale = temp;
        }
        mMaxScale = mCropScale * 4.0f;

        mMatrix.reset();
        mMatrix.postScale(mMinScale, mMinScale);

        final float[] values = new float[9];
        mMatrix.getValues(values);

        final float freeSpaceHorizontal = (getMeasuredWidth() - (mDrawableIntrinsicWidth * mMinScale)) / 2F;
        final float freeSpaceVertical = (getMeasuredHeight() - (mDrawableIntrinsicHeight * mMinScale)) / 2F;
        mMatrix.postTranslate(freeSpaceHorizontal, freeSpaceVertical);

        invalidate();
    }

    private void loadMatrixValues() {
        mMatrix.getValues(mMatrixValues);
        mScale = mMatrixValues[Matrix.MSCALE_X];
        mTranslationX = mMatrixValues[Matrix.MTRANS_X];
        mTranslationY = mMatrixValues[Matrix.MTRANS_Y];
    }

    // The translation values must be in [0, viewSize - drawableSize], except if we have free space. In that case we will translate to half of the free space
    private static float computeTranslation(float viewSize, float drawableSize, float currentTranslation, float delta) {
        final float sideFreeSpace = (viewSize - drawableSize) / 2F;

        if(sideFreeSpace > 0) {
            return sideFreeSpace - currentTranslation;
        } else if(currentTranslation + delta > 0) {
            return -currentTranslation;
        } else if(currentTranslation + delta < viewSize - drawableSize) {
            return viewSize - drawableSize - currentTranslation;
        }

        return delta;
    }

    private static float computeScaleTranslation(float viewSize, float drawableSize, float currentTranslation, float delta) {
        final float minTranslation = viewSize > drawableSize ? 0 : viewSize - drawableSize;
        final float maxTranslation = viewSize > drawableSize ? viewSize - drawableSize : 0;

        if(currentTranslation < minTranslation && delta > 0) {
            if(currentTranslation + delta > maxTranslation) {
                return maxTranslation - currentTranslation;
            } else {
                return delta;
            }
        } else if(currentTranslation > maxTranslation && delta < 0) {
            if(currentTranslation + delta < minTranslation) {
                return minTranslation - currentTranslation;
            }
            else {
                return delta;
            }
        } else if(currentTranslation > minTranslation && currentTranslation < maxTranslation) {
            if(currentTranslation + delta < minTranslation) {
                return minTranslation - currentTranslation;
            } else if(currentTranslation + delta > maxTranslation) {
                return maxTranslation - currentTranslation;
            } else {
                return delta;
            }
        }
        return 0;
    }

    // If our focal point is outside the image, we will project it to our image bounds
    private static float computeFocus(float viewSize, float drawableSize, float currentTranslation, float focusCoordinate) {
        if(currentTranslation > 0 && focusCoordinate < currentTranslation) {
            return currentTranslation;
        } else if(currentTranslation < viewSize - drawableSize && focusCoordinate > currentTranslation + drawableSize) {
            return drawableSize + currentTranslation;
        }

        return focusCoordinate;
    }

    // The scale values must be in [minScale, 1]
    private static float computeScale(float minScale, float maxScale, float currentScale, float delta) {
        if(currentScale * delta < minScale) {
            return minScale / currentScale;
        } else if(currentScale * delta > maxScale) {
            return maxScale / currentScale;
        }

        return delta;
    }

    private class FlingAnimation extends Animation {

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            mFlingScroller.computeScrollOffset(interpolatedTime);

            loadMatrixValues();

            final float dx = mFlingScroller.getCurrX() - mTranslationX;
            final float dy = mFlingScroller.getCurrY() - mTranslationY;
            mMatrix.postTranslate(dx, dy);

            ViewCompat.postInvalidateOnAnimation(TouchImageView.this);
        }
    }

    private class TouchAnimation extends Animation {

        private float initialScale;
        private float initialTranslationX;
        private float initialTranslationY;

        private float targetScale;
        private float targetTranslationX;
        private float targetTranslationY;

        TouchAnimation(float targetScale, float targetTranslationX, float targetTranslationY) {
            loadMatrixValues();

            this.initialScale =  mScale;
            this.initialTranslationX = mTranslationX;
            this.initialTranslationY = mTranslationY;

            this.targetScale = targetScale;
            this.targetTranslationX = targetTranslationX;
            this.targetTranslationY = targetTranslationY;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            loadMatrixValues();

            if(interpolatedTime >= 1) {
                mMatrix.getValues(mMatrixValues);
                mMatrixValues[Matrix.MSCALE_X] = this.targetScale;
                mMatrixValues[Matrix.MSCALE_Y] = this.targetScale;
                mMatrixValues[Matrix.MTRANS_X] = this.targetTranslationX;
                mMatrixValues[Matrix.MTRANS_Y] = this.targetTranslationY;
                mMatrix.setValues(mMatrixValues);

            } else {
                final float scaleFactor = (this.initialScale + interpolatedTime * (this.targetScale - this.initialScale)) / mScale;
                mMatrix.postScale(scaleFactor, scaleFactor);

                mMatrix.getValues(mMatrixValues);
                final float currentTranslationX = mMatrixValues[Matrix.MTRANS_X];
                final float currentTranslationY = mMatrixValues[Matrix.MTRANS_Y];

                final float dx = this.initialTranslationX + interpolatedTime * (this.targetTranslationX - this.initialTranslationX) - currentTranslationX;
                final float dy = this.initialTranslationY + interpolatedTime * (this.targetTranslationY - this.initialTranslationY) - currentTranslationY;
                mMatrix.postTranslate(dx, dy);
            }

            ViewCompat.postInvalidateOnAnimation(TouchImageView.this);
        }
    }
}