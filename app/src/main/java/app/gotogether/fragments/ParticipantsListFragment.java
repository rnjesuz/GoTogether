package app.gotogether.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


import app.gotogether.R;

public class ParticipantsListFragment extends Fragment {

    private String TAG = "ParticipantListFragment";

    public  interface OnCompleteListener {
        void onParticipantsListFragmentComplete();
    }

    private View inflatedView;

    private OnCompleteListener mListener;

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
        inflatedView = inflater.inflate(R.layout.fragment_participants, container, false);
        mListener.onParticipantsListFragmentComplete();
        return inflatedView;
    }

    public View getInflatedView() {
        return inflatedView;
    }
}
