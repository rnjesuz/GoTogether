package app.gotogether.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


import app.gotogether.R;

public class NestedScrollFragment extends Fragment {

    public static interface OnCompleteListener {
        public abstract void onComplete();
    }

    private OnCompleteListener mListener;

    private View inflatedView;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.mListener = (OnCompleteListener)context;
        }
        catch (final ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnCompleteListener");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.d("suuuuuup", "yoooooooo");
        inflatedView = inflater.inflate(R.layout.fragment_nested_scroll, container, false);
        if (inflatedView == null)
            Log.d("yoooooo", "null");
        else
            Log.d("yoooooo", "not null");
        mListener.onComplete();
        return inflatedView;
    }

    public View getInflatedView() {
        return inflatedView;
    }
}
