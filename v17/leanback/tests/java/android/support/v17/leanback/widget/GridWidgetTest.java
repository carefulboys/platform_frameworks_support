/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.support.v17.leanback.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Parcelable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.filters.SdkSuppress;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.v17.leanback.test.R;
import android.support.v17.leanback.testutils.PollingCheck;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerViewAccessibilityDelegate;
import android.text.Selection;
import android.text.Spannable;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class GridWidgetTest {

    private static final boolean HUMAN_DELAY = false;
    private static final long WAIT_FOR_SCROLL_IDLE_TIMEOUT_MS = 60000;
    private static final int WAIT_FOR_LAYOUT_PASS_TIMEOUT_MS = 2000;
    private static final int WAIT_FOR_ITEM_ANIMATION_FINISH_TIMEOUT_MS = 2000;

    protected ActivityTestRule<GridActivity> mActivityTestRule;
    protected GridActivity mActivity;
    protected BaseGridView mGridView;
    protected GridLayoutManager mLayoutManager;
    private GridLayoutManager.OnLayoutCompleteListener mWaitLayoutListener;
    protected int mOrientation;
    protected int mNumRows;
    protected int[] mRemovedItems;

    private final Comparator<View> mRowSortComparator = new Comparator<View>() {
        public int compare(View lhs, View rhs) {
            if (mOrientation == BaseGridView.HORIZONTAL) {
                return lhs.getLeft() - rhs.getLeft();
            } else {
                return lhs.getTop() - rhs.getTop();
            }
        };
    };

    /**
     * Verify margins between items on same row are same.
     */
    private final Runnable mVerifyLayout = new Runnable() {
        @Override
        public void run() {
            verifyMargin();
        }
    };

    @Rule public TestName testName = new TestName();

    public static void sendKey(int keyCode) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode);
    }

    public static void sendRepeatedKeys(int repeats, int keyCode) {
        for (int i = 0; i < repeats; i++) {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode);
        }
    }

    private void humanDelay(int delay) throws InterruptedException {
        if (HUMAN_DELAY) Thread.sleep(delay);
    }
    /**
     * Change size of the Adapter and notifyDataSetChanged.
     */
    private void changeArraySize(final int size) throws Throwable {
        performAndWaitForAnimation(new Runnable() {
            public void run() {
                mActivity.changeArraySize(size);
            }
        });
    }

    static String dumpGridView(BaseGridView gridView) {
        return "findFocus:" + gridView.getRootView().findFocus()
                + " isLayoutRequested:" + gridView.isLayoutRequested()
                + " selectedPosition:" + gridView.getSelectedPosition()
                + " adapter.itemCount:" + gridView.getAdapter().getItemCount()
                + " itemAnimator.isRunning:" + gridView.getItemAnimator().isRunning()
                + " scrollState:" + gridView.getScrollState();
    }

    /**
     * Change selected position.
     */
    private void setSelectedPosition(final int position, final int scrollExtra) throws Throwable {
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPosition(position, scrollExtra);
            }
        });
        Thread.sleep(500);
    }

    /**
     * Scrolls using given key.
     */
    protected void scroll(int key, Runnable verify) throws Throwable {
        do {
            if (verify != null) {
                mActivityTestRule.runOnUiThread(verify);
            }
            sendRepeatedKeys(10, key);
            try {
                Thread.sleep(300);
            } catch (InterruptedException ex) {
                break;
            }
        } while (mGridView.getLayoutManager().isSmoothScrolling()
                || mGridView.getScrollState() != BaseGridView.SCROLL_STATE_IDLE);
    }

    protected void scrollToBegin(Runnable verify) throws Throwable {
        int key;
        // first move to first column/row
        if (mOrientation == BaseGridView.HORIZONTAL) {
            key = KeyEvent.KEYCODE_DPAD_UP;
        } else {
            if (mGridView.getLayoutDirection() == ViewGroup.LAYOUT_DIRECTION_RTL) {
                key = KeyEvent.KEYCODE_DPAD_RIGHT;
            } else {
                key = KeyEvent.KEYCODE_DPAD_LEFT;
            }
        }
        scroll(key, null);
        if (mOrientation == BaseGridView.HORIZONTAL) {
            if (mGridView.getLayoutDirection() == ViewGroup.LAYOUT_DIRECTION_RTL) {
                key = KeyEvent.KEYCODE_DPAD_RIGHT;
            } else {
                key = KeyEvent.KEYCODE_DPAD_LEFT;
            }
        } else {
            key = KeyEvent.KEYCODE_DPAD_UP;
        }
        scroll(key, verify);
    }

    protected void scrollToEnd(Runnable verify) throws Throwable {
        int key;
        // first move to first column/row
        if (mOrientation == BaseGridView.HORIZONTAL) {
            key = KeyEvent.KEYCODE_DPAD_UP;
        } else {
            if (mGridView.getLayoutDirection() == ViewGroup.LAYOUT_DIRECTION_RTL) {
                key = KeyEvent.KEYCODE_DPAD_RIGHT;
            } else {
                key = KeyEvent.KEYCODE_DPAD_LEFT;
            }
        }
        scroll(key, null);
        if (mOrientation == BaseGridView.HORIZONTAL) {
            if (mGridView.getLayoutDirection() == ViewGroup.LAYOUT_DIRECTION_RTL) {
                key = KeyEvent.KEYCODE_DPAD_LEFT;
            } else {
                key = KeyEvent.KEYCODE_DPAD_RIGHT;
            }
        } else {
            key = KeyEvent.KEYCODE_DPAD_DOWN;
        }
        scroll(key, verify);
    }

    /**
     * Group and sort children by their position on each row (HORIZONTAL) or column(VERTICAL).
     */
    protected View[][] sortByRows() {
        final HashMap<Integer, ArrayList<View>> rows = new HashMap<Integer, ArrayList<View>>();
        ArrayList<Integer> rowLocations = new ArrayList();
        for (int i = 0; i < mGridView.getChildCount(); i++) {
            View v = mGridView.getChildAt(i);
            int rowLocation;
            if (mOrientation == BaseGridView.HORIZONTAL) {
                rowLocation = v.getTop();
            } else {
                rowLocation = mGridView.getLayoutDirection() == ViewGroup.LAYOUT_DIRECTION_RTL
                        ? v.getRight() : v.getLeft();
            }
            ArrayList<View> views = rows.get(rowLocation);
            if (views == null) {
                views = new ArrayList<View>();
                rows.put(rowLocation, views);
                rowLocations.add(rowLocation);
            }
            views.add(v);
        }
        Object[] sortedLocations = rowLocations.toArray();
        Arrays.sort(sortedLocations);
        if (mNumRows != rows.size()) {
            assertEquals("Dump Views by rows "+rows, mNumRows, rows.size());
        }
        View[][] sorted = new View[rows.size()][];
        for (int i = 0; i < rowLocations.size(); i++) {
            Integer rowLocation = rowLocations.get(i);
            ArrayList<View> arr = rows.get(rowLocation);
            View[] views = arr.toArray(new View[arr.size()]);
            Arrays.sort(views, mRowSortComparator);
            sorted[i] = views;
        }
        return sorted;
    }

    protected void verifyMargin() {
        View[][] sorted = sortByRows();
        for (int row = 0; row < sorted.length; row++) {
            View[] views = sorted[row];
            int margin = -1;
            for (int i = 1; i < views.length; i++) {
                if (mOrientation == BaseGridView.HORIZONTAL) {
                    assertEquals(mGridView.getHorizontalMargin(),
                            views[i].getLeft() - views[i - 1].getRight());
                } else {
                    assertEquals(mGridView.getVerticalMargin(),
                            views[i].getTop() - views[i - 1].getBottom());
                }
            }
        }
    }

    protected void verifyBeginAligned() {
        View[][] sorted = sortByRows();
        int alignedLocation = 0;
        if (mOrientation == BaseGridView.HORIZONTAL) {
            if (mGridView.getLayoutDirection() == ViewGroup.LAYOUT_DIRECTION_RTL) {
                for (int i = 0; i < sorted.length; i++) {
                    if (i == 0) {
                        alignedLocation = sorted[i][sorted[i].length - 1].getRight();
                    } else {
                        assertEquals(alignedLocation, sorted[i][sorted[i].length - 1].getRight());
                    }
                }
            } else {
                for (int i = 0; i < sorted.length; i++) {
                    if (i == 0) {
                        alignedLocation = sorted[i][0].getLeft();
                    } else {
                        assertEquals(alignedLocation, sorted[i][0].getLeft());
                    }
                }
            }
        } else {
            for (int i = 0; i < sorted.length; i++) {
                if (i == 0) {
                    alignedLocation = sorted[i][0].getTop();
                } else {
                    assertEquals(alignedLocation, sorted[i][0].getTop());
                }
            }
        }
    }

    protected int[] getEndEdges() {
        View[][] sorted = sortByRows();
        int[] edges = new int[sorted.length];
        if (mOrientation == BaseGridView.HORIZONTAL) {
            if (mGridView.getLayoutDirection() == ViewGroup.LAYOUT_DIRECTION_RTL) {
                for (int i = 0; i < sorted.length; i++) {
                    edges[i] = sorted[i][0].getLeft();
                }
            } else {
                for (int i = 0; i < sorted.length; i++) {
                    edges[i] = sorted[i][sorted[i].length - 1].getRight();
                }
            }
        } else {
            for (int i = 0; i < sorted.length; i++) {
                edges[i] = sorted[i][sorted[i].length - 1].getBottom();
            }
        }
        return edges;
    }

    protected void verifyEdgesSame(int[] edges, int[] edges2) {
        assertEquals(edges.length, edges2.length);
        for (int i = 0; i < edges.length; i++) {
            assertEquals(edges[i], edges2[i]);
        }
    }

    protected void verifyBoundCount(int count) {
        if (mActivity.getBoundCount() != count) {
            StringBuffer b = new StringBuffer();
            b.append("ItemsLength: ");
            for (int i = 0; i < mActivity.mItemLengths.length; i++) {
                b.append(mActivity.mItemLengths[i]).append(",");
            }
            assertEquals("Bound count does not match, ItemsLengths: "+ b,
                    count, mActivity.getBoundCount());
        }
    }

    private static int getCenterY(View v) {
        return (v.getTop() + v.getBottom())/2;
    }

    private static int getCenterX(View v) {
        return (v.getLeft() + v.getRight())/2;
    }

    private void initActivity(Intent intent) throws Throwable {
        mActivityTestRule = new ActivityTestRule<GridActivity>(GridActivity.class, false, false);
        mActivity = mActivityTestRule.launchActivity(intent);
        mActivityTestRule.runOnUiThread(new Runnable() {
                public void run() {
                    mActivity.setTitle(testName.getMethodName());
                }
            });
        Thread.sleep(1000);
        mGridView = mActivity.mGridView;
        mLayoutManager = (GridLayoutManager) mGridView.getLayoutManager();
    }

    @After
    public void clearTest() {
        mWaitLayoutListener = null;
        mLayoutManager = null;
        mGridView = null;
        mActivity = null;
        mActivityTestRule = null;
    }

    /**
     * Must be called before waitForLayout() to prepare layout listener.
     */
    protected void startWaitLayout() {
        if (mWaitLayoutListener != null) {
            throw new IllegalStateException("startWaitLayout() already called");
        }
        if (mLayoutManager.mLayoutCompleteListener != null) {
            throw new IllegalStateException("Cannot startWaitLayout()");
        }
        mWaitLayoutListener = mLayoutManager.mLayoutCompleteListener =
                mock(GridLayoutManager.OnLayoutCompleteListener.class);
    }

    /**
     * wait layout to be called and remove the listener.
     */
    protected void waitForLayout() {
        if (mWaitLayoutListener == null) {
            throw new IllegalStateException("startWaitLayout() not called");
        }
        if (mWaitLayoutListener != mLayoutManager.mLayoutCompleteListener) {
            throw new IllegalStateException("layout listener inconistent");
        }
        try {
            verify(mWaitLayoutListener, timeout(WAIT_FOR_LAYOUT_PASS_TIMEOUT_MS).atLeastOnce())
                    .onLayoutCompleted(any(RecyclerView.State.class));
        } finally {
            mWaitLayoutListener = null;
            mLayoutManager.mLayoutCompleteListener = null;
        }
    }

    /**
     * If currently running animator, wait for it to finish, otherwise return immediately.
     * To wait the ItemAnimator start, you can use waitForLayout() to make sure layout pass has
     * processed adapter change.
     */
    protected void waitForItemAnimation() {
        RecyclerView.ItemAnimator.ItemAnimatorFinishedListener listener = mock(
                RecyclerView.ItemAnimator.ItemAnimatorFinishedListener.class);
        if (mGridView.getItemAnimator().isRunning(listener)) {
            verify(listener, timeout(WAIT_FOR_ITEM_ANIMATION_FINISH_TIMEOUT_MS).atLeastOnce())
                    .onAnimationsFinished();
        }
    }

    /**
     * Run task in UI thread and wait for layout and ItemAnimator finishes.
     */
    protected void performAndWaitForAnimation(Runnable task) throws Throwable {
        startWaitLayout();
        mActivityTestRule.runOnUiThread(task);
        waitForLayout();
        waitForItemAnimation();
    }

    protected void waitForScrollIdle() throws Throwable {
        waitForScrollIdle(null);
    }

    /**
     * Wait for grid view stop scroll and optionally verify state of grid view.
     */
    protected void waitForScrollIdle(Runnable verify) throws Throwable {
        Thread.sleep(100);
        int total = 0;
        while (mGridView.getLayoutManager().isSmoothScrolling()
                || mGridView.getScrollState() != BaseGridView.SCROLL_STATE_IDLE) {
            if ((total += 100) >= WAIT_FOR_SCROLL_IDLE_TIMEOUT_MS) {
                throw new RuntimeException("waitForScrollIdle Timeout");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                break;
            }
            if (verify != null) {
                mActivityTestRule.runOnUiThread(verify);
            }
        }
    }

    @Test
    public void testThreeRowHorizontalBasic() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID, R.layout.horizontal_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 100);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 3;

        scrollToEnd(mVerifyLayout);
        verifyBoundCount(100);

        scrollToBegin(mVerifyLayout);

        verifyBeginAligned();
    }

    static class DividerDecoration extends RecyclerView.ItemDecoration {

        private ColorDrawable mTopDivider;
        private ColorDrawable mBottomDivider;
        private int mLeftOffset;
        private int mRightOffset;
        private int mTopOffset;
        private int mBottomOffset;

        DividerDecoration(int leftOffset, int topOffset, int rightOffset, int bottomOffset) {
            mLeftOffset = leftOffset;
            mTopOffset = topOffset;
            mRightOffset = rightOffset;
            mBottomOffset = bottomOffset;
        }

        @Override
        public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
            if (mTopDivider == null) {
                mTopDivider = new ColorDrawable(Color.RED);
            }
            if (mBottomDivider == null) {
                mBottomDivider = new ColorDrawable(Color.BLUE);
            }
            final int childCount = parent.getChildCount();
            final int width = parent.getWidth();
            for (int childViewIndex = 0; childViewIndex < childCount; childViewIndex++) {
                final View view = parent.getChildAt(childViewIndex);
                mTopDivider.setBounds(0, (int) view.getY() - mTopOffset, width, (int) view.getY());
                mTopDivider.draw(c);
                mBottomDivider.setBounds(0, (int) view.getY() + view.getHeight(), width,
                        (int) view.getY() + view.getHeight() + mBottomOffset);
                mBottomDivider.draw(c);
            }
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                                   RecyclerView.State state) {
            outRect.left = mLeftOffset;
            outRect.top = mTopOffset;
            outRect.right = mRightOffset;
            outRect.bottom = mBottomOffset;
        }
    }

    @Test
    public void testItemDecorationAndMargins() throws Throwable {

        final int leftMargin = 3;
        final int topMargin = 4;
        final int rightMargin = 7;
        final int bottomMargin = 8;
        final int itemHeight = 100;

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID, R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_ITEMS, new int[]{itemHeight, itemHeight, itemHeight});
        intent.putExtra(GridActivity.EXTRA_LAYOUT_MARGINS,
                new int[]{leftMargin, topMargin, rightMargin, bottomMargin});
        initActivity(intent);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        final int paddingLeft = mGridView.getPaddingLeft();
        final int paddingTop = mGridView.getPaddingTop();
        final int verticalSpace = mGridView.getVerticalMargin();
        final int decorationLeft = 17;
        final int decorationTop = 1;
        final int decorationRight = 19;
        final int decorationBottom = 2;

        performAndWaitForAnimation(new Runnable() {
            public void run() {
                mGridView.addItemDecoration(new DividerDecoration(decorationLeft, decorationTop,
                        decorationRight, decorationBottom));
            }
        });

        View child0 = mGridView.getChildAt(0);
        View child1 = mGridView.getChildAt(1);
        View child2 = mGridView.getChildAt(2);

        assertEquals(itemHeight, child0.getBottom() - child0.getTop());

        // verify left margins
        assertEquals(paddingLeft + leftMargin + decorationLeft, child0.getLeft());
        assertEquals(paddingLeft + leftMargin + decorationLeft, child1.getLeft());
        assertEquals(paddingLeft + leftMargin + decorationLeft, child2.getLeft());
        // verify top bottom margins and decoration offset
        assertEquals(paddingTop + topMargin + decorationTop, child0.getTop());
        assertEquals(bottomMargin + decorationBottom + verticalSpace + decorationTop + topMargin,
                child1.getTop() - child0.getBottom());
        assertEquals(bottomMargin + decorationBottom + verticalSpace + decorationTop + topMargin,
                child2.getTop() - child1.getBottom());

    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.LOLLIPOP)
    @Test
    public void testItemDecorationAndMarginsAndOpticalBounds() throws Throwable {
        final int leftMargin = 3;
        final int topMargin = 4;
        final int rightMargin = 7;
        final int bottomMargin = 8;
        final int itemHeight = 100;
        final int ninePatchDrawableResourceId = R.drawable.lb_card_shadow_focused;

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID, R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_ITEMS, new int[]{itemHeight, itemHeight, itemHeight});
        intent.putExtra(GridActivity.EXTRA_CHILD_LAYOUT_ID, R.layout.relative_layout);
        intent.putExtra(GridActivity.EXTRA_LAYOUT_MARGINS,
                new int[]{leftMargin, topMargin, rightMargin, bottomMargin});
        intent.putExtra(GridActivity.EXTRA_NINEPATCH_SHADOW, ninePatchDrawableResourceId);
        initActivity(intent);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        final int paddingLeft = mGridView.getPaddingLeft();
        final int paddingTop = mGridView.getPaddingTop();
        final int verticalSpace = mGridView.getVerticalMargin();
        final int decorationLeft = 17;
        final int decorationTop = 1;
        final int decorationRight = 19;
        final int decorationBottom = 2;

        final Rect opticalPaddings = new Rect();
        mGridView.getResources().getDrawable(ninePatchDrawableResourceId)
                .getPadding(opticalPaddings);
        final int opticalInsetsLeft = opticalPaddings.left;
        final int opticalInsetsTop = opticalPaddings.top;
        final int opticalInsetsRight = opticalPaddings.right;
        final int opticalInsetsBottom = opticalPaddings.bottom;
        assertTrue(opticalInsetsLeft > 0);
        assertTrue(opticalInsetsTop > 0);
        assertTrue(opticalInsetsRight > 0);
        assertTrue(opticalInsetsBottom > 0);

        performAndWaitForAnimation(new Runnable() {
            public void run() {
                mGridView.addItemDecoration(new DividerDecoration(decorationLeft, decorationTop,
                        decorationRight, decorationBottom));
            }
        });

        View child0 = mGridView.getChildAt(0);
        View child1 = mGridView.getChildAt(1);
        View child2 = mGridView.getChildAt(2);

        assertEquals(itemHeight + opticalInsetsTop + opticalInsetsBottom,
                child0.getBottom() - child0.getTop());

        // verify left margins decoration and optical insets
        assertEquals(paddingLeft + leftMargin + decorationLeft - opticalInsetsLeft,
                child0.getLeft());
        assertEquals(paddingLeft + leftMargin + decorationLeft - opticalInsetsLeft,
                child1.getLeft());
        assertEquals(paddingLeft + leftMargin + decorationLeft - opticalInsetsLeft,
                child2.getLeft());
        // verify top bottom margins decoration offset and optical insets
        assertEquals(paddingTop + topMargin + decorationTop, child0.getTop() + opticalInsetsTop);
        assertEquals(bottomMargin + decorationBottom + verticalSpace + decorationTop + topMargin,
                (child1.getTop() + opticalInsetsTop) - (child0.getBottom() - opticalInsetsBottom));
        assertEquals(bottomMargin + decorationBottom + verticalSpace + decorationTop + topMargin,
                (child2.getTop() + opticalInsetsTop) - (child1.getBottom() - opticalInsetsBottom));

    }

    @Test
    public void testThreeColumnVerticalBasic() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID, R.layout.vertical_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 200);
        initActivity(intent);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 3;

        scrollToEnd(mVerifyLayout);
        verifyBoundCount(200);

        scrollToBegin(mVerifyLayout);

        verifyBeginAligned();
    }

    @Test
    public void testRedundantAppendRemove() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_grid_testredundantappendremove);
        intent.putExtra(GridActivity.EXTRA_ITEMS, new int[]{
                149,177,128,234,227,187,163,223,146,210,228,148,227,193,182,197,177,142,225,207,
                157,171,209,204,187,184,123,221,197,153,202,179,193,214,226,173,225,143,188,159,
                139,193,233,143,227,203,222,124,228,223,164,131,228,126,211,160,165,152,235,184,
                155,224,149,181,171,229,200,234,177,130,164,172,188,139,132,203,179,220,147,131,
                226,127,230,239,183,203,206,227,123,170,239,234,200,149,237,204,160,133,202,234,
                173,122,139,149,151,153,216,231,121,145,227,153,186,174,223,180,123,215,206,216,
                239,222,219,207,193,218,140,133,171,153,183,132,233,138,159,174,189,171,143,128,
                152,222,141,202,224,190,134,120,181,231,230,136,132,224,136,210,207,150,128,183,
                221,194,179,220,126,221,137,205,223,193,172,132,226,209,133,191,227,127,159,171,
                180,149,237,177,194,207,170,202,161,144,147,199,205,186,164,140,193,203,224,129});
        initActivity(intent);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 3;

        scrollToEnd(mVerifyLayout);

        verifyBoundCount(200);

        scrollToBegin(mVerifyLayout);

        verifyBeginAligned();
    }

    @Test
    public void testRedundantAppendRemove2() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_grid_testredundantappendremove2);
        intent.putExtra(GridActivity.EXTRA_ITEMS, new int[]{
                318,333,199,224,246,273,269,289,340,313,265,306,349,269,185,282,257,354,316,252,
                237,290,283,343,196,313,290,343,191,262,342,228,343,349,251,203,226,305,265,213,
                216,333,295,188,187,281,288,311,244,232,224,332,290,181,267,276,226,261,335,355,
                225,217,219,183,234,285,257,304,182,250,244,223,257,219,342,185,347,205,302,315,
                299,309,292,237,192,309,228,250,347,227,337,298,299,185,185,331,223,284,265,351});
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 3;
        mLayoutManager = (GridLayoutManager) mGridView.getLayoutManager();

        // test append without staggered result cache
        scrollToEnd(mVerifyLayout);

        verifyBoundCount(100);
        int[] endEdges = getEndEdges();

        scrollToBegin(mVerifyLayout);

        verifyBeginAligned();

        // now test append with staggered result cache
        changeArraySize(3);
        assertEquals("Staggerd cache should be kept as is when no item size change",
                100, ((StaggeredGrid) mLayoutManager.mGrid).mLocations.size());

        mActivity.resetBoundCount();
        changeArraySize(100);

        scrollToEnd(mVerifyLayout);
        verifyBoundCount(100);

        // we should get same aligned end edges
        int[] endEdges2 = getEndEdges();
        verifyEdgesSame(endEdges, endEdges2);
    }

    @Test
    public void testItemMovedHorizontal() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 200);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 3;

        mGridView.setSelectedPositionSmooth(150);
        waitForScrollIdle(mVerifyLayout);
        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                mActivity.swap(150, 152);
            }
        });
        mActivityTestRule.runOnUiThread(mVerifyLayout);

        scrollToBegin(mVerifyLayout);

        verifyBeginAligned();
    }

    @Test
    public void testItemMovedVertical() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 200);
        initActivity(intent);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 3;

        mGridView.setSelectedPositionSmooth(150);
        waitForScrollIdle(mVerifyLayout);
        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                mActivity.swap(150, 152);
            }
        });
        mActivityTestRule.runOnUiThread(mVerifyLayout);

        scrollToEnd(mVerifyLayout);
        scrollToBegin(mVerifyLayout);

        verifyBeginAligned();
    }

    @Test
    public void testAddLastItemHorizontal() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 50);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        mActivityTestRule.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        mGridView.setSelectedPositionSmooth(49);
                    }
                }
        );
        waitForScrollIdle(mVerifyLayout);
        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                mActivity.addItems(50, new int[]{150});
            }
        });

        // assert new added item aligned to right edge
        assertEquals(mGridView.getWidth() - mGridView.getPaddingRight(),
                mGridView.getLayoutManager().findViewByPosition(50).getRight());
    }

    @Test
    public void testAddMultipleLastItemsHorizontal() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 50);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        mActivityTestRule.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        mGridView.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
                        mGridView.setWindowAlignmentOffsetPercent(50);
                        mGridView.setSelectedPositionSmooth(49);
                    }
                }
        );
        waitForScrollIdle(mVerifyLayout);
        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                mActivity.addItems(50, new int[]{150, 150, 150, 150, 150, 150, 150, 150, 150,
                        150, 150, 150, 150, 150});
            }
        });

        // The focused item will be at center of window
        View view = mGridView.getLayoutManager().findViewByPosition(49);
        assertEquals(mGridView.getWidth() / 2, (view.getLeft() + view.getRight()) / 2);
    }

    @Test
    public void testItemAddRemoveHorizontal() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 200);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 3;

        scrollToEnd(mVerifyLayout);
        int[] endEdges = getEndEdges();

        mGridView.setSelectedPositionSmooth(150);
        waitForScrollIdle(mVerifyLayout);
        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                mRemovedItems = mActivity.removeItems(151, 4);
            }
        });

        scrollToEnd(mVerifyLayout);
        mGridView.setSelectedPositionSmooth(150);
        waitForScrollIdle(mVerifyLayout);

        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                mActivity.addItems(151, mRemovedItems);
            }
        });
        scrollToEnd(mVerifyLayout);

        // we should get same aligned end edges
        int[] endEdges2 = getEndEdges();
        verifyEdgesSame(endEdges, endEdges2);

        scrollToBegin(mVerifyLayout);
        verifyBeginAligned();
    }

    @Test
    public void testSetSelectedPositionDetached() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 50);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        final int focusToIndex = 49;
        final ViewGroup parent = (ViewGroup) mGridView.getParent();
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                parent.removeView(mGridView);
            }
        });
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(focusToIndex);
            }
        });
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                parent.addView(mGridView);
                mGridView.requestFocus();
            }
        });
        waitForScrollIdle();
        assertEquals(mGridView.getSelectedPosition(), focusToIndex);
        assertTrue(mGridView.getLayoutManager().findViewByPosition(focusToIndex).hasFocus());

        final int focusToIndex2 = 0;
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                parent.removeView(mGridView);
            }
        });
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPosition(focusToIndex2);
            }
        });
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                parent.addView(mGridView);
                mGridView.requestFocus();
            }
        });
        assertEquals(mGridView.getSelectedPosition(), focusToIndex2);
        waitForScrollIdle();
        assertTrue(mGridView.getLayoutManager().findViewByPosition(focusToIndex2).hasFocus());
    }

    @Test
    public void testBug22209986() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 50);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        final int focusToIndex = mGridView.getChildCount() - 1;
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(focusToIndex);
            }
        });

        waitForScrollIdle();
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(focusToIndex + 1);
            }
        });
        // let the scroll running for a while and requestLayout during scroll
        Thread.sleep(80);
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                assertEquals(mGridView.getScrollState(), BaseGridView.SCROLL_STATE_SETTLING);
                mGridView.requestLayout();
            }
        });
        waitForScrollIdle();

        int leftEdge = mGridView.getLayoutManager().findViewByPosition(focusToIndex).getLeft();

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.requestLayout();
            }
        });
        waitForScrollIdle();
        assertEquals(leftEdge,
                mGridView.getLayoutManager().findViewByPosition(focusToIndex).getLeft());
    }

    @Test
    public void testScrollAndRemove() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 50);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        final int focusToIndex = mGridView.getChildCount() - 1;
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(focusToIndex);
            }
        });

        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                mActivity.removeItems(focusToIndex, 1);
            }
        });

        waitForScrollIdle();
        int leftEdge = mGridView.getLayoutManager().findViewByPosition(focusToIndex).getLeft();

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.requestLayout();
            }
        });
        waitForScrollIdle();
        assertEquals(leftEdge,
                mGridView.getLayoutManager().findViewByPosition(focusToIndex).getLeft());
    }

    @Test
    public void testScrollAndInsert() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_grid);
        int[] items = new int[1000];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300 + (int)(Math.random() * 100);
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, true);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 3;

        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(150);
            }
        });
        waitForScrollIdle(mVerifyLayout);

        View view =  mGridView.getChildAt(mGridView.getChildCount() - 1);
        final int focusToIndex = mGridView.getChildAdapterPosition(view);
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(focusToIndex);
            }
        });

        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                int[] newItems = new int[]{300, 300, 300};
                mActivity.addItems(0, newItems);
            }
        });
    }

    @Test
    public void testScrollAndInsertBeforeVisibleItem() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_grid);
        int[] items = new int[1000];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300 + (int)(Math.random() * 100);
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, true);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 3;

        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(150);
            }
        });
        waitForScrollIdle(mVerifyLayout);

        View view =  mGridView.getChildAt(mGridView.getChildCount() - 1);
        final int focusToIndex = mGridView.getChildAdapterPosition(view);
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(focusToIndex);
            }
        });

        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                int[] newItems = new int[]{300, 300, 300};
                mActivity.addItems(focusToIndex, newItems);
            }
        });
    }

    @Test
    public void testSmoothScrollAndRemove() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 50);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        final int focusToIndex = 40;
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(focusToIndex);
            }
        });

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mActivity.removeItems(focusToIndex, 1);
            }
        });

        assertTrue("removing the index of not attached child should not affect smooth scroller",
                mGridView.getLayoutManager().isSmoothScrolling());
        waitForScrollIdle();
        int leftEdge = mGridView.getLayoutManager().findViewByPosition(focusToIndex).getLeft();

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.requestLayout();
            }
        });
        waitForScrollIdle();
        assertEquals(leftEdge,
                mGridView.getLayoutManager().findViewByPosition(focusToIndex).getLeft());
    }

    @Test
    public void testSmoothScrollAndRemove2() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 50);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        final int focusToIndex = 40;
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(focusToIndex);
            }
        });

        startWaitLayout();
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                final int removeIndex = mGridView.getChildViewHolder(
                        mGridView.getChildAt(mGridView.getChildCount() - 1)).getAdapterPosition();
                mActivity.removeItems(removeIndex, 1);
            }
        });
        waitForLayout();

        assertFalse("removing the index of attached child should kill smooth scroller",
                mGridView.getLayoutManager().isSmoothScrolling());
        waitForItemAnimation();
        int leftEdge = mGridView.getLayoutManager().findViewByPosition(focusToIndex).getLeft();

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.requestLayout();
            }
        });
        waitForScrollIdle();
        assertEquals(leftEdge,
                mGridView.getLayoutManager().findViewByPosition(focusToIndex).getLeft());
    }

    @Test
    public void testPendingSmoothScrollAndRemove() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_REQUEST_FOCUS_ONLAYOUT, true);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 630 + (int)(Math.random() * 100);
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, true);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        mGridView.setSelectedPositionSmooth(0);
        waitForScrollIdle(mVerifyLayout);
        assertTrue(mGridView.getChildAt(0).hasFocus());

        // Pressing lots of key to make sure smooth scroller is running
        for (int i = 0; i < 20; i++) {
            sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
        }

        assertTrue(mGridView.getLayoutManager().isSmoothScrolling());
        startWaitLayout();
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                final int removeIndex = mGridView.getChildViewHolder(
                        mGridView.getChildAt(mGridView.getChildCount() - 1)).getAdapterPosition();
                mActivity.removeItems(removeIndex, 1);
            }
        });
        waitForLayout();

        assertFalse("removing the index of attached child should kill smooth scroller",
                mGridView.getLayoutManager().isSmoothScrolling());

        waitForItemAnimation();
        int focusIndex = mGridView.getSelectedPosition();
        int topEdge = mGridView.getLayoutManager().findViewByPosition(focusIndex).getTop();

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.requestLayout();
            }
        });
        waitForScrollIdle();
        assertEquals(topEdge,
                mGridView.getLayoutManager().findViewByPosition(focusIndex).getTop());
    }

    @Test
    public void testFocusToFirstItem() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 200);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 3;

        performAndWaitForAnimation(new Runnable() {
            public void run() {
                mRemovedItems = mActivity.removeItems(0, 200);
            }
        });

        humanDelay(500);
        performAndWaitForAnimation(new Runnable() {
            public void run() {
                mActivity.addItems(0, mRemovedItems);
            }
        });

        humanDelay(500);
        assertTrue(mGridView.getLayoutManager().findViewByPosition(0).hasFocus());

        changeArraySize(0);

        changeArraySize(200);
        assertTrue(mGridView.getLayoutManager().findViewByPosition(0).hasFocus());
    }

    @Test
    public void testNonFocusableHorizontal() throws Throwable {
        final int numItems = 200;
        final int startPos = 45;
        final int skips = 20;
        final int numColumns = 3;
        final int endPos = startPos + numColumns * (skips + 1);

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, numItems);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = numColumns;
        boolean[] focusable = new boolean[numItems];
        for (int i = 0; i < focusable.length; i++) {
            focusable[i] = true;
        }
        for (int i = startPos + mNumRows, j = 0; j < skips; i += mNumRows, j++) {
            focusable[i] = false;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS_FOCUSABLE, focusable);
        initActivity(intent);

        mGridView.setSelectedPositionSmooth(startPos);
        waitForScrollIdle(mVerifyLayout);

        if (mGridView.getLayoutDirection() == ViewGroup.LAYOUT_DIRECTION_RTL) {
            sendKey(KeyEvent.KEYCODE_DPAD_LEFT);
        } else {
            sendKey(KeyEvent.KEYCODE_DPAD_RIGHT);
        }
        waitForScrollIdle(mVerifyLayout);
        assertEquals(endPos, mGridView.getSelectedPosition());

        if (mGridView.getLayoutDirection() == ViewGroup.LAYOUT_DIRECTION_RTL) {
            sendKey(KeyEvent.KEYCODE_DPAD_RIGHT);
        } else {
            sendKey(KeyEvent.KEYCODE_DPAD_LEFT);
        }
        waitForScrollIdle(mVerifyLayout);
        assertEquals(startPos, mGridView.getSelectedPosition());

    }

    @Test
    public void testNoInitialFocusable() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear);
        final int numItems = 100;
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, numItems);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;
        boolean[] focusable = new boolean[numItems];
        final int firstFocusableIndex = 10;
        for (int i = 0; i < firstFocusableIndex; i++) {
            focusable[i] = false;
        }
        for (int i = firstFocusableIndex; i < focusable.length; i++) {
            focusable[i] = true;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS_FOCUSABLE, focusable);
        initActivity(intent);
        assertTrue(mGridView.isFocused());

        if (mGridView.getLayoutDirection() == ViewGroup.LAYOUT_DIRECTION_RTL) {
            sendKey(KeyEvent.KEYCODE_DPAD_LEFT);
        } else {
            sendKey(KeyEvent.KEYCODE_DPAD_RIGHT);
        }
        waitForScrollIdle(mVerifyLayout);
        assertEquals(firstFocusableIndex, mGridView.getSelectedPosition());
        assertTrue(mGridView.getLayoutManager().findViewByPosition(firstFocusableIndex).hasFocus());
    }

    @Test
    public void testFocusOutOfEmptyListView() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear);
        final int numItems = 100;
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, numItems);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;
        initActivity(intent);

        final View horizontalGridView = new HorizontalGridViewEx(mGridView.getContext());
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                horizontalGridView.setFocusable(true);
                horizontalGridView.setFocusableInTouchMode(true);
                horizontalGridView.setLayoutParams(new ViewGroup.LayoutParams(100, 100));
                ((ViewGroup) mGridView.getParent()).addView(horizontalGridView, 0);
                horizontalGridView.requestFocus();
            }
        });

        assertTrue(horizontalGridView.isFocused());

        sendKey(KeyEvent.KEYCODE_DPAD_DOWN);

        assertTrue(mGridView.hasFocus());
    }

    @Test
    public void testTransferFocusToChildWhenGainFocus() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear);
        final int numItems = 100;
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, numItems);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;
        boolean[] focusable = new boolean[numItems];
        final int firstFocusableIndex = 1;
        for (int i = 0; i < firstFocusableIndex; i++) {
            focusable[i] = false;
        }
        for (int i = firstFocusableIndex; i < focusable.length; i++) {
            focusable[i] = true;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS_FOCUSABLE, focusable);
        initActivity(intent);

        assertEquals(firstFocusableIndex, mGridView.getSelectedPosition());
        assertTrue(mGridView.getLayoutManager().findViewByPosition(firstFocusableIndex).hasFocus());
    }

    @Test
    public void testFocusFromSecondChild() throws Throwable {

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear);
        final int numItems = 100;
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, numItems);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;
        boolean[] focusable = new boolean[numItems];
        for (int i = 0; i < focusable.length; i++) {
            focusable[i] = false;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS_FOCUSABLE, focusable);
        initActivity(intent);

        // switching Adapter to cause a full rebind,  test if it will focus to second item.
        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                mActivity.mNumItems = numItems;
                mActivity.mItemFocusables[1] = true;
                mActivity.rebindToNewAdapter();
            }
        });
        assertTrue(mGridView.findViewHolderForAdapterPosition(1).itemView.hasFocus());
    }

    @Test
    public void removeFocusableItemAndFocusableRecyclerViewGetsFocus() throws Throwable {
        final int numItems = 100;
        final int numColumns = 3;
        final int focusableIndex = 2;

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, numItems);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = numColumns;
        boolean[] focusable = new boolean[numItems];
        for (int i = 0; i < focusable.length; i++) {
            focusable[i] = false;
        }
        focusable[focusableIndex] = true;
        intent.putExtra(GridActivity.EXTRA_ITEMS_FOCUSABLE, focusable);
        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mGridView.setSelectedPositionSmooth(focusableIndex);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        assertEquals(focusableIndex, mGridView.getSelectedPosition());

        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                mActivity.removeItems(focusableIndex, 1);
            }
        });
        assertTrue(dumpGridView(mGridView), mGridView.isFocused());
    }

    @Test
    public void removeFocusableItemAndUnFocusableRecyclerViewLosesFocus() throws Throwable {
        final int numItems = 100;
        final int numColumns = 3;
        final int focusableIndex = 2;

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, numItems);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = numColumns;
        boolean[] focusable = new boolean[numItems];
        for (int i = 0; i < focusable.length; i++) {
            focusable[i] = false;
        }
        focusable[focusableIndex] = true;
        intent.putExtra(GridActivity.EXTRA_ITEMS_FOCUSABLE, focusable);
        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mGridView.setFocusableInTouchMode(false);
                mGridView.setFocusable(false);
                mGridView.setSelectedPositionSmooth(focusableIndex);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        assertEquals(focusableIndex, mGridView.getSelectedPosition());

        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                mActivity.removeItems(focusableIndex, 1);
            }
        });
        assertFalse(dumpGridView(mGridView), mGridView.hasFocus());
    }

    @Test
    public void testNonFocusableVertical() throws Throwable {
        final int numItems = 200;
        final int startPos = 44;
        final int skips = 20;
        final int numColumns = 3;
        final int endPos = startPos + numColumns * (skips + 1);

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, numItems);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = numColumns;
        boolean[] focusable = new boolean[numItems];
        for (int i = 0; i < focusable.length; i++) {
            focusable[i] = true;
        }
        for (int i = startPos + mNumRows, j = 0; j < skips; i += mNumRows, j++) {
            focusable[i] = false;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS_FOCUSABLE, focusable);
        initActivity(intent);

        mGridView.setSelectedPositionSmooth(startPos);
        waitForScrollIdle(mVerifyLayout);

        sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
        waitForScrollIdle(mVerifyLayout);
        assertEquals(endPos, mGridView.getSelectedPosition());

        sendKey(KeyEvent.KEYCODE_DPAD_UP);
        waitForScrollIdle(mVerifyLayout);
        assertEquals(startPos, mGridView.getSelectedPosition());

    }

    @Test
    public void testLtrFocusOutStartDisabled() throws Throwable {
        final int numItems = 200;

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID, R.layout.vertical_grid_ltr);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, numItems);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;
        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mGridView.requestFocus();
                mGridView.setSelectedPositionSmooth(0);
            }
        });
        waitForScrollIdle(mVerifyLayout);

        sendKey(KeyEvent.KEYCODE_DPAD_LEFT);
        waitForScrollIdle(mVerifyLayout);
        assertTrue(mGridView.hasFocus());
    }

    @Test
    public void testRtlFocusOutStartDisabled() throws Throwable {
        final int numItems = 200;

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID, R.layout.vertical_grid_rtl);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, numItems);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;
        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mGridView.requestFocus();
                mGridView.setSelectedPositionSmooth(0);
            }
        });
        waitForScrollIdle(mVerifyLayout);

        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT);
        waitForScrollIdle(mVerifyLayout);
        assertTrue(mGridView.hasFocus());
    }

    @Test
    public void testTransferFocusable() throws Throwable {
        final int numItems = 200;
        final int numColumns = 3;
        final int startPos = 1;

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, numItems);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = numColumns;
        boolean[] focusable = new boolean[numItems];
        for (int i = 0; i < focusable.length; i++) {
            focusable[i] = true;
        }
        for (int i = 0; i < startPos; i++) {
            focusable[i] = false;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS_FOCUSABLE, focusable);
        initActivity(intent);

        changeArraySize(0);
        assertTrue(mGridView.isFocused());

        changeArraySize(numItems);
        assertTrue(mGridView.getLayoutManager().findViewByPosition(startPos).hasFocus());
    }

    @Test
    public void testTransferFocusable2() throws Throwable {
        final int numItems = 200;
        final int numColumns = 3;
        final int startPos = 3; // make sure view at startPos is in visible area.

        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, numItems);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, true);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = numColumns;
        boolean[] focusable = new boolean[numItems];
        for (int i = 0; i < focusable.length; i++) {
            focusable[i] = true;
        }
        for (int i = 0; i < startPos; i++) {
            focusable[i] = false;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS_FOCUSABLE, focusable);
        initActivity(intent);

        assertTrue(mGridView.getLayoutManager().findViewByPosition(startPos).hasFocus());

        changeArraySize(0);
        assertTrue(mGridView.isFocused());

        changeArraySize(numItems);
        assertTrue(mGridView.getLayoutManager().findViewByPosition(startPos).hasFocus());
    }

    @Test
    public void testNonFocusableLoseInFastLayout() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        int[] items = new int[300];
        for (int i = 0; i < items.length; i++) {
            items[i] = 480;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        intent.putExtra(GridActivity.EXTRA_REQUEST_LAYOUT_ONFOCUS, true);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;
        int pressDown = 15;

        initActivity(intent);

        mGridView.setSelectedPositionSmooth(0);
        waitForScrollIdle(mVerifyLayout);

        for (int i = 0; i < pressDown; i++) {
            sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
        }
        waitForScrollIdle(mVerifyLayout);
        assertFalse(mGridView.isFocused());

    }

    @Test
    public void testFocusableViewAvailable() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 0);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        intent.putExtra(GridActivity.EXTRA_ITEMS_FOCUSABLE,
                new boolean[]{false, false, true, false, false});
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // RecyclerView does not respect focusable and focusableInTouchMode flag, so
                // set flags in code.
                mGridView.setFocusableInTouchMode(false);
                mGridView.setFocusable(false);
            }
        });

        assertFalse(mGridView.isFocused());

        final boolean[] scrolled = new boolean[]{false};
        mGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            public void onScrolled(RecyclerView recyclerView, int dx, int dy){
                if (dy > 0) {
                    scrolled[0] = true;
                }
            }
        });
        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                mActivity.addItems(0, new int[]{200, 300, 500, 500, 200});
            }
        });
        waitForScrollIdle(mVerifyLayout);

        assertFalse("GridView should not be scrolled", scrolled[0]);
        assertTrue(mGridView.getLayoutManager().findViewByPosition(2).hasFocus());

    }

    @Test
    public void testSetSelectionWithDelta() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 300);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(3);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        int top1 = mGridView.getLayoutManager().findViewByPosition(3).getTop();

        humanDelay(1000);

        // scroll to position with delta
        setSelectedPosition(3, 100);
        int top2 = mGridView.getLayoutManager().findViewByPosition(3).getTop();
        assertEquals(top1 - 100, top2);

        // scroll to same position without delta, it will be reset
        setSelectedPosition(3, 0);
        int top3 = mGridView.getLayoutManager().findViewByPosition(3).getTop();
        assertEquals(top1, top3);

        // scroll invisible item after last visible item
        final int lastVisiblePos = ((GridLayoutManager)mGridView.getLayoutManager())
                .mGrid.getLastVisibleIndex();
        setSelectedPosition(lastVisiblePos + 1, 100);
        int top4 = mGridView.getLayoutManager().findViewByPosition(lastVisiblePos + 1).getTop();
        assertEquals(top1 - 100, top4);

        // scroll invisible item before first visible item
        final int firstVisiblePos = ((GridLayoutManager)mGridView.getLayoutManager())
                .mGrid.getFirstVisibleIndex();
        setSelectedPosition(firstVisiblePos - 1, 100);
        int top5 = mGridView.getLayoutManager().findViewByPosition(firstVisiblePos - 1).getTop();
        assertEquals(top1 - 100, top5);

        // scroll to invisible item that is far away.
        setSelectedPosition(50, 100);
        int top6 = mGridView.getLayoutManager().findViewByPosition(50).getTop();
        assertEquals(top1 - 100, top6);

        // scroll to invisible item that is far away.
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(100);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        int top7 = mGridView.getLayoutManager().findViewByPosition(100).getTop();
        assertEquals(top1, top7);

        // scroll to invisible item that is far away.
        setSelectedPosition(10, 50);
        int top8 = mGridView.getLayoutManager().findViewByPosition(10).getTop();
        assertEquals(top1 - 50, top8);
    }

    @Test
    public void testSetSelectionWithDeltaInGrid() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 500);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, true);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 3;

        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(10);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        int top1 = getCenterY(mGridView.getLayoutManager().findViewByPosition(10));

        humanDelay(500);

        // scroll to position with delta
        setSelectedPosition(20, 100);
        int top2 = getCenterY(mGridView.getLayoutManager().findViewByPosition(20));
        assertEquals(top1 - 100, top2);

        // scroll to same position without delta, it will be reset
        setSelectedPosition(20, 0);
        int top3 = getCenterY(mGridView.getLayoutManager().findViewByPosition(20));
        assertEquals(top1, top3);

        // scroll invisible item after last visible item
        final int lastVisiblePos = ((GridLayoutManager)mGridView.getLayoutManager())
                .mGrid.getLastVisibleIndex();
        setSelectedPosition(lastVisiblePos + 1, 100);
        int top4 = getCenterY(mGridView.getLayoutManager().findViewByPosition(lastVisiblePos + 1));
        verifyMargin();
        assertEquals(top1 - 100, top4);

        // scroll invisible item before first visible item
        final int firstVisiblePos = ((GridLayoutManager)mGridView.getLayoutManager())
                .mGrid.getFirstVisibleIndex();
        setSelectedPosition(firstVisiblePos - 1, 100);
        int top5 = getCenterY(mGridView.getLayoutManager().findViewByPosition(firstVisiblePos - 1));
        assertEquals(top1 - 100, top5);

        // scroll to invisible item that is far away.
        setSelectedPosition(100, 100);
        int top6 = getCenterY(mGridView.getLayoutManager().findViewByPosition(100));
        assertEquals(top1 - 100, top6);

        // scroll to invisible item that is far away.
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(200);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        Thread.sleep(500);
        int top7 = getCenterY(mGridView.getLayoutManager().findViewByPosition(200));
        assertEquals(top1, top7);

        // scroll to invisible item that is far away.
        setSelectedPosition(10, 50);
        int top8 = getCenterY(mGridView.getLayoutManager().findViewByPosition(10));
        assertEquals(top1 - 50, top8);
    }


    @Test
    public void testSetSelectionWithDeltaInGrid1() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_grid);
        intent.putExtra(GridActivity.EXTRA_ITEMS, new int[]{
                193,176,153,141,203,184,232,139,177,206,222,136,132,237,172,137,
                188,172,163,213,158,219,209,147,133,229,170,197,138,215,188,205,
                223,192,225,170,195,127,229,229,210,195,134,142,160,139,130,222,
                150,163,180,176,157,137,234,169,159,167,182,150,224,231,202,236,
                123,140,181,223,120,185,183,221,123,210,134,158,166,208,149,128,
                192,214,212,198,133,140,158,133,229,173,226,141,180,128,127,218,
                192,235,183,213,216,150,143,193,125,141,219,210,195,195,192,191,
                212,236,157,189,160,220,147,158,220,199,233,231,201,180,168,141,
                156,204,191,183,190,153,123,210,238,151,139,221,223,200,175,191,
                132,184,197,204,236,157,230,151,195,219,212,143,172,149,219,184,
                164,211,132,187,172,142,174,146,127,147,206,238,188,129,199,226,
                132,220,210,159,235,153,208,182,196,123,180,159,131,135,175,226,
                127,134,237,211,133,225,132,124,160,226,224,200,173,137,217,169,
                182,183,176,185,122,168,195,159,172,129,126,129,166,136,149,220,
                178,191,192,238,180,208,234,154,222,206,239,228,129,140,203,125,
                214,175,125,169,196,132,234,138,192,142,234,190,215,232,239,122,
                188,158,128,221,159,237,207,157,232,138,132,214,122,199,121,191,
                199,209,126,164,175,187,173,186,194,224,191,196,146,208,213,210,
                164,176,202,213,123,157,179,138,217,129,186,166,237,211,157,130,
                137,132,171,232,216,239,180,151,137,132,190,133,218,155,171,227,
                193,147,197,164,120,218,193,154,170,196,138,222,161,235,143,154,
                192,178,228,195,178,133,203,178,173,206,178,212,136,157,169,124,
                172,121,128,223,238,125,217,187,184,156,169,215,231,124,210,174,
                146,226,185,134,223,228,183,182,136,133,199,146,180,233,226,225,
                174,233,145,235,216,170,192,171,132,132,134,223,233,148,154,162,
                192,179,197,203,139,197,174,187,135,132,180,136,192,195,124,221,
                120,189,233,233,146,225,234,163,215,143,132,198,156,205,151,190,
                204,239,221,229,123,138,134,217,219,136,218,215,167,139,195,125,
                202,225,178,226,145,208,130,194,228,197,157,215,124,147,174,123,
                237,140,172,181,161,151,229,216,199,199,179,213,146,122,222,162,
                139,173,165,150,160,217,207,137,165,175,129,158,134,133,178,199,
                215,213,122,197
        });
        intent.putExtra(GridActivity.EXTRA_STAGGERED, true);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 3;

        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(10);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        int top1 = getCenterY(mGridView.getLayoutManager().findViewByPosition(10));

        humanDelay(500);

        // scroll to position with delta
        setSelectedPosition(20, 100);
        int top2 = getCenterY(mGridView.getLayoutManager().findViewByPosition(20));
        assertEquals(top1 - 100, top2);

        // scroll to same position without delta, it will be reset
        setSelectedPosition(20, 0);
        int top3 = getCenterY(mGridView.getLayoutManager().findViewByPosition(20));
        assertEquals(top1, top3);

        // scroll invisible item after last visible item
        final int lastVisiblePos = ((GridLayoutManager)mGridView.getLayoutManager())
                .mGrid.getLastVisibleIndex();
        setSelectedPosition(lastVisiblePos + 1, 100);
        int top4 = getCenterY(mGridView.getLayoutManager().findViewByPosition(lastVisiblePos + 1));
        verifyMargin();
        assertEquals(top1 - 100, top4);

        // scroll invisible item before first visible item
        final int firstVisiblePos = ((GridLayoutManager)mGridView.getLayoutManager())
                .mGrid.getFirstVisibleIndex();
        setSelectedPosition(firstVisiblePos - 1, 100);
        int top5 = getCenterY(mGridView.getLayoutManager().findViewByPosition(firstVisiblePos - 1));
        assertEquals(top1 - 100, top5);

        // scroll to invisible item that is far away.
        setSelectedPosition(100, 100);
        int top6 = getCenterY(mGridView.getLayoutManager().findViewByPosition(100));
        assertEquals(top1 - 100, top6);

        // scroll to invisible item that is far away.
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(200);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        Thread.sleep(500);
        int top7 = getCenterY(mGridView.getLayoutManager().findViewByPosition(200));
        assertEquals(top1, top7);

        // scroll to invisible item that is far away.
        setSelectedPosition(10, 50);
        int top8 = getCenterY(mGridView.getLayoutManager().findViewByPosition(10));
        assertEquals(top1 - 50, top8);
    }

    @Test
    public void testSmoothScrollSelectionEvents() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 500);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 3;
        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(30);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        humanDelay(500);

        final ArrayList<Integer> selectedPositions = new ArrayList<Integer>();
        mGridView.setOnChildSelectedListener(new OnChildSelectedListener() {
            @Override
            public void onChildSelected(ViewGroup parent, View view, int position, long id) {
                selectedPositions.add(position);
            }
        });

        sendRepeatedKeys(10, KeyEvent.KEYCODE_DPAD_UP);
        humanDelay(500);
        waitForScrollIdle(mVerifyLayout);
        // should only get childselected event for item 0 once
        assertTrue(selectedPositions.size() > 0);
        assertEquals(0, selectedPositions.get(selectedPositions.size() - 1).intValue());
        for (int i = selectedPositions.size() - 2; i >= 0; i--) {
            assertFalse(0 == selectedPositions.get(i).intValue());
        }

    }

    @Test
    public void testSmoothScrollSelectionEventsLinear() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 500);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;
        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(10);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        humanDelay(500);

        final ArrayList<Integer> selectedPositions = new ArrayList<Integer>();
        mGridView.setOnChildSelectedListener(new OnChildSelectedListener() {
            @Override
            public void onChildSelected(ViewGroup parent, View view, int position, long id) {
                selectedPositions.add(position);
            }
        });

        sendRepeatedKeys(10, KeyEvent.KEYCODE_DPAD_UP);
        humanDelay(500);
        waitForScrollIdle(mVerifyLayout);
        // should only get childselected event for item 0 once
        assertTrue(selectedPositions.size() > 0);
        assertEquals(0, selectedPositions.get(selectedPositions.size() - 1).intValue());
        for (int i = selectedPositions.size() - 2; i >= 0; i--) {
            assertFalse(0 == selectedPositions.get(i).intValue());
        }

    }

    @Test
    public void testScrollToNoneExisting() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_grid);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 100);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 3;
        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(99);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        humanDelay(500);


        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(50);
            }
        });
        Thread.sleep(100);
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.requestLayout();
                mGridView.setSelectedPositionSmooth(0);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        humanDelay(500);

    }

    @Test
    public void testSmoothscrollerInterrupted() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_REQUEST_FOCUS_ONLAYOUT, true);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 680;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        mGridView.setSelectedPositionSmooth(0);
        waitForScrollIdle(mVerifyLayout);
        assertTrue(mGridView.getChildAt(0).hasFocus());

        // Pressing lots of key to make sure smooth scroller is running
        for (int i = 0; i < 20; i++) {
            sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
        }
        while (mGridView.getLayoutManager().isSmoothScrolling()
                || mGridView.getScrollState() != BaseGridView.SCROLL_STATE_IDLE) {
            // Repeatedly pressing to make sure pending keys does not drop to zero.
            sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
        }
    }

    @Test
    public void testSmoothscrollerCancelled() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_REQUEST_FOCUS_ONLAYOUT, true);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 680;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        mGridView.setSelectedPositionSmooth(0);
        waitForScrollIdle(mVerifyLayout);
        assertTrue(mGridView.getChildAt(0).hasFocus());

        int targetPosition = items.length - 1;
        mGridView.setSelectedPositionSmooth(targetPosition);
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.stopScroll();
            }
        });
        Thread.sleep(100);
        assertEquals(mGridView.getSelectedPosition(), targetPosition);
        assertSame(mGridView.getLayoutManager().findViewByPosition(targetPosition),
                mGridView.findFocus());
    }

    @Test
    public void testSetNumRowsAndAddItem() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_REQUEST_FOCUS_ONLAYOUT, true);
        int[] items = new int[2];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        mGridView.setSelectedPositionSmooth(0);
        waitForScrollIdle(mVerifyLayout);

        mActivity.addItems(items.length, new int[]{300});

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                ((VerticalGridView) mGridView).setNumColumns(2);
            }
        });
        Thread.sleep(1000);
        assertTrue(mGridView.getChildAt(2).getLeft() != mGridView.getChildAt(1).getLeft());
    }


    @Test
    public void testRequestLayoutBugInLayout() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_CHILD_LAYOUT_ID, R.layout.relative_layout);
        intent.putExtra(GridActivity.EXTRA_REQUEST_FOCUS_ONLAYOUT, true);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(1);
            }
        });
        waitForScrollIdle(mVerifyLayout);

        sendKey(KeyEvent.KEYCODE_DPAD_UP);
        waitForScrollIdle(mVerifyLayout);

        assertEquals("Line 2", ((TextView) mGridView.findFocus()).getText().toString());
    }


    @Test
    public void testChangeLayoutInChild() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear_wrap_content);
        intent.putExtra(GridActivity.EXTRA_REQUEST_LAYOUT_ONFOCUS, true);
        int[] items = new int[2];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(0);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        verifyMargin();

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(1);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        verifyMargin();
    }

    @Test
    public void testWrapContent() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_grid_wrap);
        int[] items = new int[200];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mActivity.attachToNewAdapter(new int[0]);
            }
        });

    }


    @Test
    public void testZeroFixedSecondarySize() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear_measured_with_zero);
        intent.putExtra(GridActivity.EXTRA_SECONDARY_SIZE_ZERO, true);
        int[] items = new int[2];
        for (int i = 0; i < items.length; i++) {
            items[i] = 0;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

    }

    @Test
    public void testChildStates() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID, R.layout.vertical_linear);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 200;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        intent.putExtra(GridActivity.EXTRA_REQUEST_LAYOUT_ONFOCUS, true);
        intent.putExtra(GridActivity.EXTRA_CHILD_LAYOUT_ID, R.layout.selectable_text_view);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);
        mGridView.setSaveChildrenPolicy(VerticalGridView.SAVE_ALL_CHILD);

        final SparseArray<Parcelable> container = new SparseArray<Parcelable>();

        // 1 Save view states
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                Selection.setSelection((Spannable)(((TextView) mGridView.getChildAt(0))
                        .getText()), 0, 1);
                Selection.setSelection((Spannable)(((TextView) mGridView.getChildAt(1))
                        .getText()), 0, 1);
                mGridView.saveHierarchyState(container);
            }
        });

        // 2 Change view states
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                Selection.setSelection((Spannable)(((TextView) mGridView.getChildAt(0))
                        .getText()), 1, 2);
                Selection.setSelection((Spannable)(((TextView) mGridView.getChildAt(1))
                        .getText()), 1, 2);
            }
        });

        // 3 Detached and re-attached,  should still maintain state of (2)
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(1);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        assertEquals(((TextView) mGridView.getChildAt(0)).getSelectionStart(), 1);
        assertEquals(((TextView) mGridView.getChildAt(0)).getSelectionEnd(), 2);
        assertEquals(((TextView) mGridView.getChildAt(1)).getSelectionStart(), 1);
        assertEquals(((TextView) mGridView.getChildAt(1)).getSelectionEnd(), 2);

        // 4 Recycled and rebound, should load state from (2)
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(20);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(0);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        assertEquals(((TextView) mGridView.getChildAt(0)).getSelectionStart(), 1);
        assertEquals(((TextView) mGridView.getChildAt(0)).getSelectionEnd(), 2);
        assertEquals(((TextView) mGridView.getChildAt(1)).getSelectionStart(), 1);
        assertEquals(((TextView) mGridView.getChildAt(1)).getSelectionEnd(), 2);
    }


    @Test
    public void testNoDispatchSaveChildState() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID, R.layout.vertical_linear);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 200;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        intent.putExtra(GridActivity.EXTRA_CHILD_LAYOUT_ID, R.layout.selectable_text_view);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);
        mGridView.setSaveChildrenPolicy(VerticalGridView.SAVE_NO_CHILD);

        final SparseArray<Parcelable> container = new SparseArray<Parcelable>();

        // 1. Set text selection, save view states should do nothing on child
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                for (int i = 0; i < mGridView.getChildCount(); i++) {
                    Selection.setSelection((Spannable)(((TextView) mGridView.getChildAt(i))
                            .getText()), 0, 1);
                }
                mGridView.saveHierarchyState(container);
            }
        });

        // 2. clear the text selection
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                for (int i = 0; i < mGridView.getChildCount(); i++) {
                    Selection.removeSelection((Spannable)(((TextView) mGridView.getChildAt(i))
                            .getText()));
                }
            }
        });

        // 3. Restore view states should be a no-op for child
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.restoreHierarchyState(container);
                for (int i = 0; i < mGridView.getChildCount(); i++) {
                    assertEquals(-1, ((TextView) mGridView.getChildAt(i)).getSelectionStart());
                    assertEquals(-1, ((TextView) mGridView.getChildAt(i)).getSelectionEnd());
                }
            }
        });
    }


    static interface ViewTypeProvider {
        public int getViewType(int position);
    }

    static interface ItemAlignmentFacetProvider {
        public ItemAlignmentFacet getItemAlignmentFacet(int viewType);
    }

    static class TwoViewTypesProvider implements ViewTypeProvider {
        static int VIEW_TYPE_FIRST = 1;
        static int VIEW_TYPE_DEFAULT = 0;
        @Override
        public int getViewType(int position) {
            if (position == 0) {
                return VIEW_TYPE_FIRST;
            } else {
                return VIEW_TYPE_DEFAULT;
            }
        }
    }

    static class ChangeableViewTypesProvider implements ViewTypeProvider {
        static SparseIntArray sViewTypes = new SparseIntArray();
        @Override
        public int getViewType(int position) {
            return sViewTypes.get(position);
        }
        public static void clear() {
            sViewTypes.clear();
        }
        public static void setViewType(int position, int type) {
            sViewTypes.put(position, type);
        }
    }

    static class PositionItemAlignmentFacetProviderForRelativeLayout1
            implements ItemAlignmentFacetProvider {
        ItemAlignmentFacet mMultipleFacet;

        PositionItemAlignmentFacetProviderForRelativeLayout1() {
            mMultipleFacet = new ItemAlignmentFacet();
            ItemAlignmentFacet.ItemAlignmentDef[] defs =
                    new ItemAlignmentFacet.ItemAlignmentDef[2];
            defs[0] = new ItemAlignmentFacet.ItemAlignmentDef();
            defs[0].setItemAlignmentViewId(R.id.t1);
            defs[1] = new ItemAlignmentFacet.ItemAlignmentDef();
            defs[1].setItemAlignmentViewId(R.id.t2);
            defs[1].setItemAlignmentOffsetPercent(100);
            defs[1].setItemAlignmentOffset(-10);
            mMultipleFacet.setAlignmentDefs(defs);
        }

        @Override
        public ItemAlignmentFacet getItemAlignmentFacet(int position) {
            if (position == 0) {
                return mMultipleFacet;
            } else {
                return null;
            }
        }
    }

    @Test
    public void testMultipleScrollPosition1() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_CHILD_LAYOUT_ID, R.layout.relative_layout);
        intent.putExtra(GridActivity.EXTRA_REQUEST_FOCUS_ONLAYOUT, true);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        intent.putExtra(GridActivity.EXTRA_VIEWTYPEPROVIDER_CLASS,
                TwoViewTypesProvider.class.getName());
        // Set ItemAlignment for each ViewHolder and view type,  ViewHolder should
        // override the view type settings.
        intent.putExtra(GridActivity.EXTRA_ITEMALIGNMENTPROVIDER_CLASS,
                PositionItemAlignmentFacetProviderForRelativeLayout1.class.getName());
        intent.putExtra(GridActivity.EXTRA_ITEMALIGNMENTPROVIDER_VIEWTYPE_CLASS,
                ViewTypePositionItemAlignmentFacetProviderForRelativeLayout2.class.getName());
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        assertEquals("First view is aligned with padding top",
                mGridView.getPaddingTop(), mGridView.getChildAt(0).getTop());

        sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
        waitForScrollIdle(mVerifyLayout);

        final View v = mGridView.getChildAt(0);
        View t1 = v.findViewById(R.id.t1);
        int t1align = (t1.getTop() + t1.getBottom()) / 2;
        View t2 = v.findViewById(R.id.t2);
        int t2align = t2.getBottom() - 10;
        assertEquals("Expected alignment for 2nd textview",
                mGridView.getPaddingTop() - (t2align - t1align),
                v.getTop());
    }

    static class PositionItemAlignmentFacetProviderForRelativeLayout2 implements
            ItemAlignmentFacetProvider {
        ItemAlignmentFacet mMultipleFacet;

        PositionItemAlignmentFacetProviderForRelativeLayout2() {
            mMultipleFacet = new ItemAlignmentFacet();
            ItemAlignmentFacet.ItemAlignmentDef[] defs = new ItemAlignmentFacet.ItemAlignmentDef[2];
            defs[0] = new ItemAlignmentFacet.ItemAlignmentDef();
            defs[0].setItemAlignmentViewId(R.id.t1);
            defs[0].setItemAlignmentOffsetPercent(0);
            defs[1] = new ItemAlignmentFacet.ItemAlignmentDef();
            defs[1].setItemAlignmentViewId(R.id.t2);
            defs[1].setItemAlignmentOffsetPercent(ItemAlignmentFacet.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
            defs[1].setItemAlignmentOffset(-10);
            mMultipleFacet.setAlignmentDefs(defs);
        }

        @Override
        public ItemAlignmentFacet getItemAlignmentFacet(int position) {
            if (position == 0) {
                return mMultipleFacet;
            } else {
                return null;
            }
        }
    }

    @Test
    public void testMultipleScrollPosition2() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID, R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_CHILD_LAYOUT_ID, R.layout.relative_layout);
        intent.putExtra(GridActivity.EXTRA_REQUEST_FOCUS_ONLAYOUT, true);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        intent.putExtra(GridActivity.EXTRA_VIEWTYPEPROVIDER_CLASS,
                TwoViewTypesProvider.class.getName());
        intent.putExtra(GridActivity.EXTRA_ITEMALIGNMENTPROVIDER_CLASS,
                PositionItemAlignmentFacetProviderForRelativeLayout2.class.getName());
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        assertEquals("First view is aligned with padding top", mGridView.getPaddingTop(),
                mGridView.getChildAt(0).getTop());

        sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
        waitForScrollIdle(mVerifyLayout);

        final View v = mGridView.getChildAt(0);
        View t1 = v.findViewById(R.id.t1);
        int t1align = t1.getTop();
        View t2 = v.findViewById(R.id.t2);
        int t2align = t2.getTop() - 10;
        assertEquals("Expected alignment for 2nd textview",
                mGridView.getPaddingTop() - (t2align - t1align), v.getTop());
    }

    static class ViewTypePositionItemAlignmentFacetProviderForRelativeLayout2 implements
            ItemAlignmentFacetProvider {
        ItemAlignmentFacet mMultipleFacet;

        ViewTypePositionItemAlignmentFacetProviderForRelativeLayout2() {
            mMultipleFacet = new ItemAlignmentFacet();
            ItemAlignmentFacet.ItemAlignmentDef[] defs = new ItemAlignmentFacet.ItemAlignmentDef[2];
            defs[0] = new ItemAlignmentFacet.ItemAlignmentDef();
            defs[0].setItemAlignmentViewId(R.id.t1);
            defs[0].setItemAlignmentOffsetPercent(0);
            defs[1] = new ItemAlignmentFacet.ItemAlignmentDef();
            defs[1].setItemAlignmentViewId(R.id.t2);
            defs[1].setItemAlignmentOffsetPercent(100);
            defs[1].setItemAlignmentOffset(-10);
            mMultipleFacet.setAlignmentDefs(defs);
        }

        @Override
        public ItemAlignmentFacet getItemAlignmentFacet(int viewType) {
            if (viewType == TwoViewTypesProvider.VIEW_TYPE_FIRST) {
                return mMultipleFacet;
            } else {
                return null;
            }
        }
    }

    @Test
    public void testMultipleScrollPosition3() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID, R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_CHILD_LAYOUT_ID, R.layout.relative_layout);
        intent.putExtra(GridActivity.EXTRA_REQUEST_FOCUS_ONLAYOUT, true);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        intent.putExtra(GridActivity.EXTRA_VIEWTYPEPROVIDER_CLASS,
                TwoViewTypesProvider.class.getName());
        intent.putExtra(GridActivity.EXTRA_ITEMALIGNMENTPROVIDER_VIEWTYPE_CLASS,
                ViewTypePositionItemAlignmentFacetProviderForRelativeLayout2.class.getName());
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        assertEquals("First view is aligned with padding top", mGridView.getPaddingTop(),
                mGridView.getChildAt(0).getTop());

        sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
        waitForScrollIdle(mVerifyLayout);

        final View v = mGridView.getChildAt(0);
        View t1 = v.findViewById(R.id.t1);
        int t1align = t1.getTop();
        View t2 = v.findViewById(R.id.t2);
        int t2align = t2.getBottom() - 10;
        assertEquals("Expected alignment for 2nd textview",
                mGridView.getPaddingTop() - (t2align - t1align), v.getTop());
    }

    @Test
    public void testSelectionAndAddItemInOneCycle() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 0);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        performAndWaitForAnimation(new Runnable() {
            public void run() {
                mActivity.addItems(0, new int[]{300, 300});
                mGridView.setSelectedPosition(0);
            }
        });
        assertEquals(0, mGridView.getSelectedPosition());
    }

    @Test
    public void testSelectViewTaskSmoothWithAdapterChange() throws Throwable {
        testSelectViewTaskWithAdapterChange(true /*smooth*/);
    }

    @Test
    public void testSelectViewTaskWithAdapterChange() throws Throwable {
        testSelectViewTaskWithAdapterChange(false /*smooth*/);
    }

    private void testSelectViewTaskWithAdapterChange(final boolean smooth) throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 2);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        final View firstView = mGridView.getLayoutManager().findViewByPosition(0);
        final View[] selectedViewByTask = new View[1];
        final ViewHolderTask task = new ViewHolderTask() {
            public void run(RecyclerView.ViewHolder viewHolder) {
                selectedViewByTask[0] = viewHolder.itemView;
            }
        };
        performAndWaitForAnimation(new Runnable() {
            public void run() {
                mActivity.removeItems(0, 1);
                if (smooth) {
                    mGridView.setSelectedPositionSmooth(0, task);
                } else {
                    mGridView.setSelectedPosition(0, task);
                }
            }
        });
        assertEquals(0, mGridView.getSelectedPosition());
        assertNotNull(selectedViewByTask[0]);
        assertNotSame(firstView, selectedViewByTask[0]);
        assertSame(mGridView.getLayoutManager().findViewByPosition(0), selectedViewByTask[0]);
    }

    @Test
    public void testNotifyItemTypeChangedSelectionEvent() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 10);
        intent.putExtra(GridActivity.EXTRA_VIEWTYPEPROVIDER_CLASS,
                ChangeableViewTypesProvider.class.getName());
        ChangeableViewTypesProvider.clear();
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        final ArrayList<Integer> selectedLog = new ArrayList<Integer>();
        mGridView.setOnChildSelectedListener(new OnChildSelectedListener() {
            public void onChildSelected(ViewGroup parent, View view, int position, long id) {
                selectedLog.add(position);
            }
        });

        performAndWaitForAnimation(new Runnable() {
            public void run() {
                ChangeableViewTypesProvider.setViewType(0, 1);
                mGridView.getAdapter().notifyItemChanged(0, 1);
            }
        });
        assertEquals(0, mGridView.getSelectedPosition());
        assertEquals(selectedLog.size(), 1);
        assertEquals((int) selectedLog.get(0), 0);
    }

    @Test
    public void testSelectionSmoothAndAddItemInOneCycle() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 0);
        initActivity(intent);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        performAndWaitForAnimation(new Runnable() {
            public void run() {
                mActivity.addItems(0, new int[]{300, 300});
                mGridView.setSelectedPositionSmooth(0);
            }
        });
        assertEquals(0, mGridView.getSelectedPosition());
    }

    @Test
    public void testExtraLayoutSpace() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 1000);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        initActivity(intent);

        final int windowSize = mGridView.getHeight();
        final int extraLayoutSize = windowSize;
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        // add extra layout space
        startWaitLayout();
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setExtraLayoutSpace(extraLayoutSize);
            }
        });
        waitForLayout();
        View v;
        v = mGridView.getChildAt(mGridView.getChildCount() - 1);
        assertTrue(v.getTop() < windowSize + extraLayoutSize);
        assertTrue(v.getBottom() >= windowSize + extraLayoutSize - mGridView.getVerticalMargin());

        mGridView.setSelectedPositionSmooth(150);
        waitForScrollIdle(mVerifyLayout);
        v = mGridView.getChildAt(0);
        assertTrue(v.getBottom() > - extraLayoutSize);
        assertTrue(v.getTop() <= -extraLayoutSize + mGridView.getVerticalMargin());

        // clear extra layout space
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setExtraLayoutSpace(0);
                verifyMargin();
            }
        });
        Thread.sleep(50);
        v = mGridView.getChildAt(mGridView.getChildCount() - 1);
        assertTrue(v.getTop() < windowSize);
        assertTrue(v.getBottom() >= windowSize - mGridView.getVerticalMargin());
    }

    @Test
    public void testFocusFinder() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear_with_button);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 3);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        initActivity(intent);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        // test focus from button to vertical grid view
        final View button = mActivity.findViewById(R.id.button);
        assertTrue(button.isFocused());
        sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
        assertFalse(mGridView.isFocused());
        assertTrue(mGridView.hasFocus());

        // FocusFinder should find last focused(2nd) item on DPAD_DOWN
        final View secondChild = mGridView.getChildAt(1);
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                secondChild.requestFocus();
                button.requestFocus();
            }
        });
        assertTrue(button.isFocused());
        sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(secondChild.isFocused());

        // Bug 26918143 Even VerticalGridView is not focusable, FocusFinder should find last focused
        // (2nd) item on DPAD_DOWN.
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                button.requestFocus();
            }
        });
        mGridView.setFocusable(false);
        mGridView.setFocusableInTouchMode(false);
        assertTrue(button.isFocused());
        sendKey(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(secondChild.isFocused());
    }

    @Test
    public void testRestoreIndexAndAddItems() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_CHILD_LAYOUT_ID, R.layout.horizontal_item);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 4);
        initActivity(intent);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        assertEquals(mGridView.getSelectedPosition(), 0);
        final SparseArray states = new SparseArray();
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mGridView.saveHierarchyState(states);
                mGridView.setAdapter(null);
            }

        });
        performAndWaitForAnimation(new Runnable() {
            @Override
            public void run() {
                mGridView.restoreHierarchyState(states);
                mActivity.attachToNewAdapter(new int[0]);
                mActivity.addItems(0, new int[]{100, 100, 100, 100});
            }

        });
        assertEquals(mGridView.getSelectedPosition(), 0);
    }

    @Test
    public void test27766012() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear_with_button_onleft);
        intent.putExtra(GridActivity.EXTRA_CHILD_LAYOUT_ID, R.layout.horizontal_item);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 2);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        intent.putExtra(GridActivity.EXTRA_UPDATE_SIZE, false);
        initActivity(intent);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        // set remove animator two seconds
        mGridView.getItemAnimator().setRemoveDuration(2000);
        final View view = mGridView.getChildAt(1);
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                view.requestFocus();
            }
        });
        assertTrue(view.hasFocus());
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.removeItems(0, 2);
            }

        });
        // wait one second, removing second view is still attached to parent
        Thread.sleep(1000);
        assertSame(view.getParent(), mGridView);
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // refocus to the removed item and do a focus search.
                view.requestFocus();
                view.focusSearch(View.FOCUS_UP);
            }

        });
    }

    @Test
    public void testBug27258366() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear_with_button_onleft);
        intent.putExtra(GridActivity.EXTRA_CHILD_LAYOUT_ID, R.layout.horizontal_item);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 10);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        intent.putExtra(GridActivity.EXTRA_UPDATE_SIZE, false);
        initActivity(intent);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        // move item1 500 pixels right, when focus is on item1, default focus finder will pick
        // item0 and item2 for the best match of focusSearch(FOCUS_LEFT).  The grid widget
        // must override default addFocusables(), not to add item0 or item2.
        mActivity.mAdapterListener = new GridActivity.AdapterListener() {
            public void onBind(RecyclerView.ViewHolder vh, int position) {
                if (position == 1) {
                    vh.itemView.setPaddingRelative(500, 0, 0, 0);
                } else {
                    vh.itemView.setPaddingRelative(0, 0, 0, 0);
                }
            }
        };
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mGridView.getAdapter().notifyDataSetChanged();
            }
        });
        Thread.sleep(100);

        final ViewGroup secondChild = (ViewGroup) mGridView.getChildAt(1);
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                secondChild.requestFocus();
            }
        });
        sendKey(KeyEvent.KEYCODE_DPAD_LEFT);
        Thread.sleep(100);
        final View button = mActivity.findViewById(R.id.button);
        assertTrue(button.isFocused());
    }

    @Test
    public void testAccessibility() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_NUM_ITEMS, 1000);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        initActivity(intent);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        assertTrue(0 == mGridView.getSelectedPosition());

        final RecyclerViewAccessibilityDelegate delegateCompat = mGridView
                .getCompatAccessibilityDelegate();
        final AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                delegateCompat.onInitializeAccessibilityNodeInfo(mGridView, info);
            }
        });
        assertTrue("test sanity", info.isScrollable());
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                delegateCompat.performAccessibilityAction(mGridView,
                        AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD, null);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        int selectedPosition1 = mGridView.getSelectedPosition();
        assertTrue(0 < selectedPosition1);

        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                delegateCompat.onInitializeAccessibilityNodeInfo(mGridView, info);
            }
        });
        assertTrue("test sanity", info.isScrollable());
        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                delegateCompat.performAccessibilityAction(mGridView,
                        AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD, null);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        int selectedPosition2 = mGridView.getSelectedPosition();
        assertTrue(selectedPosition2 < selectedPosition1);
    }

    void slideInAndWaitIdle() throws Throwable {
        slideInAndWaitIdle(5000);
    }

    void slideInAndWaitIdle(long timeout) throws Throwable {
        // animateIn() would reset position
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.animateIn();
            }
        });
        PollingCheck.waitFor(timeout, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return !mGridView.getLayoutManager().isSmoothScrolling()
                        && mGridView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
            }
        });
    }

    @Test
    public void testAnimateOutBlockScrollTo() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear_with_button_onleft);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        assertEquals("First view is aligned with padding top", mGridView.getPaddingTop(),
                mGridView.getChildAt(0).getTop());

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.animateOut();
            }
        });
        // wait until sliding out.
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getChildAt(0).getTop() > mGridView.getPaddingTop();
            }
        });
        // scrollToPosition() should not affect slideOut status
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.scrollToPosition(0);
            }
        });
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
            }
        });
        assertTrue("First view slided Out", mGridView.getChildAt(0).getTop()
                >= mGridView.getHeight());

        slideInAndWaitIdle();
        assertEquals("First view is aligned with padding top", mGridView.getPaddingTop(),
                mGridView.getChildAt(0).getTop());
    }

    @Test
    public void testAnimateOutBlockSmoothScrolling() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear_with_button_onleft);
        int[] items = new int[30];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        assertEquals("First view is aligned with padding top", mGridView.getPaddingTop(),
                mGridView.getChildAt(0).getTop());

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.animateOut();
            }
        });
        // wait until sliding out.
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getChildAt(0).getTop() > mGridView.getPaddingTop();
            }
        });
        // smoothScrollToPosition() should not affect slideOut status
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.smoothScrollToPosition(29);
            }
        });
        PollingCheck.waitFor(10000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
            }
        });
        assertTrue("First view slided Out", mGridView.getChildAt(0).getTop()
                >= mGridView.getHeight());

        slideInAndWaitIdle();
        View lastChild = mGridView.getChildAt(mGridView.getChildCount() - 1);
        assertSame("Scrolled to last child",
                mGridView.findViewHolderForAdapterPosition(29).itemView, lastChild);
    }

    @Test
    public void testAnimateOutBlockLongScrollTo() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear_with_button_onleft);
        int[] items = new int[30];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        assertEquals("First view is aligned with padding top", mGridView.getPaddingTop(),
                mGridView.getChildAt(0).getTop());

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.animateOut();
            }
        });
        // wait until sliding out.
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getChildAt(0).getTop() > mGridView.getPaddingTop();
            }
        });
        // smoothScrollToPosition() should not affect slideOut status
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.scrollToPosition(29);
            }
        });
        PollingCheck.waitFor(10000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
            }
        });
        assertTrue("First view slided Out", mGridView.getChildAt(0).getTop()
                >= mGridView.getHeight());

        slideInAndWaitIdle();
        View lastChild = mGridView.getChildAt(mGridView.getChildCount() - 1);
        assertSame("Scrolled to last child",
                mGridView.findViewHolderForAdapterPosition(29).itemView, lastChild);
    }

    @Test
    public void testAnimateOutBlockLayout() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear_with_button_onleft);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        assertEquals("First view is aligned with padding top", mGridView.getPaddingTop(),
                mGridView.getChildAt(0).getTop());

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.animateOut();
            }
        });
        // wait until sliding out.
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getChildAt(0).getTop() > mGridView.getPaddingTop();
            }
        });
        // change adapter should not affect slideOut status
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mActivity.changeItem(0, 200);
            }
        });
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
            }
        });
        assertTrue("First view slided Out", mGridView.getChildAt(0).getTop()
                >= mGridView.getHeight());
        assertEquals("onLayout suppressed during slide out", 300,
                mGridView.getChildAt(0).getHeight());

        slideInAndWaitIdle();
        assertEquals("First view is aligned with padding top", mGridView.getPaddingTop(),
                mGridView.getChildAt(0).getTop());
        // size of item should be updated immediately after slide in animation finishes:
        PollingCheck.waitFor(1000, new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return 200 == mGridView.getChildAt(0).getHeight();
            }
        });
    }

    @Test
    public void testAnimateOutBlockFocusChange() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear_with_button_onleft);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        assertEquals("First view is aligned with padding top", mGridView.getPaddingTop(),
                mGridView.getChildAt(0).getTop());

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.animateOut();
                mActivity.findViewById(R.id.button).requestFocus();
            }
        });
        assertTrue(mActivity.findViewById(R.id.button).hasFocus());
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getChildAt(0).getTop() > mGridView.getPaddingTop();
            }
        });
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.requestFocus();
            }
        });
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
            }
        });
        assertTrue("First view slided Out", mGridView.getChildAt(0).getTop()
                >= mGridView.getHeight());

        slideInAndWaitIdle();
        assertEquals("First view is aligned with padding top", mGridView.getPaddingTop(),
                mGridView.getChildAt(0).getTop());
    }

    @Test
    public void testHorizontalAnimateOutBlockScrollTo() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        initActivity(intent);

        assertEquals("First view is aligned with padding left", mGridView.getPaddingLeft(),
                mGridView.getChildAt(0).getLeft());

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.animateOut();
            }
        });
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getChildAt(0).getLeft() > mGridView.getPaddingLeft();
            }
        });
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.scrollToPosition(0);
            }
        });
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
            }
        });

        assertTrue("First view is slided out", mGridView.getChildAt(0).getLeft()
                > mGridView.getWidth());

        slideInAndWaitIdle();
        assertEquals("First view is aligned with padding left", mGridView.getPaddingLeft(),
                mGridView.getChildAt(0).getLeft());

    }

    @Test
    public void testHorizontalAnimateOutRtl() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.horizontal_linear_rtl);
        int[] items = new int[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = 300;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.HORIZONTAL;
        mNumRows = 1;

        initActivity(intent);

        assertEquals("First view is aligned with padding right",
                mGridView.getWidth() - mGridView.getPaddingRight(),
                mGridView.getChildAt(0).getRight());

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.animateOut();
            }
        });
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getChildAt(0).getRight()
                        < mGridView.getWidth() - mGridView.getPaddingRight();
            }
        });
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.smoothScrollToPosition(0);
            }
        });
        PollingCheck.waitFor(new PollingCheck.PollingCheckCondition() {
            @Override
            public boolean canProceed() {
                return mGridView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
            }
        });

        assertTrue("First view is slided out", mGridView.getChildAt(0).getRight() < 0);

        slideInAndWaitIdle();
        assertEquals("First view is aligned with padding right",
                mGridView.getWidth() - mGridView.getPaddingRight(),
                mGridView.getChildAt(0).getRight());
    }

    @Test
    public void testSmoothScrollerOutRange() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID,
                R.layout.vertical_linear_with_button_onleft);
        intent.putExtra(GridActivity.EXTRA_REQUEST_FOCUS_ONLAYOUT, true);
        int[] items = new int[30];
        for (int i = 0; i < items.length; i++) {
            items[i] = 680;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        final View button = mActivity.findViewById(R.id.button);
        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                button.requestFocus();
            }
        });

        mGridView.setSelectedPositionSmooth(0);
        waitForScrollIdle(mVerifyLayout);

        mActivityTestRule.runOnUiThread(new Runnable() {
            public void run() {
                mGridView.setSelectedPositionSmooth(120);
            }
        });
        waitForScrollIdle(mVerifyLayout);
        assertTrue(button.hasFocus());
        int key;
        if (mGridView.getLayoutDirection() == ViewGroup.LAYOUT_DIRECTION_RTL) {
            key = KeyEvent.KEYCODE_DPAD_LEFT;
        } else {
            key = KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        sendKey(key);
        // the GridView should has focus in its children
        assertTrue(mGridView.hasFocus());
        assertFalse(mGridView.isFocused());
        assertEquals(29, mGridView.getSelectedPosition());
    }

    @Test
    public void testRemoveLastItemWithStableId() throws Throwable {
        Intent intent = new Intent();
        intent.putExtra(GridActivity.EXTRA_LAYOUT_RESOURCE_ID, R.layout.vertical_linear);
        intent.putExtra(GridActivity.EXTRA_HAS_STABLE_IDS, true);
        int[] items = new int[1];
        for (int i = 0; i < items.length; i++) {
            items[i] = 680;
        }
        intent.putExtra(GridActivity.EXTRA_ITEMS, items);
        intent.putExtra(GridActivity.EXTRA_STAGGERED, false);
        mOrientation = BaseGridView.VERTICAL;
        mNumRows = 1;

        initActivity(intent);

        mActivityTestRule.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mGridView.getItemAnimator().setRemoveDuration(2000);
                mActivity.removeItems(0, 1, false);
                mGridView.getAdapter().notifyDataSetChanged();
            }
        });
        Thread.sleep(500);
        assertEquals(-1, mGridView.getSelectedPosition());
    }
}

