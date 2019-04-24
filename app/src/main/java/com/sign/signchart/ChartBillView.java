package com.sign.signchart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by CaoYongSheng
 * on 2019-04-22
 *
 * @author admin
 */
public class ChartBillView extends View {
    private final static float BEZIER_RADIAN = 0.16f;//贝塞尔连接线弧度 值越小越尖锐
    private final static float BLACK_X_LABEL_LINE = 20;//px轴线两端的空白
    private final static float BLACK_VALUE_TEXT = 12;//px坐标点和文字之间的间距
    private final static int INVALID_ID = -1;//非法触控id
    private int mActivePointerId = INVALID_ID;//记录首个触控点的id 避免多点触控引起的滚动
    private ChartBillLayout mParent;
    private Context mContext;
    //提前刻画量
    private float mDrawOffset;
    private Paint mXLabelLinePaint, mXLabelTextNormalPaint, mXLabelTextSelectPaint, mXValuePointColorPaint, mXValuePointWhitePaint, mNormalValueTextPaint, mSelectValueTextPaint, mLinkLinePaint;
    //x轴轴线的路径
    private Path mXLabelLinePath;
    //x轴label未选中文字属性
    private Paint.FontMetrics mXLabelTextNormalMetrics;
    //x轴label选中文字属性
    private Paint.FontMetrics mXLabelTextSelectMetrics;
    //x轴label正常文字的高度
    private float mXLabelTextNormalHeight;
    //value选中文字的高度
    private float mValueSelectHeight;
    //选中坐标文字属性
    private Paint.FontMetrics mSelectValueMetrics;
    //未选中坐标文字属性
    private Paint.FontMetrics mNormalValueMetrics;
    //值的坐标
    private List<PointF> mPointList;
    //选中值的路径
    private Path mPointPath;
    //上个touch事件的x坐标
    private float mLastX = 0;
    //一半宽度
    private int mHalfWidth = 0;
    //图标的总长度、最小可滑动值、最大可滑动值
    private int mLength, mMinPosition = 0, mMaxPosition = 0;
    //速度获取
    protected VelocityTracker mVelocityTracker;
    //惯性最大最小速度
    protected int mMaximumVelocity, mMinimumVelocity;
    //控制滑动
    protected OverScroller mOverScroller;


    public ChartBillView(Context context, ChartBillLayout chartBillLayout) {
        super(context);
        mParent = chartBillLayout;
        init(context);
    }

    public void init(Context context) {
        mContext = context;
        mXLabelLinePath = new Path();
        mPointPath = new Path();
        mPointList = new ArrayList<>();
        mDrawOffset = Utils.dp2px(context, mParent.getXLabelInterval());
        mOverScroller = new OverScroller(mContext);
        mVelocityTracker = VelocityTracker.obtain();
        mMaximumVelocity = ViewConfiguration.get(context)
                .getScaledMaximumFlingVelocity();
        mMinimumVelocity = ViewConfiguration.get(context)
                .getScaledMinimumFlingVelocity();
        initPaint();
        checkAPILevel();
    }

