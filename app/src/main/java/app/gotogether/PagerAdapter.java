package app.gotogether;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

import app.gotogether.fragments.ClusterListFragment;
import app.gotogether.fragments.ParticipantsListFragment;


public class PagerAdapter extends FragmentPagerAdapter {

    public enum TabItem {
        PARTICIPANTS(ParticipantsListFragment.class, R.string.tab_participants),
        CLUSTER(ClusterListFragment.class, R.string.tab_cluster);


        private final Class<? extends Fragment> fragmentClass;
        private final int titleResId;

        TabItem(Class<? extends Fragment> fragmentClass, @StringRes int titleResId) {
            this.fragmentClass = fragmentClass;
            this.titleResId = titleResId;
        }
    }
    private ArrayList<TabItem> tabItemsArray;
    private final Context context;
    private ArrayList<Fragment> fragments = new ArrayList<>();
    private ParticipantsListFragment pFragment;
    private ClusterListFragment cFragment;
    // configure icons for each tab
    private int[] imageResId = {
            R.drawable.ic_group_black_24dp,
            R.drawable.ic_directions_car_black_24dp
    };
    public PagerAdapter(FragmentManager fragmentManager, Context context, TabItem... tabItems) {
        super(fragmentManager);
        this.context = context;
        this.tabItemsArray = new ArrayList<TabItem>(Arrays.asList(tabItems));
    }

    @Override
    public Fragment getItem(int position) {
        return newInstance(tabItemsArray.get(position).fragmentClass);
    }

    private Fragment newInstance(Class<? extends Fragment> fragmentClass) {
        try {
            Fragment newFragment = fragmentClass.newInstance();
            fragments.add(newFragment);
            return newFragment;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("fragment must have public no-arg constructor: " + fragmentClass.getName(), e);
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        // Generate title based on item position
        Drawable image = context.getResources().getDrawable(imageResId[position]);
        image.setBounds(0, 0, image.getIntrinsicWidth(), image.getIntrinsicHeight());
        SpannableString sb = new SpannableString(" ");
        ImageSpan imageSpan = new ImageSpan(image, ImageSpan.ALIGN_BOTTOM);
        sb.setSpan(imageSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    @Override
    public int getCount() {
        return tabItemsArray.size();
    }

    public Fragment getFragment(int index) {
        return fragments.get(index);
    }

    public void addTabPage(TabItem item) {
        tabItemsArray.add(item);
        notifyDataSetChanged();
    }
}
