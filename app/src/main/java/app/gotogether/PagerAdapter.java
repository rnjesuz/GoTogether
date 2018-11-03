package app.gotogether;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.util.Log;

import app.gotogether.fragments.NestedScrollFragment;


public class PagerAdapter extends FragmentPagerAdapter {

    public enum TabItem {
        NESTED_SCROLL(NestedScrollFragment.class, R.string.tab_nested_scroll);


        private final Class<? extends Fragment> fragmentClass;
        private final int titleResId;
        TabItem(Class<? extends Fragment> fragmentClass, @StringRes int titleResId) {
            this.fragmentClass = fragmentClass;
            this.titleResId = titleResId;
        }

    }

    private final TabItem[] tabItems;
    private final Context context;

    private final Intent intent;
    private NestedScrollFragment fragment;

    public PagerAdapter(FragmentManager fragmentManager, Context context, Intent intent, TabItem... tabItems) {
        super(fragmentManager);
        Log.d("yoooooo", "const");
        this.context = context;
        this.tabItems = tabItems;
        this.intent = intent;
    }

    @Override
    public Fragment getItem(int position) {
        Log.d("yoooo", "item");
        return newInstance(tabItems[position].fragmentClass);
    }

    private Fragment newInstance(Class<? extends Fragment> fragmentClass) {
        try {
            Log.d("yoooooooo", "mmmmmmmmhhhh");
            fragment = (NestedScrollFragment) fragmentClass.newInstance();
            return fragment;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("fragment must have public no-arg constructor: " + fragmentClass.getName(), e);
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return context.getString(tabItems[position].titleResId);
    }

    @Override
    public int getCount() {
        return tabItems.length;
    }

    public NestedScrollFragment getFragment() {
        return fragment;
    }
}