    //处理滑动 计算现在的event坐标和上一个触摸事件的坐标来计算偏移量 决定scrollBy的多少
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float currentX = event.getX();
        //开始速度检测
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        ViewGroup parent = (ViewGroup) getParent();//为了解决刻度尺在scrollview这种布局里面滑动冲突问题
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //记录首个触控点的id
                mActivePointerId = event.findPointerIndex(event.getActionIndex());
                if (!mOverScroller.isFinished()) {
                    mOverScroller.abortAnimation();
                }
                mLastX = currentX;
                parent.requestDisallowInterceptTouchEvent(true);//按下时开始让父控件不要处理任何touch事件
                break;
            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_ID || event.findPointerIndex(mActivePointerId) == INVALID_ID) {
                    break;
                }
                //计算首个触控点移动后的坐标
                float moveX = mLastX - event.getX(mActivePointerId);
                mLastX = currentX;
                scrollBy((int) moveX, 0);
                break;
            case MotionEvent.ACTION_UP:
                mActivePointerId = INVALID_ID;
                mLastX = 0;
                //处理松手后的Fling
                mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                int velocityX = (int) mVelocityTracker.getXVelocity();
                if (Math.abs(velocityX) > mMinimumVelocity) {
                    fling(-velocityX);
                } else {
                    //scrollBackToCurrentScale();
                }
                //VelocityTracker回收
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                parent.requestDisallowInterceptTouchEvent(false);//up或者cancel的时候恢复
                break;
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = INVALID_ID;
                mLastX = 0;
                if (!mOverScroller.isFinished()) {
                    mOverScroller.abortAnimation();
                }
                //回滚到整点刻度
                //scrollBackToCurrentScale();
                //VelocityTracker回收
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                parent.requestDisallowInterceptTouchEvent(false);//up或者cancel的时候恢复
                break;
        }
        return true;
    }

    private void fling(int vX) {
        mOverScroller.fling(getScrollX(), 0, vX, 0, mMinPosition, mMaxPosition, 0, 0);
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (mOverScroller.computeScrollOffset()) {
            scrollTo(mOverScroller.getCurrX(), mOverScroller.getCurrY());
            //这是最后OverScroller的最后一次滑动，如果这次滑动完了mCurrentScale不是整数，则把尺子移动到最近的整数位置
            if (!mOverScroller.computeScrollOffset()) {
//                int currentIntScale = Math.round(mCurrentScale);
//                if ((Math.abs(mCurrentScale - currentIntScale) > 0.001f)) {
//                    //Fling完进行一次检测回滚
//                    scrollBackToCurrentScale(currentIntScale);
//                }
            }
            postInvalidate();
        }
    }

    @Override
    public void scrollTo(int x, int y) {
        //默认左边缘为x最小值-半个控件的宽度
        if (x < mMinPosition) {
            x = mMinPosition;
        }
        //默认右边缘为x最大值+半个控件的宽度
        if (x > mMaxPosition) {
            x = mMaxPosition;
        }
        if (x != getScrollX()) {
            super.scrollTo(x, y);
        }
    }

    public void refreshSize() {
        mLength = (int) ((mParent.getData().size() - 1) * mParent.getXLabelInterval());
        mHalfWidth = getWidth() / 2;
        //左右空白间距为控件宽度一半
        mMinPosition = -mHalfWidth;
        mMaxPosition = mLength - mHalfWidth;
    }

    //获取控件宽高，设置相应信息
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        refreshSize();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //起始、终止绘制值的下标
        int startIndex = (int) ((getScrollX() - mDrawOffset) / mParent.getXLabelInterval());
        int endIndex = (int) ((getScrollX() + getWidth() + mDrawOffset) / mParent.getXLabelInterval());
        int height = getHeight();
        //先画path路径
        mPointList.clear();
        for (int i = startIndex; i <= endIndex; i++) {
            if (i >= 0 && i < mParent.getData().size()) {
                float locationX = i * mParent.getXLabelInterval();
                if (mParent.getXLabelGravity() == ChartBillLayout.X_TOP) {
                    //上方及下方空白间距+label文字高度+label文字和轴线的间距+选中值文字高度+坐标点和文字间距(*2 多了一份是坐标点的半径)
                    float distance = (float) ((height - BLACK_X_LABEL_LINE - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval() - mValueSelectHeight - BLACK_VALUE_TEXT * 2) /
                            (mParent.getYMaxValue() - mParent.getYMinValue()) * (mParent.getData().get(i).getMoney() - mParent.getYMinValue()));
                    //TODO onDraw方法中不能循环创建对象
                    mPointList.add(new PointF(locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - BLACK_VALUE_TEXT)));
                } else {
                    float distance = (float) ((height - BLACK_X_LABEL_LINE - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval() - mValueSelectHeight - BLACK_VALUE_TEXT * 2) /
                            (mParent.getYMaxValue() - mParent.getYMinValue()) * (mParent.getData().get(i).getMoney() - mParent.getYMinValue()));
                    mPointList.add(new PointF(locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - BLACK_VALUE_TEXT - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval())));
                }
            }
        }
        drawValuePath(canvas);
        for (int i = startIndex; i <= endIndex; i++) {
            if (i >= 0 && i < mParent.getData().size()) {
                float locationX = i * mParent.getXLabelInterval();
                if (mParent.getXLabelGravity() == ChartBillLayout.X_TOP) {
                    int centerIndex = (int) (getScrollX() / mParent.getXLabelInterval() + Utils.getScreenWidth(mContext) / mParent.getXLabelInterval() / 2);
                    //x轴label文字在上面
                    if (mParent.getData().get(i).getMonth().length() > 0) {
                        //是否是中心线
                        if (centerIndex == i) {
                            canvas.drawText(mParent.getData().get(i).getMonth(), locationX, -mXLabelTextSelectMetrics.top, mXLabelTextSelectPaint);
                        } else {
                            canvas.drawText(mParent.getData().get(i).getMonth(), locationX, -mXLabelTextNormalMetrics.top, mXLabelTextNormalPaint);
                        }
                    }
                    //x轴轴线
                    mXLabelLinePath.reset();
                    mXLabelLinePath.moveTo(locationX, mXLabelTextNormalHeight + mParent.getXLabelTextLineInterval());
                    mXLabelLinePath.lineTo(locationX, height);
                    canvas.drawPath(mXLabelLinePath, mXLabelLinePaint);
                    float distance = (float) ((height - BLACK_X_LABEL_LINE - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval() - mValueSelectHeight - BLACK_VALUE_TEXT * 2) /
                            (mParent.getYMaxValue() - mParent.getYMinValue()) * (mParent.getData().get(i).getMoney() - mParent.getYMinValue()));
                    //是否是中心值
                    if (centerIndex == i) {
                        mXValuePointColorPaint.setStrokeWidth(8);
                        canvas.drawCircle(locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - BLACK_VALUE_TEXT), 4, mXValuePointColorPaint);
                        mXValuePointWhitePaint.setStrokeWidth(4);
                        canvas.drawCircle(locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - BLACK_VALUE_TEXT), 8, mXValuePointWhitePaint);
                        mXValuePointColorPaint.setStrokeWidth(3);
                        canvas.drawCircle(locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - BLACK_VALUE_TEXT), 12, mXValuePointColorPaint);
                        canvas.drawText("R$" + mParent.getData().get(i).getMoney(), locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - mSelectValueMetrics.bottom - BLACK_VALUE_TEXT * 2), mSelectValueTextPaint);
                    } else {
                        mXValuePointWhitePaint.setStrokeWidth(8);
                        canvas.drawCircle(locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - BLACK_VALUE_TEXT), 4, mXValuePointWhitePaint);
                        mXValuePointColorPaint.setStrokeWidth(3);
                        canvas.drawCircle(locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - BLACK_VALUE_TEXT), 8, mXValuePointColorPaint);
                        canvas.drawText("R$" + mParent.getData().get(i).getMoney(), locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - mNormalValueMetrics.bottom - BLACK_VALUE_TEXT * 2), mNormalValueTextPaint);
                    }
                } else {
                    int centerIndex = (int) (getScrollX() / mParent.getXLabelInterval() + Utils.getScreenWidth(mContext) / mParent.getXLabelInterval() / 2);
                    //x轴label文字在下面
                    if (mParent.getData().get(i).getMonth().length() > 0) {
                        //是否是中心线
                        if (centerIndex == i) {
                            canvas.drawText(mParent.getData().get(i).getMonth(), locationX, getHeight() - mXLabelTextSelectMetrics.bottom, mXLabelTextSelectPaint);
                        } else {
                            canvas.drawText(mParent.getData().get(i).getMonth(), locationX, getHeight() - mXLabelTextNormalMetrics.bottom, mXLabelTextNormalPaint);
                        }
                    }
                    //x轴轴线
                    mXLabelLinePath.reset();
                    mXLabelLinePath.moveTo(locationX, 0);
                    mXLabelLinePath.lineTo(locationX, getHeight() - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval());
                    canvas.drawPath(mXLabelLinePath, mXLabelLinePaint);
                    float distance = (float) ((height - BLACK_X_LABEL_LINE - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval() - mValueSelectHeight - BLACK_VALUE_TEXT * 2) /
                            (mParent.getYMaxValue() - mParent.getYMinValue()) * (mParent.getData().get(i).getMoney() - mParent.getYMinValue()));
                    //是否是中心值
                    if (centerIndex == i) {
                        mXValuePointColorPaint.setStrokeWidth(8);
                        canvas.drawCircle(locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval() - BLACK_VALUE_TEXT), 4, mXValuePointColorPaint);
                        mXValuePointWhitePaint.setStrokeWidth(4);
                        canvas.drawCircle(locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval() - BLACK_VALUE_TEXT), 8, mXValuePointWhitePaint);
                        mXValuePointColorPaint.setStrokeWidth(3);
                        canvas.drawCircle(locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval() - BLACK_VALUE_TEXT), 12, mXValuePointColorPaint);
                        canvas.drawText("R$" + mParent.getData().get(i).getMoney(), locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval() - mSelectValueMetrics.bottom - BLACK_VALUE_TEXT * 2), mSelectValueTextPaint);
                    } else {
                        mXValuePointWhitePaint.setStrokeWidth(8);
                        canvas.drawCircle(locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval() - BLACK_VALUE_TEXT), 4, mXValuePointWhitePaint);
                        mXValuePointColorPaint.setStrokeWidth(3);
                        canvas.drawCircle(locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval() - BLACK_VALUE_TEXT), 8, mXValuePointColorPaint);
                        canvas.drawText("R$" + mParent.getData().get(i).getMoney(), locationX, (height - distance - BLACK_X_LABEL_LINE / 2 - mXLabelTextNormalHeight - mParent.getXLabelTextLineInterval() - mNormalValueMetrics.bottom - BLACK_VALUE_TEXT * 2), mNormalValueTextPaint);
                    }
                }
            }
        }
    }

    //对需要绘制的坐标点用贝塞尔曲线连接 代码来自：https://www.jianshu.com/p/98088ff77607
    private void drawValuePath(Canvas canvas) {
        if (mPointList.size() <= 0) {
            return;
        }
        //保存曲线路径
        mPointPath.reset();
        float prePreviousPointX = Float.NaN;
        float prePreviousPointY = Float.NaN;
        float previousPointX = Float.NaN;
        float previousPointY = Float.NaN;
        float currentPointX = Float.NaN;
        float currentPointY = Float.NaN;
        float nextPointX;
        float nextPointY;
        int lineSize = mPointList.size();
        for (int valueIndex = 0; valueIndex < lineSize; ++valueIndex) {
            if (Float.isNaN(currentPointX)) {
                PointF point = mPointList.get(valueIndex);
                currentPointX = point.x;
                currentPointY = point.y;
            }
            if (Float.isNaN(previousPointX)) {
                //是否是第一个点
                if (valueIndex > 0) {
                    PointF point = mPointList.get(valueIndex - 1);
                    previousPointX = point.x;
                    previousPointY = point.y;
                } else {
                    //是的话就用当前点表示上一个点
                    previousPointX = currentPointX;
                    previousPointY = currentPointY;
                }
            }
            if (Float.isNaN(prePreviousPointX)) {
                //是否是前两个点
                if (valueIndex > 1) {
                    PointF point = mPointList.get(valueIndex - 2);
                    prePreviousPointX = point.x;
                    prePreviousPointY = point.y;
                } else {
                    //是的话就用当前点表示上上个点
                    prePreviousPointX = previousPointX;
                    prePreviousPointY = previousPointY;
                }
            }
            // 判断是不是最后一个点了
            if (valueIndex < lineSize - 1) {
                PointF point = mPointList.get(valueIndex + 1);
                nextPointX = point.x;
                nextPointY = point.y;
            } else {
                //是的话就用当前点表示下一个点
                nextPointX = currentPointX;
                nextPointY = currentPointY;
            }
            if (valueIndex == 0) {
                // 将Path移动到开始点
                mPointPath.moveTo(currentPointX, currentPointY);
            } else {
                // 求出控制点坐标
                float firstDiffX = (currentPointX - prePreviousPointX);
                float firstDiffY = (currentPointY - prePreviousPointY);
                float secondDiffX = (nextPointX - previousPointX);
                float secondDiffY = (nextPointY - previousPointY);
                float firstControlPointX = previousPointX + (BEZIER_RADIAN * firstDiffX);
                float firstControlPointY = previousPointY + (BEZIER_RADIAN * firstDiffY);
                float secondControlPointX = currentPointX - (BEZIER_RADIAN * secondDiffX);
                float secondControlPointY = currentPointY - (BEZIER_RADIAN * secondDiffY);
                //画出曲线
                mPointPath.cubicTo(firstControlPointX, firstControlPointY, secondControlPointX, secondControlPointY,
                        currentPointX, currentPointY);
            }
            // 更新值,
            prePreviousPointX = previousPointX;
            prePreviousPointY = previousPointY;
            previousPointX = currentPointX;
            previousPointY = currentPointY;
            currentPointX = nextPointX;
            currentPointY = nextPointY;
        }
        canvas.drawPath(mPointPath, mLinkLinePaint);
    }

    //初始化画笔
    private void initPaint() {
        //x轴轴线
        mXLabelLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mXLabelLinePaint.setStrokeWidth(mParent.getXLabelLineWidth());
        mXLabelLinePaint.setColor(mParent.getXLabelLineColor());
        mXLabelLinePaint.setStyle(Paint.Style.STROKE);
        //是否是虚线
        if (mParent.getXLabelLineLength() > 0 && mParent.getXLabelLineDashLength() > 0) {
            mXLabelLinePaint.setPathEffect(new DashPathEffect(new float[]{mParent.getXLabelLineLength(), mParent.getXLabelLineDashLength()}, 0));
        }
        //x轴label文字
        mXLabelTextNormalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mXLabelTextNormalPaint.setTextSize(mParent.getXLabelTextNormalSize());
        mXLabelTextNormalPaint.setColor(mParent.getXLabelTextNormalColor());
        mXLabelTextNormalPaint.setTextAlign(Paint.Align.CENTER);
        mXLabelTextNormalMetrics = mXLabelTextNormalPaint.getFontMetrics();
        mXLabelTextNormalHeight = mXLabelTextNormalMetrics.bottom - mXLabelTextNormalMetrics.top;
        //x轴label文字
        mXLabelTextSelectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mXLabelTextSelectPaint.setTextSize(mParent.getXLabelTextSelectSize());
        mXLabelTextSelectPaint.setColor(mParent.getXLabelTextSelectColor());
        mXLabelTextSelectPaint.setTextAlign(Paint.Align.CENTER);
        mXLabelTextSelectMetrics = mXLabelTextNormalPaint.getFontMetrics();
        //坐标点的绘制
        mXValuePointColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mXValuePointColorPaint.setStrokeWidth(3);
        mXValuePointColorPaint.setColor(mParent.getValuePointColor());
        mXValuePointColorPaint.setStyle(Paint.Style.STROKE);
        mXValuePointColorPaint.setStrokeCap(Paint.Cap.ROUND);
        //坐标点白色填充的绘制
        mXValuePointWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mXValuePointWhitePaint.setStrokeWidth(5);
        mXValuePointWhitePaint.setColor(Color.WHITE);
        mXValuePointWhitePaint.setStyle(Paint.Style.STROKE);
        mXValuePointWhitePaint.setStrokeCap(Paint.Cap.ROUND);
        //未选中坐标值
        mNormalValueTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mNormalValueTextPaint.setTextSize(mParent.getNormalValueTextSize());
        mNormalValueTextPaint.setColor(mParent.getNormalValueTextColor());
        mNormalValueTextPaint.setTextAlign(Paint.Align.CENTER);
        mNormalValueMetrics = mNormalValueTextPaint.getFontMetrics();
        //选中坐标值
        mSelectValueTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSelectValueTextPaint.setTextSize(mParent.getSelectValueTextSize());
        mSelectValueTextPaint.setColor(mParent.getSelectValueTextColor());
        mSelectValueTextPaint.setTextAlign(Paint.Align.CENTER);
        mSelectValueMetrics = mSelectValueTextPaint.getFontMetrics();
        mValueSelectHeight = mSelectValueMetrics.bottom - mSelectValueMetrics.top;
        //连接线
        mLinkLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinkLinePaint.setColor(mParent.getLinkLineColor());
        mLinkLinePaint.setStrokeWidth(mParent.getLinkLineWidth());
        mLinkLinePaint.setStyle(Paint.Style.STROKE);
    }

    //API小于18则关闭硬件加速，否则setAntiAlias()方法不生效
    private void checkAPILevel() {
        if (Build.VERSION.SDK_INT < 18) {
            setLayerType(LAYER_TYPE_NONE, null);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}